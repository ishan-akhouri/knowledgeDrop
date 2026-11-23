package dev.coms4156.knowledgedrop.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import dev.coms4156.knowledgedrop.config.KnowledgeDropProperties;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.model.DocumentStatus;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class RetrievalServiceReadinessTest {
  private final UUID owner = UUID.randomUUID();
  private final EmbeddingClient embeddings = mock(EmbeddingClient.class);
  private final VectorStore vectors = mock(VectorStore.class);
  private final KeywordIndex keywords = mock(KeywordIndex.class);
  private final DocumentRepository documents = mock(DocumentRepository.class);
  private final RetrievalService service =
      new RetrievalService(embeddings, vectors, keywords, documents, properties());
  private final float[] embedding = {1f, 0f, 0f};

  static KnowledgeDropProperties properties() {
    return new KnowledgeDropProperties(null, null,
        new KnowledgeDropProperties.Retrieval(5, 60, 50), null, null);
  }

  static DocumentRecord document(UUID id, DocumentStatus status) {
    DocumentRecord document = new DocumentRecord(UUID.randomUUID(), "test", "text/plain", 0,
        "0".repeat(40));
    ReflectionTestUtils.setField(document, "id", id);
    if (status == DocumentStatus.READY) {
      document.markReady();
    } else if (status == DocumentStatus.FAILED) {
      document.markFailed("failed");
    }
    return document;
  }

  @ParameterizedTest
  @ValueSource(strings = {"keyword", "vector", "hybrid"})
  void filtersBeforeLimitAndRenumbersUsingOneDistinctLookup(String mode) {
    UUID processing = UUID.randomUUID();
    UUID failed = UUID.randomUUID();
    UUID missing = UUID.randomUUID();
    UUID deleted = UUID.randomUUID();
    UUID ready = UUID.randomUUID();
    DocumentRecord softDeleted = document(deleted, DocumentStatus.READY);
    softDeleted.setDeletedAt(Instant.now());
    List<RankedChunk> candidates = List.of(
        chunk("processing", processing, 1), chunk("failed", failed, 2),
        chunk("missing", missing, 3), chunk("deleted", deleted, 4),
        chunk("ready-a", ready, 5), chunk("ready-b", ready, 6),
        chunk("ready-c", ready, 7));
    when(embeddings.embed("refund")).thenReturn(embedding);
    when(keywords.search(owner, "refund", 50)).thenReturn(candidates);
    when(vectors.search(owner, embedding, 50)).thenReturn(candidates);
    Set<UUID> ids = Set.of(processing, failed, missing, deleted, ready);
    when(documents.findAllByOwnerIdAndIdIn(owner, ids)).thenReturn(List.of(
        document(processing, DocumentStatus.PROCESSING), document(failed, DocumentStatus.FAILED),
        softDeleted, document(ready, DocumentStatus.READY)));

    List<RankedChunk> result = search(mode, "refund", 2);

    assertThat(result).extracting(RankedChunk::chunkId).containsExactly("ready-a", "ready-b");
    assertThat(result).extracting(RankedChunk::rank).containsExactly(1, 2);
    // Fusion must see filtered lists: the first survivor contributes rank 1 in each list.
    assertThat(result.get(0).score()).isEqualTo(mode.equals("hybrid") ? 2.0 / 61 : 5.0);
    verify(documents).findAllByOwnerIdAndIdIn(owner, ids);
    verifyNoMoreInteractions(documents);
  }

  @ParameterizedTest
  @ValueSource(strings = {"keyword", "vector", "hybrid"})
  void emptyAndInvalidRequestsDoNotQueryDocuments(String mode) {
    assertThat(search(mode, " ", 2)).isEmpty();
    assertThat(search(mode, null, 2)).isEmpty();
    assertThat(search(mode, "refund", 0)).isEmpty();
    assertThat(search(mode, "refund", -1)).isEmpty();
    verifyNoInteractions(embeddings, keywords, vectors, documents);
    when(embeddings.embed("refund")).thenReturn(embedding);
    when(keywords.search(owner, "refund", 50)).thenReturn(List.of());
    when(vectors.search(owner, embedding, 50)).thenReturn(List.of());
    assertThat(search(mode, "refund", 2)).isEmpty();
    verifyNoInteractions(documents);
  }

  @Test
  void hybridLooksUpUnionOfDifferentPoolsOnlyOnce() {
    UUID vectorDoc = UUID.randomUUID();
    UUID keywordDoc = UUID.randomUUID();
    when(embeddings.embed("refund")).thenReturn(embedding);
    when(vectors.search(owner, embedding, 50))
        .thenReturn(List.of(chunk("vector", vectorDoc, 1)));
    when(keywords.search(owner, "refund", 50))
        .thenReturn(List.of(chunk("keyword", keywordDoc, 1)));
    Set<UUID> ids = Set.of(vectorDoc, keywordDoc);
    when(documents.findAllByOwnerIdAndIdIn(owner, ids)).thenReturn(List.of(
        document(vectorDoc, DocumentStatus.READY), document(keywordDoc, DocumentStatus.READY)));

    assertThat(service.hybrid(owner, "refund", 2)).extracting(RankedChunk::chunkId)
        .containsExactly("keyword", "vector");
    verify(documents).findAllByOwnerIdAndIdIn(owner, ids);
    verifyNoMoreInteractions(documents);
  }

  @ParameterizedTest
  @ValueSource(strings = {"keyword", "vector", "hybrid"})
  void poolIsAtLeastRequestedSizeAndAllMissingDocumentsReturnEmpty(String mode) {
    UUID missing = UUID.randomUUID();
    when(embeddings.embed("refund")).thenReturn(embedding);
    when(keywords.search(owner, "refund", 60)).thenReturn(List.of(chunk("missing", missing, 1)));
    when(vectors.search(owner, embedding, 60)).thenReturn(List.of(chunk("missing", missing, 1)));
    when(documents.findAllByOwnerIdAndIdIn(owner, Set.of(missing))).thenReturn(List.of());
    assertThat(search(mode, "refund", 60)).isEmpty();
    verify(documents).findAllByOwnerIdAndIdIn(owner, Set.of(missing));
    verifyNoMoreInteractions(documents);
  }

  private List<RankedChunk> search(String mode, String query, int k) {
    return switch (mode) {
      case "keyword" -> service.keyword(owner, query, k);
      case "vector" -> service.vector(owner, query, k);
      default -> service.hybrid(owner, query, k);
    };
  }

  private RankedChunk chunk(String id, UUID documentId, int rank) {
    return new RankedChunk(id, documentId, 1, "refund", rank, 5.0);
  }
}
