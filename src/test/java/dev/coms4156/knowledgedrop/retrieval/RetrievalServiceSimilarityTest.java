package dev.coms4156.knowledgedrop.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.coms4156.knowledgedrop.api.dto.SimilarDocument;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.model.DocumentStatus;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetrievalServiceSimilarityTest {
  private final UUID owner = UUID.randomUUID();
  private final EmbeddingClient embeddings = mock(EmbeddingClient.class);
  private final VectorStore vectors = mock(VectorStore.class);
  private final KeywordIndex keywords = mock(KeywordIndex.class);
  private final DocumentRepository documents = mock(DocumentRepository.class);
  private final RetrievalService service =
      new RetrievalService(embeddings, vectors, keywords, documents, new RrfMerger(60), 7);
  private final float[] embedding = {1, 0};

  @Test
  void groupsBeforeLimitAndSelectsMaximumEvenForUnsortedNegativeScores() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    when(embeddings.embed("text")).thenReturn(embedding);
    when(vectors.search(owner, embedding, 7)).thenReturn(List.of(
        chunk("a", first, -0.9), chunk("b", first, -0.1), chunk("c", second, -0.2)));
    when(documents.findAllByOwnerIdAndIdIn(owner, Set.of(first, second)))
        .thenReturn(List.of(ready(first), ready(second)));

    List<SimilarDocument> result = service.similarity(owner, "text", 2);

    assertThat(result).extracting(SimilarDocument::documentId).containsExactly(first, second);
    assertThat(result).extracting(SimilarDocument::score).containsExactly(-0.1, -0.2);
    assertThat(result).extracting(SimilarDocument::snippet).containsExactly("b", "c");
    verifyNoInteractions(keywords);
  }

  @Test
  void tiesAreStableAndExplicitLimitCanExceedPool() {
    UUID first = new UUID(0, 1);
    UUID second = new UUID(0, 2);
    when(embeddings.embed("text")).thenReturn(embedding);
    List<RankedChunk> hits = List.of(chunk("z", second, 0.8),
        chunk("b", first, 0.8), chunk("a", first, 0.8));
    when(vectors.search(owner, embedding, 8)).thenReturn(hits);
    when(vectors.search(owner, embedding, 7)).thenReturn(hits);
    when(documents.findAllByOwnerIdAndIdIn(owner, Set.of(first, second)))
        .thenReturn(List.of(ready(first), ready(second)));
    assertThat(service.similarity(owner, "text", 8)).extracting(SimilarDocument::documentId)
        .containsExactly(first, second);
    assertThat(service.similarity(owner, "text", 1)).extracting(SimilarDocument::snippet)
        .containsExactly("a");
  }

  @Test
  void invalidInputsAndEmptyCandidatesReturnEmpty() {
    assertThat(service.similarity(owner, null, 1)).isEmpty();
    assertThat(service.similarity(owner, "  ", 1)).isEmpty();
    assertThat(service.similarity(owner, "text", 0)).isEmpty();
    verifyNoInteractions(embeddings, vectors, documents, keywords);
    when(embeddings.embed("text")).thenReturn(embedding);
    when(vectors.search(owner, embedding, 7)).thenReturn(List.of());
    assertThat(service.similarity(owner, "text", 5)).isEmpty();
    verifyNoInteractions(documents, keywords);
  }

  @Test
  void hybridGroupsTheEntireMergedPoolBeforeLimitingDocuments() {
    UUID first = new UUID(0, 1);
    UUID second = new UUID(0, 2);
    RetrievalService smallPool =
        new RetrievalService(embeddings, vectors, keywords, documents, new RrfMerger(60), 2);
    when(embeddings.embed("text")).thenReturn(embedding);
    when(vectors.search(owner, embedding, 2)).thenReturn(List.of(
        chunk("a", first, 0.9), chunk("b", first, 0.8)));
    when(keywords.search(owner, "text", 2)).thenReturn(List.of(
        chunk("b", first, 4), chunk("c", second, 3)));
    when(documents.findAllByOwnerIdAndIdIn(owner, Set.of(first, second)))
        .thenReturn(List.of(ready(first), ready(second)));
    when(documents.findAllByOwnerIdAndIdIn(owner, Set.of(first)))
        .thenReturn(List.of(ready(first)));

    List<SimilarDocument> results = smallPool.similarity(owner, "text", 2, "hybrid");

    assertThat(results).extracting(SimilarDocument::documentId).containsExactly(first, second);
    assertThat(results.get(0).snippet()).isEqualTo("b");
    assertThat(results.get(0).score()).isEqualTo(1.0 / 61 + 1.0 / 62);
  }

  private DocumentRecord ready(UUID id) {
    return RetrievalServiceReadinessTest.document(id, DocumentStatus.READY);
  }

  private RankedChunk chunk(String id, UUID documentId, double score) {
    return new RankedChunk(id, documentId, 0, id, 1, score);
  }
}
