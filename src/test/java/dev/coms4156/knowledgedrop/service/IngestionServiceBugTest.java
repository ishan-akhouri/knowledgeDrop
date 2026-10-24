package dev.coms4156.knowledgedrop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** White-box tests that each target a suspected defect in {@link IngestionService}. */
@ExtendWith(MockitoExtension.class)
class IngestionServiceBugTest {

  private static final String HASH = "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d";

  @Mock private DocumentRepository documentRepository;
  @Mock private FileStorage fileStorage;
  @Mock private KeywordIndex keywordIndex;
  @Mock private VectorStore vectorStore;
  @Mock private EmbeddingClient embeddings;

  private IngestionService service;
  private UUID ownerId;
  private UUID documentId;

  @BeforeEach
  void setUp() {
    KnowledgeDropProperties properties =
        new KnowledgeDropProperties(
            "./data/files", new KnowledgeDropProperties.Ingestion(10, 3), null, null, null);
    service =
        new IngestionService(
            documentRepository, fileStorage, keywordIndex, vectorStore, embeddings, properties);
    ownerId = UUID.randomUUID();
    documentId = UUID.randomUUID();
    lenient()
        .when(embeddings.embedAll(anyList()))
        .thenAnswer(
            inv -> ((List<String>) inv.getArgument(0)).stream().map(t -> new float[] {1}).toList());
  }

  private DocumentRecord givenDocument(String name, byte[] content) throws IOException {
    DocumentRecord document = new DocumentRecord(ownerId, name, "text/plain", 5, HASH);
    ReflectionTestUtils.setField(document, "id", documentId);
    when(documentRepository.findByIdAndDeletedAtIsNull(documentId))
        .thenReturn(Optional.of(document));
    lenient()
        .when(fileStorage.open(ownerId, name))
        .thenReturn(new ByteArrayInputStream(content));
    return document;
  }

  @SuppressWarnings("unchecked")
  private List<RankedChunk> capturedChunks() {
    ArgumentCaptor<List<RankedChunk>> captor = ArgumentCaptor.forClass(List.class);
    verify(vectorStore).index(eq(ownerId), eq(documentId), captor.capture(), anyList());
    return captor.getValue();
  }

  /** A document deleted (or replaced) before its job runs must not become searchable. */
  @Test
  void softDeletedDocumentIsNotIngested() {
    DocumentRecord document = new DocumentRecord(ownerId, "notes.txt", "text/plain", 5, HASH);
    ReflectionTestUtils.setField(document, "id", documentId);
    document.setDeletedAt(Instant.now());
    lenient().when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
    when(documentRepository.findByIdAndDeletedAtIsNull(documentId))
        .thenReturn(Optional.empty());

    service.ingest(documentId);

    verifyNoInteractions(fileStorage, embeddings);
    verify(vectorStore, never()).index(any(), any(), anyList(), anyList());
    verify(keywordIndex, never()).index(any(), any(), anyList());
  }

  /** A file with no extractable text (e.g. a scanned PDF) should not be reported as ready. */
  @Test
  void documentWithNoTextIsNotMarkedReady() throws IOException {
    DocumentRecord document = givenDocument("notes.txt", new byte[0]);

    service.ingest(documentId);

    assertEquals(DocumentStatus.FAILED, document.getStatus());
    assertEquals("No text could be extracted from the file", document.getFailureReason());
    verifyNoInteractions(embeddings, vectorStore, keywordIndex);
  }

  /** Whitespace-only text is no more searchable than no text. */
  @Test
  void documentWithOnlyWhitespaceIsMarkedFailed() throws IOException {
    DocumentRecord document =
        givenDocument("notes.txt", " \n\t\n             \n".getBytes(StandardCharsets.UTF_8));

    service.ingest(documentId);

    assertEquals(DocumentStatus.FAILED, document.getStatus());
    verifyNoInteractions(embeddings, vectorStore, keywordIndex);
  }

  /** RankedChunk documents page 0 for sources without pages; plain text has no pages. */
  @Test
  void plainTextChunksHavePageZero() throws IOException {
    givenDocument("notes.txt", "hello".getBytes(StandardCharsets.UTF_8));

    service.ingest(documentId);

    assertEquals(0, capturedChunks().get(0).page());
  }

  /** Chunk boundaries must not split a UTF-16 surrogate pair (emoji, many CJK characters). */
  @Test
  @Disabled("Known bug: chunking splits surrogate pairs; not fixed yet")
  void chunkBoundaryDoesNotSplitSurrogatePair() throws IOException {
    givenDocument("notes.txt", "aaaaaaaaa😀bbbb".getBytes(StandardCharsets.UTF_8));

    service.ingest(documentId);

    for (RankedChunk chunk : capturedChunks()) {
      String text = chunk.text();
      assertFalse(
          Character.isHighSurrogate(text.charAt(text.length() - 1)),
          "chunk ends with a lone high surrogate: " + chunk.chunkId());
      assertFalse(
          Character.isLowSurrogate(text.charAt(0)),
          "chunk starts with a lone low surrogate: " + chunk.chunkId());
    }
  }

  /** One vector per chunk is required; a short vector list must not be indexed. */
  @Test
  @Disabled("Known bug: vector count is not checked against chunk count; not fixed yet")
  void mismatchedVectorCountFailsIngestion() throws IOException {
    final DocumentRecord document =
        givenDocument("notes.txt", "abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8));
    when(embeddings.embedAll(anyList())).thenReturn(List.of(new float[] {1}));

    service.ingest(documentId);

    verify(vectorStore, never()).index(any(), any(), anyList(), anyList());
    assertEquals(DocumentStatus.FAILED, document.getStatus());
  }

  /** A corrupt PDF is a parse failure, not a file read failure. */
  @Test
  @Disabled("Known bug: Tika parse failures are reported as file read failures; not fixed yet")
  void corruptPdfIsReportedAsExtractionFailure() throws IOException {
    DocumentRecord document =
        givenDocument("report.pdf", "%PDF-1.7\nthis is not a pdf".getBytes(StandardCharsets.UTF_8));

    service.ingest(documentId);

    assertEquals(DocumentStatus.FAILED, document.getStatus());
    assertTrue(
        document.getFailureReason().startsWith("Could not extract text"),
        "reason was: " + document.getFailureReason());
  }
}
