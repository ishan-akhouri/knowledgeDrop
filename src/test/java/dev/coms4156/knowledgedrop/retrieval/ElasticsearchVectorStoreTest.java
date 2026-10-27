package dev.coms4156.knowledgedrop.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.search.Hit;
import dev.coms4156.knowledgedrop.retrieval.ElasticsearchKeywordIndex.ChunkDocument;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests that need no running Elasticsearch. */
class ElasticsearchVectorStoreTest {

  private static final int DIMENSIONS = 3;

  private final ElasticsearchClient client = mock(ElasticsearchClient.class);
  private final ElasticsearchVectorStore store =
      new ElasticsearchVectorStore(client, "test", DIMENSIONS);

  @Test
  void index_differentNumberOfChunksAndVectors_isRejected() {
    List<RankedChunk> chunks = List.of(chunk("c-1"), chunk("c-2"));
    List<float[]> vectors = List.of(new float[] {1f, 0f, 0f});

    assertThatThrownBy(() -> store.index(UUID.randomUUID(), UUID.randomUUID(), chunks, vectors))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("one vector per chunk");
    verifyNoInteractions(client);
  }

  @Test
  void index_vectorWithWrongDimension_isRejected() {
    List<RankedChunk> chunks = List.of(chunk("c-1"));
    List<float[]> vectors = List.of(new float[] {1f, 0f});

    assertThatThrownBy(() -> store.index(UUID.randomUUID(), UUID.randomUUID(), chunks, vectors))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("3 dimensions but got 2");
    verifyNoInteractions(client);
  }

  @Test
  void index_noChunks_doesNothing() {
    store.index(UUID.randomUUID(), UUID.randomUUID(), List.of(), List.of());

    verifyNoInteractions(client);
  }

  @Test
  void search_queryVectorWithWrongDimension_isRejected() {
    UUID clientId = UUID.randomUUID();
    float[] tooLong = {1f, 0f, 0f, 0f};

    assertThatThrownBy(() -> store.search(clientId, tooLong, 5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("3 dimensions but got 4");
    assertThatThrownBy(() -> store.search(clientId, null, 5))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(client);
  }

  @Test
  void search_nonPositiveLimit_returnsEmptyWithoutCallingElasticsearch() {
    float[] vector = {1f, 0f, 0f};

    assertThat(store.search(UUID.randomUUID(), vector, 0)).isEmpty();
    assertThat(store.search(UUID.randomUUID(), vector, -1)).isEmpty();

    verifyNoInteractions(client);
  }

  @Test
  void clientFilter_matchesTheClientIdField() {
    UUID clientId = UUID.randomUUID();

    String json = ElasticsearchVectorStore.clientFilter(clientId).toString();

    assertThat(json).contains("clientId").contains(clientId.toString());
  }

  @Test
  void numCandidates_isTenTimesTheLimitWithinElasticsearchBounds() {
    assertThat(ElasticsearchVectorStore.numCandidates(1)).isEqualTo(100);
    assertThat(ElasticsearchVectorStore.numCandidates(5)).isEqualTo(100);
    assertThat(ElasticsearchVectorStore.numCandidates(50)).isEqualTo(500);
    assertThat(ElasticsearchVectorStore.numCandidates(5_000)).isEqualTo(10_000);
    assertThat(ElasticsearchVectorStore.numCandidates(Integer.MAX_VALUE)).isEqualTo(10_000);
  }

  @Test
  void toCosine_undoesElasticsearchsCosineScoreScaling() {
    assertThat(ElasticsearchVectorStore.toCosine(1.0)).isEqualTo(1.0);
    assertThat(ElasticsearchVectorStore.toCosine(0.5)).isEqualTo(0.0);
    assertThat(ElasticsearchVectorStore.toCosine(0.0)).isEqualTo(-1.0);
  }

  @Test
  void toRankedChunks_ranksFromOneAndReportsCosineScores() {
    UUID documentId = UUID.randomUUID();
    List<Hit<ChunkDocument>> hits =
        List.of(hit("c-b", documentId, 2, "closest", 0.9), hit("c-a", documentId, 1, "next", 0.5));

    List<RankedChunk> ranked = ElasticsearchVectorStore.toRankedChunks(hits);

    assertThat(ranked).extracting(RankedChunk::chunkId).containsExactly("c-b", "c-a");
    assertThat(ranked).extracting(RankedChunk::rank).containsExactly(1, 2);
    assertThat(ranked.get(0).score()).isCloseTo(0.8, within(1e-9));
    assertThat(ranked.get(1).score()).isCloseTo(0.0, within(1e-9));
  }

  @Test
  void ping_returnsFalseWhenElasticsearchIsUnreachable() throws IOException {
    when(client.ping()).thenThrow(new IOException("connection refused"));

    assertThat(store.ping()).isFalse();
  }

  private static RankedChunk chunk(String chunkId) {
    return new RankedChunk(chunkId, UUID.randomUUID(), 1, "text", 0, 0.0);
  }

  private static Hit<ChunkDocument> hit(
      String chunkId, UUID documentId, int page, String text, double score) {
    ChunkDocument source =
        new ChunkDocument(
            chunkId, UUID.randomUUID().toString(), documentId.toString(), page, 0, text);
    return Hit.of(h -> h.index("test").id(chunkId).score(score).source(source));
  }
}
