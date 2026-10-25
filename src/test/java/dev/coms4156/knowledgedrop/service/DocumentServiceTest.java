package dev.coms4156.knowledgedrop.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.coms4156.knowledgedrop.api.dto.DocumentStatusResponse;
import dev.coms4156.knowledgedrop.api.dto.ReadDocument;
import dev.coms4156.knowledgedrop.config.KnowledgeDropProperties;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Unit tests for {@link DocumentService} with a mocked repository, ingestion service and file
 * storage. A few tests use a real {@link FileStorage} rooted in a temporary directory to check
 * that uploaded files really land in the data path.
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

  private static final String HELLO_SHA1 = "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d";
  private static final String EMPTY_SHA1 = "da39a3ee5e6b4b0d3255bfef95601890afd80709";

  @Mock private DocumentRepository documentRepository;

  @Mock private IngestionService ingestionService;

  @Mock private FileStorage fileStorage;

  @Mock private PlatformTransactionManager transactionManager;

  @InjectMocks private DocumentService documentService;

  private ClientRecord client;
  private UUID clientId;

  @BeforeEach
  void setUp() {
    // create() registers an afterCommit hook; give it a synchronization context to register on.
    // No commit happens in these unit tests, so ingestion is never triggered here (ignored for
    // now).
    TransactionSynchronizationManager.initSynchronization();
    clientId = UUID.randomUUID();
    client = new ClientRecord("acme", "token-hash");
    ReflectionTestUtils.setField(client, "id", clientId);
  }

  @AfterEach
  void tearDown() {
    TransactionSynchronizationManager.clearSynchronization();
  }

  /** Makes the mocked repository assign an ID to any record that does not have one yet. */
  private void saveAssignsIds() {
    when(documentRepository.save(any(DocumentRecord.class)))
        .thenAnswer(DocumentServiceTest::assignId);
  }

  /**
   * Same as {@link #saveAssignsIds()} for saveAndFlush, which the replace path uses so the old
   * record's active key is cleared before the new record is inserted.
   */
  private void saveAndFlushAssignsIds() {
    when(documentRepository.saveAndFlush(any(DocumentRecord.class)))
        .thenAnswer(DocumentServiceTest::assignId);
  }

  private static DocumentRecord assignId(InvocationOnMock invocation) {
    DocumentRecord record = invocation.getArgument(0);
    if (record.getId() == null) {
      ReflectionTestUtils.setField(record, "id", UUID.randomUUID());
    }
    return record;
  }

  private static MockMultipartFile textFile(String name, String content) {
    return new MockMultipartFile(
        "file", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
  }

  /** Builds a service that writes through a real {@link FileStorage} rooted at {@code dataDir}. */
  private DocumentService serviceWithRealStorage(Path dataDir) {
    KnowledgeDropProperties properties =
        new KnowledgeDropProperties(
            dataDir.toString(),
            new KnowledgeDropProperties.Ingestion(800, 100),
            new KnowledgeDropProperties.Retrieval(5, 60, 50),
            new KnowledgeDropProperties.Embedding("http://localhost:5000", "model", 384),
            new KnowledgeDropProperties.Llm("", "", ""));
    return new DocumentService(
        documentRepository, ingestionService, new FileStorage(properties), transactionManager);
  }

  // ---------------------------------------------------------------------------------------------
  // create: new documents
  // ---------------------------------------------------------------------------------------------

  @Test
  void create_newDocument_storesMetadataAndHashAndStartsIngestion() {
    saveAssignsIds();
    MockMultipartFile file = textFile("notes.txt", "hello");

    DocumentStatusResponse response = documentService.create(client, file);
    assertNotNull(response.documentId());
    assertEquals(DocumentStatus.PROCESSING, response.status());

    ArgumentCaptor<DocumentRecord> captor = ArgumentCaptor.forClass(DocumentRecord.class);
    verify(documentRepository).save(captor.capture());
    DocumentRecord stored = captor.getValue();
    assertEquals(clientId, stored.getOwnerId());
    assertEquals("notes.txt", stored.getName());
    assertEquals("text/plain", stored.getType());
    assertEquals(5L, stored.getFileBytes());
    assertEquals(HELLO_SHA1, stored.getContentHash());
    assertEquals(DocumentStatus.PROCESSING, stored.getStatus());
    assertNull(stored.getDeletedAt());

    assertEquals(stored.getId(), response.documentId());
    // ingestion is deferred until commit; not exercised in unit tests for now
  }

  @Test
  void create_emptyFile_throwsBadRequestWithoutSaving() {
    MockMultipartFile file = textFile("empty.txt", "");

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    verifyNoInteractions(documentRepository, ingestionService, fileStorage);
  }

  @Test
  void create_fileLargerThanReadBuffer_hashesWholeContent() throws Exception {
    saveAssignsIds();
    byte[] content = new byte[20_000];
    for (int i = 0; i < content.length; i++) {
      content[i] = (byte) (i % 251);
    }
    MockMultipartFile file = new MockMultipartFile("file", "big.bin", "application/pdf", content);

    documentService.create(client, file);

    ArgumentCaptor<DocumentRecord> captor = ArgumentCaptor.forClass(DocumentRecord.class);
    verify(documentRepository).save(captor.capture());
    assertEquals(20_000L, captor.getValue().getFileBytes());
    String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(content));
    assertEquals(expected, captor.getValue().getContentHash());
    assertEquals("application/pdf", captor.getValue().getType());
  }

  @Test
  void create_lookupIsScopedToCallerAndFileName() {
    saveAssignsIds();

    documentService.create(client, textFile("notes.txt", "hello"));

    verify(documentRepository)
        .findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "notes.txt");
  }

  // ---------------------------------------------------------------------------------------------
  // create: saving the original file in the data path
  // ---------------------------------------------------------------------------------------------

  @Test
  void create_newDocument_savesOriginalFileForClientUnderItsName() throws Exception {
    saveAssignsIds();
    AtomicReference<byte[]> savedBytes = new AtomicReference<>();
    when(fileStorage.save(eq(clientId), eq("notes.txt"), any(InputStream.class)))
        .thenAnswer(
            invocation -> {
              InputStream in = invocation.getArgument(2);
              savedBytes.set(in.readAllBytes());
              return Path.of("unused");
            });

    documentService.create(client, textFile("notes.txt", "hello"));

    verify(fileStorage).save(eq(clientId), eq("notes.txt"), any(InputStream.class));
    assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), savedBytes.get());
  }

  @Test
  void create_savesFileBeforeRecord() throws Exception {
    saveAssignsIds();

    documentService.create(client, textFile("notes.txt", "hi"));

    InOrder order = inOrder(fileStorage, documentRepository);
    order.verify(fileStorage).save(eq(clientId), eq("notes.txt"), any(InputStream.class));
    order.verify(documentRepository).save(any(DocumentRecord.class));
    // ingestion is deferred until commit; not exercised in unit tests for now
  }

  @Test
  void create_existingDocumentWithSameName_savesNewFileOnce() throws Exception {
    saveAndFlushAssignsIds();
    DocumentRecord existing =
        new DocumentRecord(clientId, "notes.txt", "text/plain", 3L, EMPTY_SHA1);
    ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "notes.txt"))
        .thenReturn(Optional.of(existing));

    documentService.create(client, textFile("notes.txt", "hello"));

    verify(fileStorage, times(1)).save(eq(clientId), eq("notes.txt"), any(InputStream.class));
  }

  @Test
  void create_fileStorageFails_throwsInternalServerErrorAndLeavesRecordsUntouched()
      throws Exception {
    when(fileStorage.save(any(UUID.class), any(String.class), any(InputStream.class)))
        .thenThrow(new IOException("disk full"));
    MockMultipartFile file = textFile("notes.txt", "hello");

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    verifyNoInteractions(documentRepository, ingestionService);
  }

  @Test
  void create_fileStorageRejectsFileName_throwsInternalServerErrorAndLeavesRecordsUntouched()
      throws Exception {
    when(fileStorage.save(any(UUID.class), any(String.class), any(InputStream.class)))
        .thenThrow(new IllegalArgumentException("Invalid file name"));
    MockMultipartFile file = textFile("../evil.txt", "hello");

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    verifyNoInteractions(documentRepository, ingestionService);
  }

  // ---------------------------------------------------------------------------------------------
  // create: saving the original file in the data path, with real file storage
  // ---------------------------------------------------------------------------------------------

  @Test
  void create_realStorage_writesFileToClientDirectoryInDataPath(@TempDir Path dataDir)
      throws Exception {
    saveAssignsIds();
    DocumentService service = serviceWithRealStorage(dataDir);

    service.create(client, textFile("notes.txt", "hello"));

    Path expected = dataDir.resolve(clientId.toString()).resolve("notes.txt");
    assertTrue(Files.exists(expected));
    assertEquals("hello", Files.readString(expected));
  }

  @Test
  void create_realStorage_writesBinaryContentUnchanged(@TempDir Path dataDir) throws Exception {
    saveAssignsIds();
    DocumentService service = serviceWithRealStorage(dataDir);
    byte[] content = new byte[20_000];
    for (int i = 0; i < content.length; i++) {
      content[i] = (byte) (i % 251);
    }

    service.create(client, new MockMultipartFile("file", "big.bin", "application/pdf", content));

    Path expected = dataDir.resolve(clientId.toString()).resolve("big.bin");
    assertArrayEquals(content, Files.readAllBytes(expected));
  }

  @Test
  void create_realStorage_sameNameUploadReplacesStoredFile(@TempDir Path dataDir)
      throws Exception {
    saveAssignsIds();
    DocumentService service = serviceWithRealStorage(dataDir);

    service.create(client, textFile("notes.txt", "first version"));
    service.create(client, textFile("notes.txt", "second"));

    Path expected = dataDir.resolve(clientId.toString()).resolve("notes.txt");
    assertEquals("second", Files.readString(expected));
  }

  @Test
  void create_realStorage_keepsEachClientsFilesSeparate(@TempDir Path dataDir) throws Exception {
    saveAssignsIds();
    DocumentService service = serviceWithRealStorage(dataDir);
    UUID otherClientId = UUID.randomUUID();
    ClientRecord otherClient = new ClientRecord("other", "other-token-hash");
    ReflectionTestUtils.setField(otherClient, "id", otherClientId);

    service.create(client, textFile("notes.txt", "mine"));
    service.create(otherClient, textFile("notes.txt", "theirs"));

    assertEquals(
        "mine", Files.readString(dataDir.resolve(clientId.toString()).resolve("notes.txt")));
    assertEquals(
        "theirs", Files.readString(dataDir.resolve(otherClientId.toString()).resolve("notes.txt")));
  }

  @Test
  void create_realStorage_fileNameEscapingClientDirectory_isRejectedAndNothingWritten(
      @TempDir Path dataDir) throws Exception {
    DocumentService service = serviceWithRealStorage(dataDir);
    MockMultipartFile file = textFile("../../escaped.txt", "evil");

    ApiException ex = assertThrows(ApiException.class, () -> service.create(client, file));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    assertFalse(Files.exists(dataDir.resolve("escaped.txt")));
    assertFalse(Files.exists(dataDir.getParent().resolve("escaped.txt")));
    verifyNoInteractions(documentRepository, ingestionService);
  }

  // ---------------------------------------------------------------------------------------------
  // create: replacing an existing document
  // ---------------------------------------------------------------------------------------------

  @Test
  void create_existingDocumentWithSameName_softDeletesOldAndIngestsNew() {
    saveAndFlushAssignsIds();
    DocumentRecord existing =
        new DocumentRecord(clientId, "notes.txt", "text/plain", 3L, EMPTY_SHA1);
    UUID existingId = UUID.randomUUID();
    ReflectionTestUtils.setField(existing, "id", existingId);
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "notes.txt"))
        .thenReturn(Optional.of(existing));

    DocumentStatusResponse response =
        documentService.create(client, textFile("notes.txt", "hello"));
    assertEquals(DocumentStatus.PROCESSING, response.status());

    // The old record is soft-deleted and saved first, then the replacement is saved.
    ArgumentCaptor<DocumentRecord> captor = ArgumentCaptor.forClass(DocumentRecord.class);
    verify(documentRepository, times(2)).saveAndFlush(captor.capture());
    DocumentRecord softDeleted = captor.getAllValues().get(0);
    DocumentRecord replacement = captor.getAllValues().get(1);
    assertSame(existing, softDeleted);
    assertNotNull(softDeleted.getDeletedAt());
    assertNotEquals(existingId, replacement.getId());
    assertNull(replacement.getDeletedAt());
    assertEquals(HELLO_SHA1, replacement.getContentHash());
    assertEquals(replacement.getId(), response.documentId());
    // ingestion is deferred until commit; not exercised in unit tests for now
  }

  @Test
  void create_existingDocumentWithSameName_savesBothRecordsInOneCommittedTransaction() {
    saveAndFlushAssignsIds();
    DocumentRecord existing =
        new DocumentRecord(clientId, "notes.txt", "text/plain", 3L, EMPTY_SHA1);
    ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "notes.txt"))
        .thenReturn(Optional.of(existing));

    documentService.create(client, textFile("notes.txt", "hello"));

    InOrder order = inOrder(transactionManager, documentRepository);
    order.verify(transactionManager).getTransaction(any());
    order.verify(documentRepository, times(2)).saveAndFlush(any(DocumentRecord.class));
    order.verify(transactionManager).commit(any());
    verify(transactionManager, never()).rollback(any());
  }

  @Test
  void create_existingDocumentWithSameName_newRecordSaveFails_rollsBackAndSkipsIngestion() {
    DocumentRecord existing =
        new DocumentRecord(clientId, "notes.txt", "text/plain", 3L, EMPTY_SHA1);
    ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "notes.txt"))
        .thenReturn(Optional.of(existing));
    // Soft-deleting the old record succeeds, but saving the replacement fails.
    when(documentRepository.saveAndFlush(any(DocumentRecord.class)))
        .thenAnswer(
            invocation -> {
              DocumentRecord record = invocation.getArgument(0);
              if (record == existing) {
                return record;
              }
              throw new IllegalStateException("db down");
            });
    MockMultipartFile file = textFile("notes.txt", "hello");

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    verify(transactionManager).rollback(any());
    verify(transactionManager, never()).commit(any());
    verifyNoInteractions(ingestionService);
  }

  // ---------------------------------------------------------------------------------------------
  // create: failures
  // ---------------------------------------------------------------------------------------------

  @Test
  void create_nullClient_throwsBadRequestWithoutTouchingStorage() {
    MockMultipartFile file = textFile("notes.txt", "hello");

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(null, file));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    verifyNoInteractions(documentRepository, ingestionService, fileStorage);
  }

  @Test
  void create_fileStreamCannotBeOpened_throwsInternalServerError() throws IOException {
    MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn("notes.txt");
    when(file.getContentType()).thenReturn("text/plain");
    when(file.getSize()).thenReturn(5L);
    when(file.getInputStream()).thenThrow(new IOException("disk gone"));

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    verifyNoInteractions(documentRepository, ingestionService, fileStorage);
  }

  @Test
  void create_existingLookupFails_throwsInternalServerError() {
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "notes.txt"))
        .thenThrow(new IllegalStateException("db down"));
    MockMultipartFile file = textFile("notes.txt", "hello");

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    verify(documentRepository, never()).save(any());
    verifyNoInteractions(ingestionService);
  }

  @Test
  void create_fileContentUnreadable_throwsInternalServerError() throws IOException {
    InputStream failing =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("read failed");
          }
        };
    MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn("notes.txt");
    when(file.getContentType()).thenReturn("text/plain");
    when(file.getSize()).thenReturn(5L);
    when(file.getInputStream()).thenReturn(failing);

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    verifyNoInteractions(documentRepository, ingestionService);
  }

  @Test
  void create_saveFails_throwsInternalServerErrorAndSkipsIngestion() {
    when(documentRepository.save(any(DocumentRecord.class)))
        .thenThrow(new IllegalStateException("db down"));
    MockMultipartFile file = textFile("notes.txt", "hello");

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    verifyNoInteractions(ingestionService);
  }

  // ---------------------------------------------------------------------------------------------
  // read: returning a document's metadata and stored content
  // ---------------------------------------------------------------------------------------------

  private DocumentRecord storedRecord(String name, long bytes, String hash) {
    DocumentRecord record = new DocumentRecord(clientId, name, "text/plain", bytes, hash);
    ReflectionTestUtils.setField(record, "id", UUID.randomUUID());
    return record;
  }

  private void stubExisting(DocumentRecord record) {
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, record.getName()))
        .thenReturn(Optional.of(record));
  }

  /** Stubs storage so that every open of {@code name} yields a fresh stream over the content. */
  private void stubStoredContent(String name, String content) throws IOException {
    when(fileStorage.open(clientId, name))
        .thenAnswer(
            invocation -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void read_existingDocument_returnsItsMetadata() throws Exception {
    DocumentRecord record = storedRecord("notes.txt", 5L, HELLO_SHA1);
    stubExisting(record);
    stubStoredContent("notes.txt", "hello");

    ReadDocument content = documentService.read(client, "notes.txt");

    assertEquals("notes.txt", content.fileName());
    assertEquals("text/plain", content.fileType());
    assertEquals(5L, content.fileSize());
    assertNotNull(content.inputStream());
  }

  @Test
  void read_existingDocument_streamYieldsStoredContent() throws Exception {
    stubExisting(storedRecord("notes.txt", 5L, HELLO_SHA1));
    stubStoredContent("notes.txt", "hello");

    ReadDocument content = documentService.read(client, "notes.txt");

    try (InputStream in = content.inputStream()) {
      assertEquals("hello", new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  @Test
  void read_emptyDocument_returnsEmptyStream() throws Exception {
    stubExisting(storedRecord("empty.txt", 0L, EMPTY_SHA1));
    stubStoredContent("empty.txt", "");

    ReadDocument content = documentService.read(client, "empty.txt");

    try (InputStream in = content.inputStream()) {
      assertEquals(0, in.readAllBytes().length);
    }
  }

  @Test
  void read_lookupIsScopedToCallerAndFileName() throws Exception {
    stubExisting(storedRecord("notes.txt", 5L, HELLO_SHA1));
    stubStoredContent("notes.txt", "hello");

    documentService.read(client, "notes.txt");

    verify(documentRepository).findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "notes.txt");
    // Opened twice: once to verify the hash, then again so the returned stream is unread.
    verify(fileStorage, times(2)).open(clientId, "notes.txt");
  }

  @Test
  void read_doesNotModifyOrSaveAnyRecord() throws Exception {
    stubExisting(storedRecord("notes.txt", 5L, HELLO_SHA1));
    stubStoredContent("notes.txt", "hello");

    documentService.read(client, "notes.txt");

    verify(documentRepository, never()).save(any());
    verifyNoInteractions(ingestionService);
  }

  // ---------------------------------------------------------------------------------------------
  // read: failures
  // ---------------------------------------------------------------------------------------------

  @Test
  void read_documentNotFoundOrDeleted_throwsNotFoundWithoutOpeningFile() {
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "missing.txt"))
        .thenReturn(Optional.empty());

    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.read(client, "missing.txt"));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    verifyNoInteractions(fileStorage);
  }

  @Test
  void read_nullClient_throwsBadRequestWithoutTouchingStorage() {
    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.read(null, "notes.txt"));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    verifyNoInteractions(documentRepository, fileStorage);
  }

  @Test
  void read_lookupFails_propagatesFailureWithoutOpeningFile() {
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "notes.txt"))
        .thenThrow(new IllegalStateException("db down"));

    // read() does not wrap repository failures, so an unexpected lookup error propagates as-is
    // rather than being reported as "document not found".
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class, () -> documentService.read(client, "notes.txt"));

    assertEquals("db down", ex.getMessage());
    verifyNoInteractions(fileStorage);
  }

  @Test
  void read_storedFileMissing_throwsInternalServerError() throws Exception {
    stubExisting(storedRecord("notes.txt", 5L, HELLO_SHA1));
    when(fileStorage.open(clientId, "notes.txt")).thenThrow(new IOException("no such file"));

    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.read(client, "notes.txt"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
  }

  @Test
  void read_fileNameRejectedByStorage_throwsInternalServerError() throws Exception {
    stubExisting(storedRecord("../evil.txt", 5L, HELLO_SHA1));
    when(fileStorage.open(clientId, "../evil.txt"))
        .thenThrow(new IllegalArgumentException("Invalid file name"));

    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.read(client, "../evil.txt"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
  }

  @Test
  void read_storedContentDoesNotMatchHash_throwsInternalServerError() throws Exception {
    stubExisting(storedRecord("notes.txt", 5L, HELLO_SHA1));
    stubStoredContent("notes.txt", "tampered");

    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.read(client, "notes.txt"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
  }

  @Test
  void read_storedContentUnreadable_throwsInternalServerError() throws Exception {
    stubExisting(storedRecord("notes.txt", 5L, HELLO_SHA1));
    InputStream failing =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("read failed");
          }
        };
    when(fileStorage.open(clientId, "notes.txt")).thenReturn(failing);

    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.read(client, "notes.txt"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
  }

  // ---------------------------------------------------------------------------------------------
  // read: with real file storage
  // ---------------------------------------------------------------------------------------------

  /** Creates a document through real storage and makes the mocked repository find it by name. */
  private DocumentService createThenExpose(Path dataDir, String name, String content) {
    saveAssignsIds();
    DocumentService service = serviceWithRealStorage(dataDir);
    service.create(client, textFile(name, content));
    ArgumentCaptor<DocumentRecord> captor = ArgumentCaptor.forClass(DocumentRecord.class);
    verify(documentRepository).save(captor.capture());
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, name))
        .thenReturn(Optional.of(captor.getValue()));
    return service;
  }

  @Test
  void read_realStorage_returnsContentWrittenByCreate(@TempDir Path dataDir) throws Exception {
    DocumentService service = createThenExpose(dataDir, "notes.txt", "hello");

    ReadDocument content = service.read(client, "notes.txt");

    assertEquals("notes.txt", content.fileName());
    try (InputStream in = content.inputStream()) {
      assertEquals("hello", new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  @Test
  void read_realStorage_fileChangedOnDiskAfterUpload_throwsInternalServerError(
      @TempDir Path dataDir) throws Exception {
    DocumentService service = createThenExpose(dataDir, "notes.txt", "hello");
    Files.writeString(dataDir.resolve(clientId.toString()).resolve("notes.txt"), "tampered");

    ApiException ex = assertThrows(ApiException.class, () -> service.read(client, "notes.txt"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
  }

  @Test
  void read_realStorage_fileDeletedFromDisk_throwsInternalServerError(@TempDir Path dataDir)
      throws Exception {
    DocumentService service = createThenExpose(dataDir, "notes.txt", "hello");
    Files.delete(dataDir.resolve(clientId.toString()).resolve("notes.txt"));

    ApiException ex = assertThrows(ApiException.class, () -> service.read(client, "notes.txt"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
  }

  // ---------------------------------------------------------------------------------------------
  // delete / list: still TODO, so they must fail clearly with 501
  // ---------------------------------------------------------------------------------------------

  @Test
  void delete_notImplementedYet_throwsNotImplemented() {
    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.delete(client, "notes.txt"));

    assertEquals(HttpStatus.NOT_IMPLEMENTED, ex.getStatus());
  }

  @Test
  void list_notImplementedYet_throwsNotImplemented() {
    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.list(client, null, null, null));

    assertEquals(HttpStatus.NOT_IMPLEMENTED, ex.getStatus());
  }
}
