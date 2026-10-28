package dev.coms4156.knowledgedrop.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.coms4156.knowledgedrop.api.dto.ReadDocument;
import dev.coms4156.knowledgedrop.config.KnowledgeDropProperties;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import dev.coms4156.knowledgedrop.storage.FileStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Additional unit tests for {@link DocumentService#read}: stream handling, hash verification,
 * failure messages, client scoping, and round trips through a real {@link FileStorage}.
 * Complements {@link DocumentServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceReadEdgeCasesTest {

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
    client = new ClientRecord("acme", "token-hash");
    ReflectionTestUtils.setField(client, "id", clientId);
  }

  // ---------------------------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------------------------

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

  private static String text(InputStream in) throws IOException {
    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
  }

  private DocumentRecord recordFor(String name, String type, byte[] content)
      throws NoSuchAlgorithmException {
    DocumentRecord record =
        new DocumentRecord(clientId, name, type, content.length, sha1Hex(content));
    ReflectionTestUtils.setField(record, "id", UUID.randomUUID());
    return record;
  }

  private void stubLookup(DocumentRecord record) {
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, record.getName()))
        .thenReturn(Optional.of(record));
  }

  /** Stubs a document record and storage content that agree with each other. */
  private void stubDocument(String name, String type, byte[] content) throws Exception {
    stubLookup(recordFor(name, type, content));
    when(fileStorage.open(clientId, name))
        .thenAnswer(invocation -> new ByteArrayInputStream(content));
  }

  private static TrackingStream trackedStream(List<TrackingStream> opened, byte[] content) {
    TrackingStream stream = new TrackingStream(content);
    opened.add(stream);
    return stream;
  }

  private void saveAssignsIds() {
    when(documentRepository.save(any(DocumentRecord.class))).thenAnswer(this::assignId);
  }

  private void saveAndFlushAssignsIds() {
    when(documentRepository.saveAndFlush(any(DocumentRecord.class))).thenAnswer(this::assignId);
  }

  private DocumentRecord assignId(InvocationOnMock invocation) {
    DocumentRecord record = invocation.getArgument(0);
    if (record.getId() == null) {
      ReflectionTestUtils.setField(record, "id", UUID.randomUUID());
    }
    return record;
  }

  private DocumentRecord lastSavedRecord(int expectedSaves) {
    ArgumentCaptor<DocumentRecord> captor = ArgumentCaptor.forClass(DocumentRecord.class);
    verify(documentRepository, times(expectedSaves)).save(captor.capture());
    return captor.getValue();
  }

  private DocumentRecord lastSavedAndFlushedRecord(int expectedSaves) {
    ArgumentCaptor<DocumentRecord> captor = ArgumentCaptor.forClass(DocumentRecord.class);
    verify(documentRepository, times(expectedSaves)).saveAndFlush(captor.capture());
    return captor.getValue();
  }

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
  // lookup and scoping
  // ---------------------------------------------------------------------------------------------

  @Test
  void read_documentNotFoundOrDeleted_reportsItInTheMessage() {
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "gone.txt"))
        .thenReturn(Optional.empty());

    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.read(client, "gone.txt"));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    assertEquals("Document not found or has been deleted", ex.getMessage());
    verifyNoInteractions(fileStorage);
  }

  @Test
  void read_otherClientsDocumentWithSameName_isNotVisible() {
    UUID otherClientId = UUID.randomUUID();
    ClientRecord other = new ClientRecord("other", "other-token-hash");
    ReflectionTestUtils.setField(other, "id", otherClientId);

    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.read(other, "notes.txt"));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    verify(documentRepository).findByOwnerIdAndNameAndDeletedAtIsNull(otherClientId, "notes.txt");
    verify(documentRepository, never())
        .findByOwnerIdAndNameAndDeletedAtIsNull(eq(clientId), any(String.class));
    verifyNoInteractions(fileStorage);
  }

  // ---------------------------------------------------------------------------------------------
  // returned content and metadata
  // ---------------------------------------------------------------------------------------------

  @ParameterizedTest
  @CsvSource({
    "report.pdf,application/pdf",
    "photo.png,image/png",
    "notes.md,text/markdown"
  })
  void read_returnsTheNameAndTypeStoredWithTheDocument(String name, String type)
      throws Exception {
    stubDocument(name, type, HELLO_BYTES);

    ReadDocument result = documentService.read(client, name);

    assertEquals(name, result.fileName());
    assertEquals(type, result.fileType());
    assertEquals(5L, result.fileSize());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 8191, 8192, 8193, 16384, 100_000})
  void read_contentAroundBufferBoundaries_isVerifiedAndReturnedIntact(int size)
      throws Exception {
    byte[] content = patternedBytes(size);
    stubDocument("data.bin", "application/octet-stream", content);

    ReadDocument result = documentService.read(client, "data.bin");

    assertEquals((long) size, result.fileSize());
    try (InputStream in = result.inputStream()) {
      assertArrayEquals(content, in.readAllBytes());
    }
  }

  @Test
  void read_calledTwice_returnsIndependentStreams() throws Exception {
    stubDocument("notes.txt", "text/plain", HELLO_BYTES);

    ReadDocument first = documentService.read(client, "notes.txt");
    ReadDocument second = documentService.read(client, "notes.txt");

    assertNotSame(first.inputStream(), second.inputStream());
    try (InputStream in = first.inputStream()) {
      assertEquals("hello", text(in));
    }
    try (InputStream in = second.inputStream()) {
      assertEquals("hello", text(in));
    }
  }

  // ---------------------------------------------------------------------------------------------
  // stream handling
  // ---------------------------------------------------------------------------------------------

  @Test
  void read_closesTheHashStreamButLeavesTheReturnedStreamOpen() throws Exception {
    stubLookup(recordFor("notes.txt", "text/plain", HELLO_BYTES));
    List<TrackingStream> opened = new ArrayList<>();
    when(fileStorage.open(clientId, "notes.txt"))
        .thenAnswer(invocation -> trackedStream(opened, HELLO_BYTES));

    ReadDocument result = documentService.read(client, "notes.txt");

    assertSame(opened.get(1), result.inputStream());
    assertEquals(2, opened.size());
    assertTrue(opened.get(0).isClosed());
    assertFalse(opened.get(1).isClosed());
    result.inputStream().close();
    assertTrue(opened.get(1).isClosed());
  }

  @Test
  void read_hashMismatch_closesTheHashStreamAndNeverReopensTheFile() throws Exception {
    stubLookup(recordFor("notes.txt", "text/plain", HELLO_BYTES));
    List<TrackingStream> opened = new ArrayList<>();
    byte[] tampered = "tampered".getBytes(StandardCharsets.UTF_8);
    when(fileStorage.open(clientId, "notes.txt"))
        .thenAnswer(invocation -> trackedStream(opened, tampered));

    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.read(client, "notes.txt"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    assertEquals("File content hash mismatch", ex.getMessage());
    assertEquals(1, opened.size());
    assertTrue(opened.get(0).isClosed());
  }

  // ---------------------------------------------------------------------------------------------
  // failure messages
  // ---------------------------------------------------------------------------------------------

  @Test
  void read_storedFileCannotBeOpened_reportsHashFailure() throws Exception {
    stubLookup(recordFor("notes.txt", "text/plain", HELLO_BYTES));
    when(fileStorage.open(clientId, "notes.txt")).thenThrow(new IOException("no such file"));

    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.read(client, "notes.txt"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    assertEquals("Failed to compute file hash: no such file", ex.getMessage());
  }

  @Test
  void read_storageRejectsTheFileName_reportsUnexpectedError() throws Exception {
    stubLookup(recordFor("notes.txt", "text/plain", HELLO_BYTES));
    when(fileStorage.open(clientId, "notes.txt"))
        .thenThrow(new IllegalArgumentException("Invalid file name"));

    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.read(client, "notes.txt"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    assertEquals("Unexpected error: Invalid file name", ex.getMessage());
  }

  @Test
  void read_storedContentUnreadable_reportsHashFailure() throws Exception {
    stubLookup(recordFor("notes.txt", "text/plain", HELLO_BYTES));
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

    assertEquals("Failed to compute file hash: read failed", ex.getMessage());
  }

  @Test
  void read_fileCannotBeReopenedAfterVerification_reportsReopenFailure() throws Exception {
    stubLookup(recordFor("notes.txt", "text/plain", HELLO_BYTES));
    when(fileStorage.open(clientId, "notes.txt"))
        .thenAnswer(invocation -> new ByteArrayInputStream(HELLO_BYTES))
        .thenThrow(new IOException("gone"));

    ApiException ex =
        assertThrows(ApiException.class, () -> documentService.read(client, "notes.txt"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    assertEquals("Failed to reopen file content: gone", ex.getMessage());
    verify(fileStorage, times(2)).open(clientId, "notes.txt");
  }

  // ---------------------------------------------------------------------------------------------
  // round trips through real file storage
  // ---------------------------------------------------------------------------------------------

  @Test
  void createThenRead_realStorage_binaryContentRoundTrips(@TempDir Path dataDir)
      throws Exception {
    saveAssignsIds();
    DocumentService service = serviceWithRealStorage(dataDir);
    byte[] content = patternedBytes(50_000);
    service.create(
        client, new MockMultipartFile("file", "blob.bin", "application/octet-stream", content));
    DocumentRecord saved = lastSavedRecord(1);
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "blob.bin"))
        .thenReturn(Optional.of(saved));

    ReadDocument result = service.read(client, "blob.bin");

    assertEquals(50_000L, result.fileSize());
    assertEquals("application/octet-stream", result.fileType());
    try (InputStream in = result.inputStream()) {
      assertArrayEquals(content, in.readAllBytes());
    }
  }

  @Test
  void createReplaceRead_realStorage_returnsTheLatestUpload(@TempDir Path dataDir)
      throws Exception {
    saveAssignsIds();
    saveAndFlushAssignsIds();
    DocumentService service = serviceWithRealStorage(dataDir);
    service.create(client, new MockMultipartFile("file", "notes.txt", "text/plain", HELLO_BYTES));
    DocumentRecord first = lastSavedRecord(1);
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "notes.txt"))
        .thenReturn(Optional.of(first));
    service.create(
        client,
        new MockMultipartFile(
            "file", "notes.txt", "text/plain", "second".getBytes(StandardCharsets.UTF_8)));
    DocumentRecord second = lastSavedAndFlushedRecord(2);
    when(documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, "notes.txt"))
        .thenReturn(Optional.of(second));

    ReadDocument result = service.read(client, "notes.txt");

    assertNotNull(first.getDeletedAt());
    assertNull(second.getDeletedAt());
    assertEquals(6L, result.fileSize());
    try (InputStream in = result.inputStream()) {
      assertEquals("second", text(in));
    }
  }
}
