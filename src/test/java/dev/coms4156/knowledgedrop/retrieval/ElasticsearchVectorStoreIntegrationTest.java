package dev.coms4156.knowledgedrop.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs against the local Elasticsearch from docker-compose.yml ({@code docker compose up -d}).
 * Fails with a clear message when Elasticsearch is not reachable. Uses tiny 4-dimensional vectors
 * in a throwaway index, never the real one.
 */
@SpringBootTest
@ActiveProfiles("test")
class ElasticsearchVectorStoreIntegrationTest {

  private static final int DIMENSIONS = 4;

  @Autowired private ElasticsearchClient client;

  private final UUID clientA = UUID.randomUUID();
  private final UUID clientB = UUID.randomUUID();
  private final UUID docA = UUID.randomUUID();
  private final UUID docB = UUID.randomUUID();
  private final String indexName = "kd-vectors-test-" + UUID.randomUUID();
  private ElasticsearchVectorStore store;

  @BeforeEach
  void setUp() {
    store = new ElasticsearchVectorStore(client, indexName, DIMENSIONS);
    assertThat(store.ping())
        .as("Elasticsearch is not running. Start it with: docker compose up -d")
        .isTrue();
  }

  @AfterEach
  void tearDown() throws IOException {
    if (store != null && store.ping()) {
      client.indices().delete(d -> d.index(indexName).ignoreUnavailable(true));
    }
  }

  @Test
  void search_returnsOnlyTheCallersChunksNearestFirstWithCosineScores() {
    store.index(
        clientA,
        docA,
        List.of(chunk("a-0", docA, "same"), chunk("a-1", docA, "close"), chunk("a-2", docA, "far")),
        List.of(vec(1f, 0f, 0f, 0f), vec(0.9f, 0.1f, 0f, 0f), vec(0f, 1f, 0f, 0f)));
    store.index(
        clientB, docB, List.of(chunk("b-0", docB, "other client")), List.of(vec(1f, 0f, 0f, 0f)));

    List<RankedChunk> results = store.search(clientA, vec(1f, 0f, 0f, 0f), 10);

    assertThat(results).extracting(RankedChunk::chunkId).containsExactly("a-0", "a-1", "a-2");
    assertThat(results).extracting(RankedChunk::rank).containsExactly(1, 2, 3);
    assertThat(results).allMatch(c -> c.documentId().equals(docA));
    assertThat(results.get(0).score()).isCloseTo(1.0, within(0.01));
    assertThat(results.get(1).score()).isCloseTo(0.994, within(0.01));
    assertThat(results.get(2).score()).isCloseTo(0.0, within(0.01));
    assertThat(results.get(0).text()).isEqualTo("same");
  }

  @Test
  void search_respectsLimit() {
    store.index(
        clientA,
        docA,
        List.of(chunk("a-0", docA, "one"), chunk("a-1", docA, "two"), chunk("a-2", docA, "three")),
        List.of(vec(1f, 0f, 0f, 0f), vec(0.9f, 0.1f, 0f, 0f), vec(0.5f, 0.5f, 0f, 0f)));

    List<RankedChunk> results = store.search(clientA, vec(1f, 0f, 0f, 0f), 2);

    assertThat(results).extracting(RankedChunk::chunkId).containsExactly("a-0", "a-1");
  }

  @Test
  void search_clientWithNoChunks_returnsEmpty() {
    store.index(clientA, docA, List.of(chunk("a-0", docA, "one")), List.of(vec(1f, 0f, 0f, 0f)));

    assertThat(store.search(clientB, vec(1f, 0f, 0f, 0f), 10)).isEmpty();
  }

  @Test
  void deleteByDocument_removesOnlyThatDocument() {
    UUID otherDoc = UUID.randomUUID();
    store.index(clientA, docA, List.of(chunk("a-0", docA, "a")), List.of(vec(1f, 0f, 0f, 0f)));
    store.index(
        clientA, otherDoc, List.of(chunk("o-0", otherDoc, "o")), List.of(vec(0.9f, 0.1f, 0f, 0f)));

    store.deleteByDocument(docA);

    assertThat(store.search(clientA, vec(1f, 0f, 0f, 0f), 10))
        .extracting(RankedChunk::chunkId)
        .containsExactly("o-0");
  }

  @Test
  void deleteByClient_removesEverythingTheClientOwnsAndNothingElse() {
    store.index(clientA, docA, List.of(chunk("a-0", docA, "a")), List.of(vec(1f, 0f, 0f, 0f)));
    store.index(clientB, docB, List.of(chunk("b-0", docB, "b")), List.of(vec(1f, 0f, 0f, 0f)));

    store.deleteByClient(clientA);

    assertThat(store.search(clientA, vec(1f, 0f, 0f, 0f), 10)).isEmpty();
    assertThat(store.search(clientB, vec(1f, 0f, 0f, 0f), 10)).hasSize(1);
  }

  @Test
  void reindexingTheSameChunkIdReplacesItInsteadOfDuplicating() {
    store.index(clientA, docA, List.of(chunk("a-0", docA, "old")), List.of(vec(1f, 0f, 0f, 0f)));
    store.index(clientA, docA, List.of(chunk("a-0", docA, "new")), List.of(vec(0f, 1f, 0f, 0f)));

    List<RankedChunk> results = store.search(clientA, vec(0f, 1f, 0f, 0f), 10);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).text()).isEqualTo("new");
    assertThat(results.get(0).score()).isCloseTo(1.0, within(0.01));
  }

  private static RankedChunk chunk(String chunkId, UUID documentId, String text) {
    return new RankedChunk(chunkId, documentId, 1, text, 0, 0.0);
  }

  private static float[] vec(float... values) {
    return values;
  }
}
