package dev.coms4156.knowledgedrop.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetrievalServiceHybridTest {

  private static final float[] QUERY_VECTOR = {0.1f, 0.2f};

  private final UUID clientId = UUID.randomUUID();
  private final EmbeddingClient embeddings = mock(EmbeddingClient.class);
  private final VectorStore vectorStore = mock(VectorStore.class);
  private final KeywordIndex keywordIndex = mock(KeywordIndex.class);

  private RetrievalService serviceWithPool(int candidatePool) {
    return new RetrievalService(
        embeddings, vectorStore, keywordIndex, new RrfMerger(60), candidatePool);
  }

  private static RankedChunk chunk(String id, int rank) {
    return new RankedChunk(id, UUID.randomUUID(), 1, "text " + id, rank, 0.5);
  }

  @Test
  void hybrid_fusesBothListsWithRrfAndCutsToK() {
    when(embeddings.embed("refund policy")).thenReturn(QUERY_VECTOR);
    when(vectorStore.search(clientId, QUERY_VECTOR, 50))
        .thenReturn(List.of(chunk("a", 1), chunk("b", 2)));
    when(keywordIndex.search(clientId, "refund policy", 50))
        .thenReturn(List.of(chunk("b", 1), chunk("c", 2)));

    List<RankedChunk> results = serviceWithPool(50).hybrid(clientId, "refund policy", 2);

    assertThat(results).extracting(RankedChunk::chunkId).containsExactly("b", "a");
    assertThat(results).extracting(RankedChunk::rank).containsExactly(1, 2);
    assertThat(results.get(0).score()).isCloseTo(1.0 / 61 + 1.0 / 62, within(1e-12));
  }

  @Test
  void hybrid_asksEachStoreForTheWholeCandidatePool() {
    when(embeddings.embed("refund")).thenReturn(QUERY_VECTOR);

    serviceWithPool(50).hybrid(clientId, "refund", 3);

    verify(vectorStore).search(clientId, QUERY_VECTOR, 50);
    verify(keywordIndex).search(clientId, "refund", 50);
  }

  @Test
  void hybrid_neverAsksForFewerCandidatesThanK() {
    when(embeddings.embed("refund")).thenReturn(QUERY_VECTOR);

    serviceWithPool(5).hybrid(clientId, "refund", 8);

    verify(vectorStore).search(clientId, QUERY_VECTOR, 8);
    verify(keywordIndex).search(clientId, "refund", 8);
  }

  @Test
  void hybrid_stillReturnsResultsWhenOneModeFindsNothing() {
    when(embeddings.embed("refund")).thenReturn(QUERY_VECTOR);
    when(keywordIndex.search(clientId, "refund", 50))
        .thenReturn(List.of(chunk("a", 1), chunk("b", 2)));

    List<RankedChunk> results = serviceWithPool(50).hybrid(clientId, "refund", 5);

    assertThat(results).extracting(RankedChunk::chunkId).containsExactly("a", "b");
  }

  @Test
  void hybrid_returnsNothingWithoutCallingTheStoresForBlankQueryOrNonPositiveK() {
    RetrievalService service = serviceWithPool(50);

    assertThat(service.hybrid(clientId, null, 5)).isEmpty();
    assertThat(service.hybrid(clientId, "   ", 5)).isEmpty();
    assertThat(service.hybrid(clientId, "refund", 0)).isEmpty();
    assertThat(service.hybrid(clientId, "refund", -1)).isEmpty();
    verifyNoInteractions(embeddings, vectorStore, keywordIndex);
  }

  @Test
  void constructor_rejectsNonPositiveCandidatePool() {
    assertThatThrownBy(() -> serviceWithPool(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("candidatePool");
  }
}
