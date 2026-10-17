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
import dev.coms4156.knowledgedrop.config.KnowledgeDropProperties;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.model.DocumentStatus;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import dev.coms4156.knowledgedrop.storage.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
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

  @InjectMocks private DocumentService documentService;

  private ClientRecord client;
  private UUID clientId;

  @BeforeEach
  void setUp() {
    clientId = UUID.randomUUID();
    client = new ClientRecord("acme", "token-hash");
    ReflectionTestUtils.setField(client, "id", clientId);
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

  /** Builds a service that writes through a real {@link FileStorage} rooted at {@code dataDir}. */
  private DocumentService serviceWithRealStorage(Path dataDir) {
    KnowledgeDropProperties properties =
        new KnowledgeDropProperties(
            dataDir.toString(),
            new KnowledgeDropProperties.Ingestion(800, 100),
            new KnowledgeDropProperties.Retrieval(5, 60, 50),
            new KnowledgeDropProperties.Embedding("http://localhost:5000", "model", 384),
            new KnowledgeDropProperties.Llm("", "", ""));
    return new DocumentService(documentRepository, ingestionService, new FileStorage(properties));
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
    verify(ingestionService).ingest(stored.getId());
  }

  @Test
  void create_emptyFile_hashesEmptyContent() {
    saveAssignsIds();
    MockMultipartFile file = textFile("empty.txt", "");

    documentService.create(client, file);

    ArgumentCaptor<DocumentRecord> captor = ArgumentCaptor.forClass(DocumentRecord.class);
    verify(documentRepository).save(captor.capture());
    assertEquals(0L, captor.getValue().getFileBytes());
    assertEquals(EMPTY_SHA1, captor.getValue().getContentHash());
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
  void create_savesFileAfterRecordAndBeforeIngestion() throws Exception {
    saveAssignsIds();

    DocumentStatusResponse response = documentService.create(client, textFile("notes.txt", "hi"));

    InOrder order = inOrder(documentRepository, fileStorage, ingestionService);
    order.verify(documentRepository).save(any(DocumentRecord.class));
    order.verify(fileStorage).save(eq(clientId), eq("notes.txt"), any(InputStream.class));
    order.verify(ingestionService).ingest(response.documentId());
  }

  @Test
  void create_emptyFile_stillSavesFile() throws Exception {
    saveAssignsIds();
    AtomicReference<byte[]> savedBytes = new AtomicReference<>();
    when(fileStorage.save(eq(clientId), eq("empty.txt"), any(InputStream.class)))
        .thenAnswer(
            invocation -> {
              InputStream in = invocation.getArgument(2);
              savedBytes.set(in.readAllBytes());
              return Path.of("unused");
            });

    documentService.create(client, textFile("empty.txt", ""));

    verify(fileStorage).save(eq(clientId), eq("empty.txt"), any(InputStream.class));
    assertEquals(0, savedBytes.get().length);
  }

  @Test
  void create_existingDocumentWithSameName_savesNewFileOnce() throws Exception {
    saveAssignsIds();
    DocumentRecord existing =
        new DocumentRecord(clientId, "notes.txt", "text/plain", 3L, EMPTY_SHA1);
    ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "notes.txt"))
        .thenReturn(Optional.of(existing));

    documentService.create(client, textFile("notes.txt", "hello"));

    verify(fileStorage, times(1)).save(eq(clientId), eq("notes.txt"), any(InputStream.class));
  }

  @Test
  void create_fileStorageFails_throwsInternalServerErrorAndSkipsIngestion() throws Exception {
    saveAssignsIds();
    when(fileStorage.save(any(UUID.class), any(String.class), any(InputStream.class)))
        .thenThrow(new IOException("disk full"));
    MockMultipartFile file = textFile("notes.txt", "hello");

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    verifyNoInteractions(ingestionService);
  }

  @Test
  void create_fileStorageRejectsFileName_throwsInternalServerErrorAndSkipsIngestion()
      throws Exception {
    saveAssignsIds();
    when(fileStorage.save(any(UUID.class), any(String.class), any(InputStream.class)))
        .thenThrow(new IllegalArgumentException("Invalid file name"));
    MockMultipartFile file = textFile("../evil.txt", "hello");

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    verifyNoInteractions(ingestionService);
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
    saveAssignsIds();
    DocumentService service = serviceWithRealStorage(dataDir);
    MockMultipartFile file = textFile("../../escaped.txt", "evil");

    ApiException ex = assertThrows(ApiException.class, () -> service.create(client, file));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    assertFalse(Files.exists(dataDir.resolve("escaped.txt")));
    assertFalse(Files.exists(dataDir.getParent().resolve("escaped.txt")));
    verifyNoInteractions(ingestionService);
  }

  // ---------------------------------------------------------------------------------------------
  // create: replacing an existing document
  // ---------------------------------------------------------------------------------------------

  @Test
  void create_existingDocumentWithSameName_softDeletesOldAndIngestsNew() {
    saveAssignsIds();
    DocumentRecord existing =
        new DocumentRecord(clientId, "notes.txt", "text/plain", 3L, EMPTY_SHA1);
    UUID existingId = UUID.randomUUID();
    ReflectionTestUtils.setField(existing, "id", existingId);
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "notes.txt"))
        .thenReturn(Optional.of(existing));

    DocumentStatusResponse response =
        documentService.create(client, textFile("notes.txt", "hello"));
    assertEquals(DocumentStatus.PROCESSING, response.status());

    ArgumentCaptor<DocumentRecord> captor = ArgumentCaptor.forClass(DocumentRecord.class);
    verify(documentRepository, times(2)).save(captor.capture());
    List<DocumentRecord> saved = captor.getAllValues();

    assertSame(existing, saved.get(0));
    assertNotNull(existing.getDeletedAt());

    DocumentRecord replacement = saved.get(1);
    assertNotEquals(existingId, replacement.getId());
    assertNull(replacement.getDeletedAt());
    assertEquals(HELLO_SHA1, replacement.getContentHash());
    assertEquals(replacement.getId(), response.documentId());
    verify(ingestionService).ingest(replacement.getId());
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
  void create_fileStreamCannotBeOpened_throwsBadRequest() throws IOException {
    MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn("notes.txt");
    when(file.getContentType()).thenReturn("text/plain");
    when(file.getSize()).thenReturn(5L);
    when(file.getInputStream()).thenThrow(new IOException("disk gone"));

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
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
    verifyNoInteractions(ingestionService, fileStorage);
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
    verify(documentRepository, never()).save(any());
    verifyNoInteractions(ingestionService, fileStorage);
  }

  @Test
  void create_saveFails_throwsInternalServerErrorAndSkipsIngestion() {
    when(documentRepository.save(any(DocumentRecord.class)))
        .thenThrow(new IllegalStateException("db down"));
    MockMultipartFile file = textFile("notes.txt", "hello");

    ApiException ex = assertThrows(ApiException.class, () -> documentService.create(client, file));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    verifyNoInteractions(ingestionService, fileStorage);
  }

  // ---------------------------------------------------------------------------------------------
  // read / delete / list: still TODO, so they must fail clearly with 501
  // ---------------------------------------------------------------------------------------------

  @Test
  void read_notImplementedYet_throwsNotImplemented() {
    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.read(client, "notes.txt"));

    assertEquals(HttpStatus.NOT_IMPLEMENTED, ex.getStatus());
  }

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
