package dev.coms4156.knowledgedrop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.coms4156.knowledgedrop.api.dto.MessageResponse;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.model.DocumentStatus;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import dev.coms4156.knowledgedrop.storage.FileStorage;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Unit tests for {@link DocumentService#delete} with a mocked repository, ingestion service and
 * file storage: soft-delete behavior, chunk removal, client scoping and failure handling.
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceDeleteTest {

  private static final String HELLO_SHA1 = "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d";

  @Mock private DocumentRepository documentRepository;

  @Mock private IngestionService ingestionService;

  @Mock private FileStorage fileStorage;

  @Mock private PlatformTransactionManager transactionManager;

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

  private DocumentRecord storedRecord(String name) {
    DocumentRecord record = new DocumentRecord(clientId, name, "text/plain", 5L, HELLO_SHA1);
    ReflectionTestUtils.setField(record, "id", UUID.randomUUID());
    return record;
  }

  private void stubLookup(DocumentRecord record) {
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, record.getName()))
        .thenReturn(Optional.of(record));
  }

  private DocumentRecord existingDocument(String name) {
    DocumentRecord record = storedRecord(name);
    stubLookup(record);
    return record;
  }

  // ---------------------------------------------------------------------------------------------
  // successful delete
  // ---------------------------------------------------------------------------------------------

  @Test
  void delete_existingDocument_returnsConfirmationMessage() {
    existingDocument("notes.txt");

    MessageResponse response = documentService.delete(client, "notes.txt");

    assertEquals("Document 'notes.txt' has been deleted", response.message());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "notes.txt",
        "my notes (final).txt",
        "résumé.pdf",
        "文档.txt",
        "it's.txt",
        "UPPER.TXT"
      })
  void delete_confirmationMessageEchoesTheFileNameVerbatim(String name) {
    existingDocument(name);

    MessageResponse response = documentService.delete(client, name);

    assertEquals("Document '" + name + "' has been deleted", response.message());
  }

  @Test
  void delete_existingDocument_softDeletesAndSavesTheSameRecord() {
    DocumentRecord record = existingDocument("notes.txt");
    Instant before = Instant.now();

    documentService.delete(client, "notes.txt");

    assertNotNull(record.getDeletedAt());
    assertFalse(record.getDeletedAt().isBefore(before));
    assertFalse(record.getDeletedAt().isAfter(Instant.now()));
    ArgumentCaptor<DocumentRecord> captor = ArgumentCaptor.forClass(DocumentRecord.class);
    verify(documentRepository).save(captor.capture());
    assertSame(record, captor.getValue());
  }

  @Test
  void delete_existingDocument_freesTheNameForReuse() {
    DocumentRecord record = existingDocument("notes.txt");
    assertNotNull(ReflectionTestUtils.getField(record, "activeKey"));

    documentService.delete(client, "notes.txt");

    assertNull(ReflectionTestUtils.getField(record, "activeKey"));
  }

  @Test
  void delete_existingDocument_leavesOtherFieldsUntouched() {
    DocumentRecord record = existingDocument("notes.txt");
    UUID id = record.getId();

    documentService.delete(client, "notes.txt");

    assertEquals(id, record.getId());
    assertEquals(clientId, record.getOwnerId());
    assertEquals("notes.txt", record.getName());
    assertEquals("text/plain", record.getType());
    assertEquals(5L, record.getFileBytes());
    assertEquals(HELLO_SHA1, record.getContentHash());
  }

  @ParameterizedTest
  @EnumSource(DocumentStatus.class)
  void delete_documentInAnyStatus_isSoftDeletedAndKeepsItsStatus(DocumentStatus status) {
    DocumentRecord record = storedRecord("notes.txt");
    if (status == DocumentStatus.READY) {
      record.markReady();
    } else if (status == DocumentStatus.FAILED) {
      record.markFailed("boom");
    }
    stubLookup(record);

    documentService.delete(client, "notes.txt");

    assertNotNull(record.getDeletedAt());
    assertEquals(status, record.getStatus());
  }

  @Test
  void delete_existingDocument_removesItsChunksButDoesNotIngest() {
    DocumentRecord record = existingDocument("notes.txt");

    documentService.delete(client, "notes.txt");

    verify(ingestionService).deleteChunks(record.getId());
    verify(ingestionService, never()).ingest(any(UUID.class));
  }

  @Test
  void delete_marksRecordDeletedBeforeRemovingChunks() {
    existingDocument("notes.txt");

    documentService.delete(client, "notes.txt");

    InOrder order = inOrder(documentRepository, ingestionService);
    order.verify(documentRepository).save(any(DocumentRecord.class));
    order.verify(ingestionService).deleteChunks(any(UUID.class));
  }

  @Test
  void delete_isSoftDelete_neverRemovesTheRecordOrTheStoredFile() {
    existingDocument("notes.txt");

    documentService.delete(client, "notes.txt");

    verify(documentRepository, never()).delete(any(DocumentRecord.class));
    verify(documentRepository, never()).deleteById(any(UUID.class));
    verify(documentRepository, never()).deleteAll();
    verifyNoInteractions(fileStorage);
  }

  @Test
  void delete_lookupIsScopedToCallerAndFileName() {
    existingDocument("notes.txt");

    documentService.delete(client, "notes.txt");

    verify(documentRepository, times(1))
        .findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "notes.txt");
  }

  @Test
  void delete_sameNameTwice_secondCallFailsAndNothingIsSavedAgain() {
    DocumentRecord record = storedRecord("notes.txt");
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "notes.txt"))
        .thenReturn(Optional.of(record), Optional.empty());

    documentService.delete(client, "notes.txt");
    assertThrows(ApiException.class, () -> documentService.delete(client, "notes.txt"));

    verify(documentRepository, times(1)).save(any(DocumentRecord.class));
    verify(ingestionService, times(1)).deleteChunks(record.getId());
  }

  // ---------------------------------------------------------------------------------------------
  // document missing, deleted, or owned by someone else
  // ---------------------------------------------------------------------------------------------

  // delete() should return 404 when the document cannot be found, the same as read(). Only a
  // failure of the lookup itself (see delete_lookupFails_...) should be reported as a 500.
  @Test
  void delete_documentNotFoundOrAlreadyDeleted_throwsNotFound() {
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "missing.txt"))
        .thenReturn(Optional.empty());

    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.delete(client, "missing.txt"));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
  }

  @Test
  void delete_documentNotFound_savesNothingAndRemovesNoChunks() {
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "missing.txt"))
        .thenReturn(Optional.empty());

    assertThrows(ApiException.class, () -> documentService.delete(client, "missing.txt"));

    verify(documentRepository, never()).save(any(DocumentRecord.class));
    verifyNoInteractions(ingestionService, fileStorage);
  }

  @Test
  void delete_otherClientsDocumentWithSameName_isNeverLookedUpOrTouched() {
    UUID otherClientId = UUID.randomUUID();
    ClientRecord other = new ClientRecord("other", "other-token-hash");
    ReflectionTestUtils.setField(other, "id", otherClientId);

    assertThrows(ApiException.class, () -> documentService.delete(other, "notes.txt"));

    verify(documentRepository).findByOwnerIdAndNameAndDeletedAtIsNull(otherClientId, "notes.txt");
    verify(documentRepository, never())
        .findByOwnerIdAndNameAndDeletedAtIsNull(eq(clientId), any(String.class));
    verify(documentRepository, never()).save(any(DocumentRecord.class));
    verifyNoInteractions(ingestionService);
  }

  // ---------------------------------------------------------------------------------------------
  // failures
  // ---------------------------------------------------------------------------------------------

  @Test
  void delete_nullClient_throwsBadRequestWithoutTouchingAnything() {
    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.delete(null, "notes.txt"));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    verifyNoInteractions(documentRepository, ingestionService, fileStorage);
  }

  @Test
  void delete_lookupFails_throwsInternalServerErrorAndChangesNothing() {
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "notes.txt"))
        .thenThrow(new IllegalStateException("db down"));

    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.delete(client, "notes.txt"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    assertTrue(ex.getMessage().contains("db down"));
    verify(documentRepository, never()).save(any(DocumentRecord.class));
    verifyNoInteractions(ingestionService);
  }

  @Test
  void delete_saveFails_throwsInternalServerErrorAndSkipsChunkRemoval() {
    existingDocument("notes.txt");
    when(documentRepository.save(any(DocumentRecord.class)))
        .thenThrow(new IllegalStateException("db down"));

    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.delete(client, "notes.txt"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    assertTrue(ex.getMessage().contains("db down"));
    verifyNoInteractions(ingestionService);
  }

  @Test
  void delete_chunkRemovalFails_errorPropagatesAfterRecordIsAlreadyMarkedDeleted() {
    DocumentRecord record = existingDocument("notes.txt");
    doThrow(ApiException.notImplemented("chunk deletion"))
        .when(ingestionService)
        .deleteChunks(record.getId());

    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.delete(client, "notes.txt"));

    assertEquals(HttpStatus.NOT_IMPLEMENTED, ex.getStatus());
    verify(documentRepository).save(record);
    assertNotNull(record.getDeletedAt());
  }
}
