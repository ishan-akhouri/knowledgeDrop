package dev.coms4156.knowledgedrop.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.coms4156.knowledgedrop.api.dto.DocumentStatusResponse;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.model.DocumentStatus;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import dev.coms4156.knowledgedrop.storage.FileStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.multipart.MultipartFile;

/**
 * Additional unit tests for {@link DocumentService#create}: upload validation, hashing, stream
 * handling, the hand-off to ingestion, and the replace-existing-document transaction. Complements
 * {@link DocumentServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceCreateEdgeCasesTest {

  private static final String HELLO_SHA1 = "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d";
  private static final String EMPTY_SHA1 = "da39a3ee5e6b4b0d3255bfef95601890afd80709";
  private static final long TEN_GB = 10L * 1024 * 1024 * 1024;
  private static final byte[] HELLO_BYTES = "hello".getBytes(StandardCharsets.UTF_8);

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
    client = clientWithId("acme", clientId);
  }

  // ---------------------------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------------------------

  private static ClientRecord clientWithId(String name, UUID id) {
    ClientRecord record = new ClientRecord(name, "token-hash-" + name);
    ReflectionTestUtils.setField(record, "id", id);
    return record;
  }

  /** Makes the mocked repository assign an ID to any record that does not have one yet. */
  private void saveAssignsIds() {
    when(documentRepository.save(any(DocumentRecord.class)))
        .thenAnswer(
            invocation -> {
              DocumentRecord record = invocation.getArgument(0);
              if (record.getId() == null) {
                ReflectionTestUtils.setField(record, "id", UUID.randomUUID());
              }
              return record;
            });
  }

  private static MockMultipartFile textFile(String name, String content) {
    return new MockMultipartFile(
        "file", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] patternedBytes(int size) {
    byte[] content = new byte[size];
    for (int i = 0; i < size; i++) {
      content[i] = (byte) (i * 31 + 7);
    }
    return content;
  }

  private static String sha1Hex(byte[] content) throws NoSuchAlgorithmException {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(content));
  }

  /** A mock upload that only answers the metadata calls made before validation. */
  private static MultipartFile uploadMetadataOnly(String name, String type, long size) {
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn(name);
    when(file.getContentType()).thenReturn(type);
    when(file.getSize()).thenReturn(size);
    return file;
  }

  /** A mock upload whose every getInputStream call yields a fresh stream over the content. */
  private static MultipartFile upload(
      String name, String type, long size, byte[] content) throws IOException {
    MultipartFile file = uploadMetadataOnly(name, type, size);
    when(file.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(content));
    return file;
  }

  private static TrackingStream trackedStream(List<TrackingStream> opened) {
    TrackingStream stream = new TrackingStream(HELLO_BYTES);
    opened.add(stream);
    return stream;
  }

  private DocumentRecord savedRecord() {
    ArgumentCaptor<DocumentRecord> captor = ArgumentCaptor.forClass(DocumentRecord.class);
    verify(documentRepository).save(captor.capture());
    return captor.getValue();
  }

  /** Stubs an active document with this name owned by the caller. */
  private DocumentRecord existingRecord(String name) {
    DocumentRecord record =
        new DocumentRecord(clientId, name, "application/pdf", 3L, EMPTY_SHA1);
    ReflectionTestUtils.setField(record, "id", UUID.randomUUID());
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, name))
        .thenReturn(Optional.of(record));
    return record;
  }

  /** An input stream that remembers whether it was closed. */
  private static final class TrackingStream extends ByteArrayInputStream {
    private boolean closed;

    TrackingStream(byte[] content) {
      super(content);
    }

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }

    boolean isClosed() {
      return closed;
    }
  }

  // ---------------------------------------------------------------------------------------------
  // validation
  // ---------------------------------------------------------------------------------------------

  @Test
  void create_fileOverSizeLimit_throwsContentTooLargeWithoutTouchingAnything() throws Exception {
    MultipartFile file = uploadMetadataOnly("huge.bin", "application/octet-stream", TEN_GB + 1);

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.CONTENT_TOO_LARGE, ex.getStatus());
    assertEquals("File size exceeds 10GB limit", ex.getMessage());
    verify(file, never()).getInputStream();
    verifyNoInteractions(documentRepository, ingestionService, fileStorage);
  }

  @Test
  void create_fileExactlyAtSizeLimit_isAccepted() throws Exception {
    saveAssignsIds();
    MultipartFile file = upload("limit.bin", "application/octet-stream", TEN_GB, new byte[] {1, 2});

    DocumentStatusResponse response = documentService.create(client, file);

    assertEquals(DocumentStatus.PROCESSING, response.status());
    assertEquals(TEN_GB, savedRecord().getFileBytes());
  }

  @Test
  void create_emptyFile_reportsThatTheFileIsEmpty() {
    MultipartFile file = uploadMetadataOnly("empty.txt", "text/plain", 0L);

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    assertEquals("File is empty", ex.getMessage());
    verifyNoInteractions(documentRepository, ingestionService, fileStorage);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "   ", "\t", "\n"})
  void create_missingOrBlankFileName_throwsBadRequestWithoutTouchingAnything(String name) {
    MultipartFile file = uploadMetadataOnly(name, "text/plain", 5L);

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    assertEquals("File name is missing", ex.getMessage());
    verifyNoInteractions(documentRepository, ingestionService, fileStorage);
  }

  @Test
  void create_fileNameLookupFails_throwsBadRequest() {
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenThrow(new IllegalStateException("boom"));

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    assertEquals("Failed to extract information from request: boom", ex.getMessage());
    verifyNoInteractions(documentRepository, ingestionService, fileStorage);
  }

  @Test
  void create_contentTypeLookupFails_throwsBadRequest() {
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn("notes.txt");
    when(file.getContentType()).thenThrow(new IllegalStateException("boom"));

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    verifyNoInteractions(documentRepository, ingestionService, fileStorage);
  }

  @Test
  void create_sizeLookupFails_throwsBadRequest() {
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn("notes.txt");
    when(file.getContentType()).thenReturn("text/plain");
    when(file.getSize()).thenThrow(new IllegalStateException("boom"));

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    verifyNoInteractions(documentRepository, ingestionService, fileStorage);
  }

  @Test
  void create_negativeReportedSize_throwsInternalServerErrorAndSavesNoRecord() throws Exception {
    MultipartFile file = upload("notes.txt", "text/plain", -5L, HELLO_BYTES);

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    assertTrue(ex.getMessage().contains("Failed to create document record"));
    verify(documentRepository, never()).save(any(DocumentRecord.class));
    verifyNoInteractions(ingestionService);
  }

  // ---------------------------------------------------------------------------------------------
  // metadata stored on the record
  // ---------------------------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(
      strings = {
        "my notes (v2).txt",
        "résumé.pdf",
        "文档.txt",
        "it's.txt",
        "a.b.c.tar.gz",
        "NOTES"
      })
  void create_fileNameIsStoredAndPassedToStorageVerbatim(String name) throws Exception {
    saveAssignsIds();

    documentService.create(client, textFile(name, "hello"));

    assertEquals(name, savedRecord().getName());
    verify(fileStorage).save(eq(clientId), eq(name), any(InputStream.class));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"application/pdf", "image/png", "text/markdown", "application/octet-stream"})
  void create_contentTypeIsStoredAsReported(String type) {
    saveAssignsIds();
    MockMultipartFile file = new MockMultipartFile("file", "f.bin", type, HELLO_BYTES);

    documentService.create(client, file);

    assertEquals(type, savedRecord().getType());
  }

  // ---------------------------------------------------------------------------------------------
  // hashing and stream handling
  // ---------------------------------------------------------------------------------------------

  @ParameterizedTest
  @CsvSource({
    "abc,a9993e364706816aba3e25717850c26c9cd0d89d",
    "'The quick brown fox jumps over the lazy dog',2fd4e1c67a2d28fced849ee1bb76e7391b93eb12",
    "hello,aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d"
  })
  void create_contentHashMatchesKnownSha1Vectors(String content, String expectedHash) {
    saveAssignsIds();

    documentService.create(client, textFile("notes.txt", content));

    assertEquals(expectedHash, savedRecord().getContentHash());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 8191, 8192, 8193, 16384, 16385, 100_000})
  void create_contentAroundReadBufferBoundaries_isHashedCompletely(int size) throws Exception {
    saveAssignsIds();
    byte[] content = patternedBytes(size);

    documentService.create(client, new MockMultipartFile("file", "f.bin", "text/plain", content));

    DocumentRecord stored = savedRecord();
    assertEquals(sha1Hex(content), stored.getContentHash());
    assertEquals((long) size, stored.getFileBytes());
  }

  @Test
  void create_largeContent_storageReceivesTheCompleteContent() throws Exception {
    saveAssignsIds();
    byte[] content = patternedBytes(20_000);
    AtomicReference<byte[]> savedBytes = new AtomicReference<>();
    when(fileStorage.save(eq(clientId), eq("big.bin"), any(InputStream.class)))
        .thenAnswer(
            invocation -> {
              InputStream in = invocation.getArgument(2);
              savedBytes.set(in.readAllBytes());
              return Path.of("unused");
            });

    documentService.create(
        client, new MockMultipartFile("file", "big.bin", "application/pdf", content));

    assertArrayEquals(content, savedBytes.get());
  }

  @Test
  void create_closesEveryStreamItOpensFromTheUpload() throws Exception {
    saveAssignsIds();
    List<TrackingStream> opened = new ArrayList<>();
    MultipartFile file = uploadMetadataOnly("notes.txt", "text/plain", 5L);
    when(file.getInputStream()).thenAnswer(invocation -> trackedStream(opened));

    documentService.create(client, file);

    assertEquals(2, opened.size());
    assertTrue(opened.stream().allMatch(TrackingStream::isClosed));
  }

  @Test
  void create_storageFails_stillClosesTheStreamItOpened() throws Exception {
    List<TrackingStream> opened = new ArrayList<>();
    MultipartFile file = uploadMetadataOnly("notes.txt", "text/plain", 5L);
    when(file.getInputStream()).thenAnswer(invocation -> trackedStream(opened));
    when(fileStorage.save(any(UUID.class), any(String.class), any(InputStream.class)))
        .thenThrow(new IOException("disk full"));

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    assertEquals(1, opened.size());
    assertTrue(opened.get(0).isClosed());
  }

  @Test
  void create_storageFails_reportsTheReason() throws Exception {
    when(fileStorage.save(any(UUID.class), any(String.class), any(InputStream.class)))
        .thenThrow(new IOException("disk full"));
    MockMultipartFile file = textFile("notes.txt", "hello");

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertTrue(ex.getMessage().contains("Failed to save file content"));
    assertTrue(ex.getMessage().contains("disk full"));
  }

  @Test
  void create_secondPassOverUploadCannotBeOpened_throwsInternalServerError() throws Exception {
    MultipartFile file = uploadMetadataOnly("notes.txt", "text/plain", 5L);
    when(file.getInputStream())
        .thenAnswer(invocation -> new ByteArrayInputStream(HELLO_BYTES))
        .thenThrow(new IOException("gone"));

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    assertTrue(ex.getMessage().contains("Failed to process file content"));
    verify(fileStorage).save(eq(clientId), eq("notes.txt"), any(InputStream.class));
    verifyNoInteractions(documentRepository, ingestionService);
  }

  // ---------------------------------------------------------------------------------------------
  // hand-off to ingestion
  // ---------------------------------------------------------------------------------------------

  @Test
  void create_newDocument_startsIngestionForTheNewDocumentId() {
    saveAssignsIds();

    DocumentStatusResponse response = documentService.create(client, textFile("a.txt", "hello"));

    verify(ingestionService).ingest(response.documentId());
  }

  @Test
  void create_ingestionStartsOnlyAfterTheRecordIsSaved() {
    saveAssignsIds();

    documentService.create(client, textFile("a.txt", "hello"));

    InOrder order = inOrder(documentRepository, ingestionService);
    order.verify(documentRepository).save(any(DocumentRecord.class));
    order.verify(ingestionService).ingest(any(UUID.class));
  }

  @Test
  void create_replacingDocument_ingestsOnlyTheNewDocument() {
    saveAssignsIds();
    DocumentRecord existing = existingRecord("notes.txt");

    DocumentStatusResponse response = documentService.create(client, textFile("notes.txt", "hi"));

    assertNotEquals(existing.getId(), response.documentId());
    verify(ingestionService, times(1)).ingest(response.documentId());
    verify(ingestionService, never()).ingest(existing.getId());
  }

  // In production ingest() is @Async, so its failures never reach the caller; called directly (as
  // in a unit test) the service does not catch them, and the record has already been saved.
  @Test
  void create_ingestionFails_recordIsAlreadySavedAndTheErrorPropagates() {
    saveAssignsIds();
    doThrow(ApiException.notImplemented("document ingestion pipeline"))
        .when(ingestionService)
        .ingest(any(UUID.class));
    MockMultipartFile file = textFile("notes.txt", "hello");

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.NOT_IMPLEMENTED, ex.getStatus());
    verify(documentRepository).save(any(DocumentRecord.class));
  }

  // ---------------------------------------------------------------------------------------------
  // new documents versus replacements
  // ---------------------------------------------------------------------------------------------

  @Test
  void create_newDocument_doesNotOpenTransaction() {
    saveAssignsIds();

    documentService.create(client, textFile("notes.txt", "hello"));

    verifyNoInteractions(transactionManager);
  }

  @Test
  void create_sameNameForDifferentClients_doesNotReplaceTheOtherClientsDocument() {
    saveAssignsIds();
    UUID otherClientId = UUID.randomUUID();
    ClientRecord other = clientWithId("other", otherClientId);

    documentService.create(client, textFile("notes.txt", "mine"));
    documentService.create(other, textFile("notes.txt", "theirs"));

    verify(documentRepository).findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "notes.txt");
    verify(documentRepository).findByOwnerIdAndNameAndDeletedAtIsNull(otherClientId, "notes.txt");
    verify(documentRepository, times(2)).save(any(DocumentRecord.class));
    verifyNoInteractions(transactionManager);
  }

  @Test
  void create_sameNameTwice_secondUploadSoftDeletesTheFirst() {
    saveAssignsIds();
    DocumentStatusResponse firstResponse =
        documentService.create(client, textFile("notes.txt", "first"));
    DocumentRecord first = savedRecord();
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "notes.txt"))
        .thenReturn(Optional.of(first));

    DocumentStatusResponse secondResponse =
        documentService.create(client, textFile("notes.txt", "second"));

    assertNotNull(first.getDeletedAt());
    assertNotEquals(firstResponse.documentId(), secondResponse.documentId());
    verify(documentRepository, times(3)).save(any(DocumentRecord.class));
    verify(ingestionService).ingest(firstResponse.documentId());
    verify(ingestionService).ingest(secondResponse.documentId());
  }

  @Test
  void create_replacingDocument_marksTheOldRecordDeletedAtUploadTime() {
    saveAssignsIds();
    DocumentRecord existing = existingRecord("notes.txt");
    Instant before = Instant.now();

    documentService.create(client, textFile("notes.txt", "hello"));

    assertNotNull(existing.getDeletedAt());
    assertFalse(existing.getDeletedAt().isBefore(before));
    assertFalse(existing.getDeletedAt().isAfter(Instant.now()));
  }

  @Test
  void create_replacingDocument_replacementDescribesTheNewUploadNotTheOldOne() {
    saveAssignsIds();
    DocumentRecord existing = existingRecord("notes.txt");

    documentService.create(client, textFile("notes.txt", "hello"));

    assertEquals("application/pdf", existing.getType());
    assertEquals(3L, existing.getFileBytes());
    assertEquals(EMPTY_SHA1, existing.getContentHash());
    ArgumentCaptor<DocumentRecord> captor = ArgumentCaptor.forClass(DocumentRecord.class);
    verify(documentRepository, times(2)).save(captor.capture());
    DocumentRecord replacement = captor.getAllValues().get(1);
    assertEquals("text/plain", replacement.getType());
    assertEquals(5L, replacement.getFileBytes());
    assertEquals(HELLO_SHA1, replacement.getContentHash());
    assertEquals(clientId, replacement.getOwnerId());
    assertEquals("notes.txt", replacement.getName());
  }

  @Test
  void create_replacingDocument_oldRecordSaveFails_neverSavesNewRecordAndRollsBack() {
    existingRecord("notes.txt");
    when(documentRepository.save(any(DocumentRecord.class)))
        .thenThrow(new IllegalStateException("db down"));
    MockMultipartFile file = textFile("notes.txt", "hello");

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    assertTrue(ex.getMessage().contains("db down"));
    verify(documentRepository, times(1)).save(any(DocumentRecord.class));
    verify(transactionManager).rollback(any());
    verify(transactionManager, never()).commit(any());
    verifyNoInteractions(ingestionService);
  }

  @Test
  void create_replacingDocument_transactionCannotStart_changesNothing() {
    existingRecord("notes.txt");
    when(transactionManager.getTransaction(any()))
        .thenThrow(new IllegalStateException("no connection"));
    MockMultipartFile file = textFile("notes.txt", "hello");

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    verify(documentRepository, never()).save(any(DocumentRecord.class));
    verifyNoInteractions(ingestionService);
  }

  @Test
  void create_replacingDocument_commitFails_throwsInternalServerErrorAndSkipsIngestion() {
    saveAssignsIds();
    existingRecord("notes.txt");
    doThrow(new TransactionSystemException("commit failed")).when(transactionManager).commit(any());
    MockMultipartFile file = textFile("notes.txt", "hello");

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    assertTrue(ex.getMessage().contains("commit failed"));
    verify(documentRepository, times(2)).save(any(DocumentRecord.class));
    verifyNoInteractions(ingestionService);
  }
}
