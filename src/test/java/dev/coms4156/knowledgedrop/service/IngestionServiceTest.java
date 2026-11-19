package dev.coms4156.knowledgedrop.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.coms4156.knowledgedrop.config.KnowledgeDropProperties;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.model.DocumentStatus;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import dev.coms4156.knowledgedrop.retrieval.EmbeddingClient;
import dev.coms4156.knowledgedrop.retrieval.KeywordIndex;
import dev.coms4156.knowledgedrop.retrieval.RankedChunk;
import dev.coms4156.knowledgedrop.retrieval.VectorStore;
import dev.coms4156.knowledgedrop.storage.FileStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link IngestionService} with a mocked repository, file storage, keyword index,
 * vector store and embedding client. Text extraction uses the real Tika parser on plain-text
 * content, so chunking is checked end to end.
 */
@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

  private static final String HASH = "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d";
  private static final int CHUNK_SIZE = 10;
  private static final int CHUNK_OVERLAP = 3;

  @Mock private DocumentRepository documentRepository;

  @Mock private FileStorage fileStorage;

  @Mock private KeywordIndex keywordIndex;

  @Mock private VectorStore vectorStore;

  @Mock private EmbeddingClient embeddings;

  private IngestionService ingestionService;

  private UUID ownerId;
  private UUID documentId;
  private DocumentRecord document;

  @BeforeEach
  void setUp() {
    ingestionService = newService(CHUNK_SIZE, CHUNK_OVERLAP);
    ownerId = UUID.randomUUID();
    documentId = UUID.randomUUID();
    document = new DocumentRecord(ownerId, "notes.txt", "text/plain", 5, HASH);
    ReflectionTestUtils.setField(document, "id", documentId);
  }

  private IngestionService newService(int chunkSize, int chunkOverlap) {
    KnowledgeDropProperties properties =
        new KnowledgeDropProperties(
            "./data/files",
            new KnowledgeDropProperties.Ingestion(chunkSize, chunkOverlap),
            null,
            null,
            null);
    return new IngestionService(
        documentRepository, fileStorage, keywordIndex, vectorStore, embeddings, properties);
  }

  private static InputStream text(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }

  /** Stubs a found document whose file holds {@code content}, and embeddings for any chunks. */
  private void givenDocumentWithContent(String content) throws IOException {
    when(documentRepository.findByIdAndDeletedAtIsNull(documentId))
        .thenReturn(Optional.of(document));
    when(fileStorage.open(ownerId, "notes.txt")).thenReturn(text(content));
  }

  private void givenEmbeddingsSucceed() {
    when(embeddings.embedAll(anyList()))
        .thenAnswer(
            invocation -> {
              List<String> texts = invocation.getArgument(0);
              return texts.stream().map(t -> new float[] {t.length()}).toList();
            });
  }

  @SuppressWarnings("unchecked")
  private List<RankedChunk> capturedVectorChunks() {
    ArgumentCaptor<List<RankedChunk>> captor = ArgumentCaptor.forClass(List.class);
    verify(vectorStore).index(eq(ownerId), eq(documentId), captor.capture(), anyList());
    return captor.getValue();
  }

  // ---------------------------------------------------------------------------
  // Constructor validation
  // ---------------------------------------------------------------------------

  @Test
  void constructorRejectsZeroChunkSize() {
    assertThrows(IllegalStateException.class, () -> newService(0, 0));
  }

  @Test
  void constructorRejectsNegativeChunkSize() {
    assertThrows(IllegalStateException.class, () -> newService(-5, 0));
  }

  @Test
  void constructorRejectsNegativeOverlap() {
    assertThrows(IllegalStateException.class, () -> newService(10, -1));
  }

  @Test
  void constructorRejectsOverlapEqualToChunkSize() {
    IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> newService(10, 10));
    assertTrue(e.getMessage().contains("chunk-size=10"));
    assertTrue(e.getMessage().contains("chunk-overlap=10"));
  }

  @Test
  void constructorRejectsOverlapGreaterThanChunkSize() {
    assertThrows(IllegalStateException.class, () -> newService(10, 11));
  }

  @Test
  void constructorAcceptsZeroOverlap() {
    assertDoesNotThrow(() -> newService(10, 0));
  }

  // ---------------------------------------------------------------------------
  // Loading the document
  // ---------------------------------------------------------------------------

  @Test
  void ingestSkipsMissingDocument() {
    when(documentRepository.findByIdAndDeletedAtIsNull(documentId))
        .thenReturn(Optional.empty());

    ingestionService.ingest(documentId);

    verify(documentRepository, never()).save(any());
    verifyNoInteractions(fileStorage, embeddings, vectorStore, keywordIndex);
  }

  @Test
  void ingestReturnsQuietlyWhenRepositoryLookupFails() {
    when(documentRepository.findByIdAndDeletedAtIsNull(documentId))
        .thenThrow(new RuntimeException("db down"));

    ingestionService.ingest(documentId);

    verify(documentRepository, never()).save(any());
    verifyNoInteractions(fileStorage, embeddings, vectorStore, keywordIndex);
  }

  // ---------------------------------------------------------------------------
  // Successful ingestion
  // ---------------------------------------------------------------------------

  @Test
  void ingestShortDocumentProducesOneChunkAndMarksReady() throws IOException {
    givenDocumentWithContent("hello");
    givenEmbeddingsSucceed();

    ingestionService.ingest(documentId);

    List<RankedChunk> chunks = capturedVectorChunks();
    assertEquals(1, chunks.size());
    RankedChunk chunk = chunks.get(0);
    assertEquals(documentId + "-0", chunk.chunkId());
    assertEquals(documentId, chunk.documentId());
    assertEquals(0, chunk.page());
    assertEquals("hello", chunk.text().strip());
    assertEquals(0, chunk.rank());
    assertEquals(0.0, chunk.score());

    verify(keywordIndex).index(ownerId, documentId, chunks);
    verify(documentRepository).save(document);
    assertEquals(DocumentStatus.READY, document.getStatus());
    assertNull(document.getFailureReason());
  }

  @Test
  void ingestSplitsLongTextIntoOverlappingChunks() throws IOException {
    String content = "abcdefghijklmnopqrstuvwxyz";
    givenDocumentWithContent(content);
    givenEmbeddingsSucceed();

    ingestionService.ingest(documentId);

    List<RankedChunk> chunks = capturedVectorChunks();
    assertTrue(chunks.size() >= 3, "expected several chunks, got " + chunks.size());
    assertEquals("abcdefghij", chunks.get(0).text());
    assertEquals("hijklmnopq", chunks.get(1).text());
    assertEquals("opqrstuvwx", chunks.get(2).text());

    for (int i = 0; i < chunks.size(); i++) {
      RankedChunk chunk = chunks.get(i);
      assertEquals(documentId + "-" + i, chunk.chunkId());
      assertTrue(chunk.text().length() <= CHUNK_SIZE);
      if (i > 0) {
        String previous = chunks.get(i - 1).text();
        String overlap = previous.substring(previous.length() - CHUNK_OVERLAP);
        assertTrue(
            chunk.text().startsWith(overlap),
            "chunk " + i + " should start with the last " + CHUNK_OVERLAP + " characters");
      }
    }

    // Dropping each chunk's overlap prefix rebuilds the extracted text.
    StringBuilder rebuilt = new StringBuilder(chunks.get(0).text());
    for (int i = 1; i < chunks.size(); i++) {
      rebuilt.append(chunks.get(i).text().substring(CHUNK_OVERLAP));
    }
    assertEquals(content, rebuilt.toString().strip());
    assertEquals(DocumentStatus.READY, document.getStatus());
  }

  @Test
  void ingestEmbedsEveryChunkTextInOrder() throws IOException {
    givenDocumentWithContent("abcdefghijklmnopqrstuvwxyz");
    givenEmbeddingsSucceed();

    ingestionService.ingest(documentId);

    List<RankedChunk> chunks = capturedVectorChunks();
    verify(embeddings).embedAll(chunks.stream().map(RankedChunk::text).toList());
  }

  @Test
  void ingestPassesVectorsFromEmbeddingClientToVectorStore() throws IOException {
    givenDocumentWithContent("hello");
    List<float[]> vectors = List.of(new float[] {0.1f, 0.2f, 0.3f});
    when(embeddings.embedAll(anyList())).thenReturn(vectors);

    ingestionService.ingest(documentId);

    verify(vectorStore).index(eq(ownerId), eq(documentId), anyList(), eq(vectors));
  }

  @Test
  void ingestIndexesVectorsThenKeywordsThenMarksReady() throws IOException {
    givenDocumentWithContent("hello");
    givenEmbeddingsSucceed();

    ingestionService.ingest(documentId);

    InOrder order = inOrder(embeddings, vectorStore, keywordIndex, documentRepository);
    order.verify(embeddings).embedAll(anyList());
    order.verify(vectorStore).index(eq(ownerId), eq(documentId), anyList(), anyList());
    order.verify(keywordIndex).index(eq(ownerId), eq(documentId), anyList());
    order.verify(documentRepository).save(document);
  }

  @Test
  void ingestClearsEarlierFailureOnSuccess() throws IOException {
    document.markFailed("old failure");
    givenDocumentWithContent("hello");
    givenEmbeddingsSucceed();

    ingestionService.ingest(documentId);

    assertEquals(DocumentStatus.READY, document.getStatus());
    assertNull(document.getFailureReason());
  }

  @Test
  void ingestDoesNotDeleteChunksOnSuccess() throws IOException {
    givenDocumentWithContent("hello");
    givenEmbeddingsSucceed();

    ingestionService.ingest(documentId);

    verify(vectorStore, never()).deleteByDocument(any());
    verify(keywordIndex, never()).deleteByDocument(any());
  }

  // ---------------------------------------------------------------------------
  // Failures while reading or extracting
  // ---------------------------------------------------------------------------

  @Test
  void ingestMarksFailedWhenFileCannotBeOpened() throws IOException {
    when(documentRepository.findByIdAndDeletedAtIsNull(documentId))
        .thenReturn(Optional.of(document));
    when(fileStorage.open(ownerId, "notes.txt")).thenThrow(new IOException("no such file"));

    ingestionService.ingest(documentId);

    assertEquals(DocumentStatus.FAILED, document.getStatus());
    assertEquals("Could not read file: no such file", document.getFailureReason());
    verify(documentRepository).save(document);
    verifyNoInteractions(embeddings, vectorStore, keywordIndex);
  }

  @Test
  void ingestMarksFailedWhenStreamThrowsWhileReading() throws IOException {
    when(documentRepository.findByIdAndDeletedAtIsNull(documentId))
        .thenReturn(Optional.of(document));
    InputStream broken =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("disk error");
          }
        };
    when(fileStorage.open(ownerId, "notes.txt")).thenReturn(broken);

    ingestionService.ingest(documentId);

    assertEquals(DocumentStatus.FAILED, document.getStatus());
    assertTrue(
        document.getFailureReason().startsWith("Could not"),
        "unexpected reason: " + document.getFailureReason());
    verify(documentRepository).save(document);
    verifyNoInteractions(vectorStore, keywordIndex);
  }

  @Test
  void ingestMarksFailedWhenStorageRejectsFileName() throws IOException {
    when(documentRepository.findByIdAndDeletedAtIsNull(documentId))
        .thenReturn(Optional.of(document));
    when(fileStorage.open(ownerId, "notes.txt"))
        .thenThrow(new IllegalArgumentException("Invalid file name"));

    ingestionService.ingest(documentId);

    assertEquals(DocumentStatus.FAILED, document.getStatus());
    assertEquals("Could not extract text: Invalid file name", document.getFailureReason());
    verifyNoInteractions(embeddings, vectorStore, keywordIndex);
  }

  // ---------------------------------------------------------------------------
  // Failures while embedding
  // ---------------------------------------------------------------------------

  @Test
  void ingestMarksFailedWhenEmbeddingFails() throws IOException {
    givenDocumentWithContent("hello");
    when(embeddings.embedAll(anyList())).thenThrow(new RuntimeException("model offline"));

    ingestionService.ingest(documentId);

    assertEquals(DocumentStatus.FAILED, document.getStatus());
    assertEquals("Could not create embeddings: model offline", document.getFailureReason());
    verify(documentRepository).save(document);
    verifyNoInteractions(vectorStore, keywordIndex);
  }

  // ---------------------------------------------------------------------------
  // Failures while indexing
  // ---------------------------------------------------------------------------

  @Test
  void ingestCleansUpAndMarksFailedWhenVectorIndexFails() throws IOException {
    givenDocumentWithContent("hello");
    givenEmbeddingsSucceed();
    doThrow(new RuntimeException("es down"))
        .when(vectorStore)
        .index(eq(ownerId), eq(documentId), anyList(), anyList());

    ingestionService.ingest(documentId);

    verify(keywordIndex, never()).index(any(), any(), anyList());
    verify(vectorStore).deleteByDocument(documentId);
    verify(keywordIndex).deleteByDocument(documentId);
    assertEquals(DocumentStatus.FAILED, document.getStatus());
    assertEquals("Could not index document: es down", document.getFailureReason());
    verify(documentRepository).save(document);
  }

  @Test
  void ingestCleansUpAndMarksFailedWhenKeywordIndexFails() throws IOException {
    givenDocumentWithContent("hello");
    givenEmbeddingsSucceed();
    doThrow(new RuntimeException("bm25 down"))
        .when(keywordIndex)
        .index(eq(ownerId), eq(documentId), anyList());

    ingestionService.ingest(documentId);

    InOrder order = inOrder(vectorStore, keywordIndex, documentRepository);
    order.verify(vectorStore).deleteByDocument(documentId);
    order.verify(keywordIndex).deleteByDocument(documentId);
    order.verify(documentRepository).save(document);
    assertEquals(DocumentStatus.FAILED, document.getStatus());
    assertEquals("Could not index document: bm25 down", document.getFailureReason());
  }

  @Test
  void ingestCleansUpAndMarksFailedWhenSavingReadyStatusFails() throws IOException {
    givenDocumentWithContent("hello");
    givenEmbeddingsSucceed();
    when(documentRepository.save(document))
        .thenThrow(new RuntimeException("write failed"))
        .thenReturn(document);

    ingestionService.ingest(documentId);

    verify(vectorStore).deleteByDocument(documentId);
    verify(keywordIndex).deleteByDocument(documentId);
    assertEquals(DocumentStatus.FAILED, document.getStatus());
    assertEquals("Could not index document: write failed", document.getFailureReason());
  }

  @Test
  void ingestStillMarksFailedWhenCleanupFails() throws IOException {
    givenDocumentWithContent("hello");
    givenEmbeddingsSucceed();
    doThrow(new RuntimeException("es down"))
        .when(vectorStore)
        .index(eq(ownerId), eq(documentId), anyList(), anyList());
    doThrow(new RuntimeException("delete failed")).when(vectorStore).deleteByDocument(documentId);
    doThrow(new RuntimeException("delete failed")).when(keywordIndex).deleteByDocument(documentId);

    ingestionService.ingest(documentId);

    assertEquals(DocumentStatus.FAILED, document.getStatus());
    verify(documentRepository).save(document);
  }

  @Test
  void ingestTruncatesVeryLongFailureReason() throws IOException {
    givenDocumentWithContent("hello");
    when(embeddings.embedAll(anyList())).thenThrow(new RuntimeException("x".repeat(5000)));

    ingestionService.ingest(documentId);

    assertEquals(1000, document.getFailureReason().length());
  }

  @Test
  void ingestPropagatesWhenFailureCannotBeRecorded() throws IOException {
    givenDocumentWithContent("hello");
    when(embeddings.embedAll(anyList())).thenThrow(new RuntimeException("model offline"));
    RuntimeException saveError = new RuntimeException("db down");
    when(documentRepository.save(document)).thenThrow(saveError);

    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> ingestionService.ingest(documentId));
    assertSame(saveError, thrown);
  }

  // ---------------------------------------------------------------------------
  // deleteChunks
  // ---------------------------------------------------------------------------

  @Test
  void deleteChunksDeletesFromBothStores() {
    ingestionService.deleteChunks(documentId);

    verify(vectorStore).deleteByDocument(documentId);
    verify(keywordIndex).deleteByDocument(documentId);
  }

  @Test
  void deleteChunksStillDeletesKeywordsWhenVectorDeleteFails() {
    doThrow(new RuntimeException("es down")).when(vectorStore).deleteByDocument(documentId);

    ingestionService.deleteChunks(documentId);

    verify(keywordIndex).deleteByDocument(documentId);
  }

  @Test
  void deleteChunksSwallowsKeywordDeleteFailure() {
    doThrow(new RuntimeException("es down")).when(keywordIndex).deleteByDocument(documentId);

    ingestionService.deleteChunks(documentId);

    verify(vectorStore).deleteByDocument(documentId);
  }

  @Test
  void deleteChunksDoesNotTouchRepository() {
    ingestionService.deleteChunks(documentId);

    verifyNoInteractions(documentRepository, fileStorage, embeddings);
  }
}
