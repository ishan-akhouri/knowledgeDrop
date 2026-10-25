package dev.coms4156.knowledgedrop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.coms4156.knowledgedrop.api.dto.DocumentSummary;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.model.DocumentStatus;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import dev.coms4156.knowledgedrop.storage.FileStorage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link DocumentService#list} with a mocked repository: status filtering, paging
 * arguments, mapping to {@link DocumentSummary}, client scoping and error handling.
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceListTest {

  private static final String HELLO_SHA1 = "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d";

  @Mock private DocumentRepository documentRepository;

  @Mock private IngestionService ingestionService;

  @Mock private FileStorage fileStorage;

  @InjectMocks private DocumentService documentService;

  private ClientRecord client;
  private UUID clientId;

  @BeforeEach
  void setUp() {
    clientId = UUID.randomUUID();
    client = new ClientRecord("acme", "token-hash");
    ReflectionTestUtils.setField(client, "id", clientId);
  }

  // ---------------------------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------------------------

  private DocumentRecord record(
      String name, String type, DocumentStatus status, Instant uploadedAt) {
    DocumentRecord record = new DocumentRecord(clientId, name, type, 5L, HELLO_SHA1);
    ReflectionTestUtils.setField(record, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(record, "uploadedAt", uploadedAt);
    if (status == DocumentStatus.READY) {
      record.markReady();
    } else if (status == DocumentStatus.FAILED) {
      record.markFailed("boom");
    }
    return record;
  }

  private DocumentRecord record(String name) {
    return record(name, "text/plain", DocumentStatus.PROCESSING, Instant.now());
  }

  private void stubAllQuery(List<DocumentRecord> records) {
    when(documentRepository.findByOwnerIdAndDeletedAtIsNull(eq(clientId), any(Pageable.class)))
        .thenReturn(new PageImpl<>(records));
  }

  private void stubStatusQuery(DocumentStatus status, List<DocumentRecord> records) {
    when(documentRepository.findByOwnerIdAndStatusAndDeletedAtIsNull(
            eq(clientId), eq(status), any(Pageable.class)))
        .thenReturn(new PageImpl<>(records));
  }

  /** Calls list without a status filter and returns the Pageable the repository received. */
  private Pageable pageableSentForAllQuery(Integer limit, Integer offset) {
    stubAllQuery(List.of());
    documentService.list(client, null, limit, offset);
    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(documentRepository).findByOwnerIdAndDeletedAtIsNull(eq(clientId), captor.capture());
    return captor.getValue();
  }

  // ---------------------------------------------------------------------------------------------
  // status filter
  // ---------------------------------------------------------------------------------------------

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "all", "ALL", "All", "  all  "})
  void list_noStatusOrAll_queriesEveryNonDeletedDocument(String status) {
    stubAllQuery(List.of(record("a.txt"), record("b.txt")));

    List<DocumentSummary> result = documentService.list(client, status, null, null);

    assertEquals(2, result.size());
    verify(documentRepository).findByOwnerIdAndDeletedAtIsNull(eq(clientId), any(Pageable.class));
    verify(documentRepository, never())
        .findByOwnerIdAndStatusAndDeletedAtIsNull(
            any(UUID.class), any(DocumentStatus.class), any(Pageable.class));
  }

  @ParameterizedTest
  @CsvSource({
    "processing,PROCESSING",
    "ready,READY",
    "failed,FAILED",
    "READY,READY",
    "Failed,FAILED",
    "'  ready  ',READY"
  })
  void list_statusFilter_isCaseInsensitiveAndTrimmed(String raw, DocumentStatus expected) {
    stubStatusQuery(expected, List.of(record("a.txt")));

    List<DocumentSummary> result = documentService.list(client, raw, null, null);

    assertEquals(1, result.size());
    verify(documentRepository)
        .findByOwnerIdAndStatusAndDeletedAtIsNull(
            eq(clientId), eq(expected), any(Pageable.class));
    verify(documentRepository, never())
        .findByOwnerIdAndDeletedAtIsNull(any(UUID.class), any(Pageable.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"done", "pending", "deleted", "ready,failed", "1"})
  void list_unknownStatus_throwsBadRequestWithoutQueryingRepository(String status) {
    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.list(client, status, null, null));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    assertNotNull(ex.getMessage());
    verifyNoInteractions(documentRepository);
  }

  // ---------------------------------------------------------------------------------------------
  // result mapping
  // ---------------------------------------------------------------------------------------------

  @Test
  void list_clientWithNoDocuments_returnsEmptyList() {
    stubAllQuery(List.of());

    List<DocumentSummary> result = documentService.list(client, null, null, null);

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void list_statusFilterWithNoMatches_returnsEmptyList() {
    stubStatusQuery(DocumentStatus.FAILED, List.of());

    List<DocumentSummary> result = documentService.list(client, "failed", null, null);

    assertTrue(result.isEmpty());
  }

  @Test
  void list_mapsRecordFieldsToSummary() {
    Instant uploaded = Instant.parse("2026-03-01T12:30:00Z");
    DocumentRecord doc = record("report.pdf", "application/pdf", DocumentStatus.READY, uploaded);
    stubAllQuery(List.of(doc));

    List<DocumentSummary> result = documentService.list(client, null, null, null);

    assertEquals(1, result.size());
    DocumentSummary summary = result.get(0);
    assertEquals(doc.getId(), summary.documentId());
    assertEquals("report.pdf", summary.filename());
    assertEquals("application/pdf", summary.type());
    assertEquals(uploaded, summary.uploadTime());
    assertEquals(DocumentStatus.READY, summary.status());
  }

  @Test
  void list_mapsEachStatus() {
    Instant now = Instant.now();
    stubAllQuery(
        List.of(
            record("p.txt", "text/plain", DocumentStatus.PROCESSING, now),
            record("r.txt", "text/plain", DocumentStatus.READY, now),
            record("f.txt", "text/plain", DocumentStatus.FAILED, now)));

    List<DocumentSummary> result = documentService.list(client, "all", null, null);

    assertEquals(
        List.of(DocumentStatus.PROCESSING, DocumentStatus.READY, DocumentStatus.FAILED),
        result.stream().map(DocumentSummary::status).toList());
  }

  @Test
  void list_preservesRepositoryOrder() {
    stubAllQuery(List.of(record("b.txt"), record("a.txt"), record("c.txt")));

    List<DocumentSummary> result = documentService.list(client, null, null, null);

    assertEquals(
        List.of("b.txt", "a.txt", "c.txt"),
        result.stream().map(DocumentSummary::filename).toList());
  }

  // ---------------------------------------------------------------------------------------------
  // paging arguments
  // ---------------------------------------------------------------------------------------------

  @Test
  void list_noLimitOrOffset_startsAtZeroWithDefaultPageSize() {
    Pageable pageable = pageableSentForAllQuery(null, null);

    assertEquals(0L, pageable.getOffset());
    assertEquals(DocumentService.DEFAULT_LIST_LIMIT, pageable.getPageSize());
  }

  @Test
  void list_limitAndOffset_arePassedToRepository() {
    Pageable pageable = pageableSentForAllQuery(10, 5);

    assertEquals(5L, pageable.getOffset());
    assertEquals(10, pageable.getPageSize());
  }

  @Test
  void list_offsetNotMultipleOfLimit_keepsExactOffset() {
    Pageable pageable = pageableSentForAllQuery(3, 4);

    assertEquals(4L, pageable.getOffset());
    assertEquals(3, pageable.getPageSize());
  }

  @Test
  void list_limitWithoutOffset_startsAtZero() {
    Pageable pageable = pageableSentForAllQuery(7, null);

    assertEquals(0L, pageable.getOffset());
    assertEquals(7, pageable.getPageSize());
  }

  @Test
  void list_offsetWithoutLimit_skipsRowsAndUsesDefaultPageSize() {
    Pageable pageable = pageableSentForAllQuery(null, 12);

    assertEquals(12L, pageable.getOffset());
    assertEquals(DocumentService.DEFAULT_LIST_LIMIT, pageable.getPageSize());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, -100})
  void list_nonPositiveLimit_usesDefaultLimit(int limit) {
    Pageable pageable = pageableSentForAllQuery(limit, 2);

    assertEquals(DocumentService.DEFAULT_LIST_LIMIT, pageable.getPageSize());
    assertEquals(2L, pageable.getOffset());
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, -50})
  void list_negativeOffset_isTreatedAsZero(int offset) {
    Pageable pageable = pageableSentForAllQuery(5, offset);

    assertEquals(0L, pageable.getOffset());
    assertEquals(5, pageable.getPageSize());
  }

  @Test
  void list_zeroOffset_startsAtZero() {
    Pageable pageable = pageableSentForAllQuery(5, 0);

    assertEquals(0L, pageable.getOffset());
  }

  @Test
  void list_sortsNewestUploadsFirst() {
    Pageable pageable = pageableSentForAllQuery(null, null);

    Sort.Order order = pageable.getSort().getOrderFor("uploadedAt");
    assertNotNull(order);
    assertEquals(Sort.Direction.DESC, order.getDirection());
  }

  @Test
  void list_statusFilter_receivesSamePagingAndSort() {
    stubStatusQuery(DocumentStatus.READY, List.of());

    documentService.list(client, "ready", 4, 8);

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(documentRepository)
        .findByOwnerIdAndStatusAndDeletedAtIsNull(
            eq(clientId), eq(DocumentStatus.READY), captor.capture());
    Pageable pageable = captor.getValue();
    assertEquals(8L, pageable.getOffset());
    assertEquals(4, pageable.getPageSize());
    assertEquals(
        Sort.Direction.DESC, pageable.getSort().getOrderFor("uploadedAt").getDirection());
  }

  // ---------------------------------------------------------------------------------------------
  // scoping, side effects and failures
  // ---------------------------------------------------------------------------------------------

  @Test
  void list_isScopedToCallingClient() {
    UUID otherClientId = UUID.randomUUID();
    ClientRecord other = new ClientRecord("other", "other-hash");
    ReflectionTestUtils.setField(other, "id", otherClientId);
    when(documentRepository.findByOwnerIdAndDeletedAtIsNull(
            eq(otherClientId), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    documentService.list(other, null, null, null);

    verify(documentRepository)
        .findByOwnerIdAndDeletedAtIsNull(eq(otherClientId), any(Pageable.class));
    verify(documentRepository, never())
        .findByOwnerIdAndDeletedAtIsNull(eq(clientId), any(Pageable.class));
  }

  @Test
  void list_doesNotModifyRecordsOrTouchOtherCollaborators() {
    DocumentRecord doc = record("a.txt");
    stubAllQuery(List.of(doc));

    documentService.list(client, null, null, null);

    verify(documentRepository, never()).save(any(DocumentRecord.class));
    verify(documentRepository, never()).delete(any(DocumentRecord.class));
    verifyNoInteractions(ingestionService, fileStorage);
    assertNull(doc.getDeletedAt());
    assertEquals(DocumentStatus.PROCESSING, doc.getStatus());
  }

  @Test
  void list_nullClient_throwsBadRequestWithoutQueryingRepository() {
    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.list(null, null, null, null));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    verifyNoInteractions(documentRepository);
  }
}
