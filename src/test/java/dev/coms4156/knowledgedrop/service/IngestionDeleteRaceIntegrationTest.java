package dev.coms4156.knowledgedrop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
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
import dev.coms4156.knowledgedrop.retrieval.VectorStore;
import dev.coms4156.knowledgedrop.storage.FileStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Deletes a document while its ingestion is in progress, against the real repository and an
 * in-memory database, and checks the delete wins. Elasticsearch, embeddings and file storage are
 * mocked; ingestion is called directly, as the background thread would call it (no transaction).
 */
@SpringBootTest
@ActiveProfiles("test")
class IngestionDeleteRaceIntegrationTest {

  private static final String HASH = "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d";

  @Autowired private DocumentRepository documentRepository;

  private final FileStorage fileStorage = mock(FileStorage.class);
  private final KeywordIndex keywordIndex = mock(KeywordIndex.class);
  private final VectorStore vectorStore = mock(VectorStore.class);
  private final EmbeddingClient embeddings = mock(EmbeddingClient.class);

  private IngestionService ingestionService;
  private UUID ownerId;
  private UUID documentId;

  @BeforeEach
  void setUp() throws IOException {
    KnowledgeDropProperties properties =
        new KnowledgeDropProperties(
            "./target/test-data/files",
            new KnowledgeDropProperties.Ingestion(10, 3),
            null,
            null,
            null);
    ingestionService =
        new IngestionService(
            documentRepository, fileStorage, keywordIndex, vectorStore, embeddings, properties);

    ownerId = UUID.randomUUID();
    DocumentRecord document = new DocumentRecord(ownerId, "notes.txt", "text/plain", 5, HASH);
    documentId = documentRepository.save(document).getId();
    when(fileStorage.open(ownerId, "notes.txt"))
        .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
  }

  /** Soft-deletes the document the way the delete endpoint does, in its own transaction. */
  private void deleteDocument() {
    DocumentRecord current = documentRepository.findById(documentId).orElseThrow();
    current.setDeletedAt(Instant.now());
    documentRepository.save(current);
  }

  private DocumentRecord reload() {
    return documentRepository.findById(documentId).orElseThrow();
  }

  private void givenEmbeddingsSucceed() {
    when(embeddings.embedAll(anyList())).thenAnswer(inv -> embed(inv.getArgument(0)));
  }

  private static List<float[]> embed(List<String> texts) {
    return texts.stream().map(t -> new float[] {1}).toList();
  }

  @Test
  void liveDocumentIsMarkedReady() {
    givenEmbeddingsSucceed();

    ingestionService.ingest(documentId);

    DocumentRecord stored = reload();
    assertEquals(DocumentStatus.READY, stored.getStatus());
    assertNull(stored.getDeletedAt());
    verify(vectorStore, never()).deleteByDocument(any());
  }

  @Test
  void deleteDuringIngestionStaysDeletedAndChunksAreRemoved() {
    // the delete lands while the slow embedding step runs, after the record was loaded
    when(embeddings.embedAll(anyList()))
        .thenAnswer(
            inv -> {
              deleteDocument();
              return embed(inv.getArgument(0));
            });

    ingestionService.ingest(documentId);

    DocumentRecord stored = reload();
    assertNotNull(stored.getDeletedAt(), "ingestion must not undo the delete");
    assertEquals(DocumentStatus.PROCESSING, stored.getStatus());
    verify(vectorStore).deleteByDocument(documentId);
    verify(keywordIndex).deleteByDocument(documentId);
  }

  @Test
  void deleteDuringIngestionThatThenFailsStaysDeletedAndUnmarked() {
    when(embeddings.embedAll(anyList()))
        .thenAnswer(
            inv -> {
              deleteDocument();
              throw new RuntimeException("model offline");
            });

    ingestionService.ingest(documentId);

    DocumentRecord stored = reload();
    assertNotNull(stored.getDeletedAt());
    assertEquals(DocumentStatus.PROCESSING, stored.getStatus());
    assertNull(stored.getFailureReason());
  }

  @Test
  void deleteWhileQueuedSkipsIngestionEntirely() {
    deleteDocument();

    ingestionService.ingest(documentId);

    assertNotNull(reload().getDeletedAt());
    verifyNoInteractions(fileStorage, embeddings, vectorStore, keywordIndex);
  }
}
