package dev.coms4156.knowledgedrop.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Fails with a clear message when Elasticsearch is not reachable. Uses a throwaway index, never
 * the real one.
 */
@SpringBootTest
@ActiveProfiles("test")
class ElasticsearchKeywordIndexIntegrationTest {

  @Autowired private ElasticsearchClient client;

  private final UUID clientA = UUID.randomUUID();
  private final UUID clientB = UUID.randomUUID();
  private final UUID docA = UUID.randomUUID();
  private final UUID docB = UUID.randomUUID();
  private final String indexName = "kd-chunks-test-" + UUID.randomUUID();
  private ElasticsearchKeywordIndex index;

  @BeforeEach
  void setUp() {
    index = new ElasticsearchKeywordIndex(client, indexName);
    assertThat(index.ping())
        .as("Elasticsearch is not running. Start it with: docker compose up -d")
        .isTrue();
  }

  @AfterEach
  void tearDown() throws IOException {
    if (index != null && index.ping()) {
      client.indices().delete(d -> d.index(indexName).ignoreUnavailable(true));
    }
  }

  @Test
  void search_returnsOnlyTheCallersChunksRankedBestFirstFromOne() {
    index.index(
        clientA,
        docA,
        List.of(
            chunk("a-0", docA, 1, "The refund policy allows refunds within thirty days."),
            chunk("a-1", docA, 2, "Shipping takes five business days."),
            chunk("a-2", docA, 2, "A refund refund refund is issued for refund requests.")));
    index.index(clientB, docB, List.of(chunk("b-0", docB, 1, "Another client's refund policy.")));

    List<RankedChunk> results = index.search(clientA, "refund", 10);

    assertThat(results).extracting(RankedChunk::chunkId).containsExactly("a-2", "a-0");
    assertThat(results).extracting(RankedChunk::rank).containsExactly(1, 2);
    assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
    assertThat(results).allMatch(c -> c.documentId().equals(docA));
  }

  @Test
  void search_respectsLimit() {
    index.index(
        clientA,
        docA,
        List.of(
            chunk("a-0", docA, 1, "refund one"),
            chunk("a-1", docA, 1, "refund two"),
            chunk("a-2", docA, 1, "refund three")));

    assertThat(index.search(clientA, "refund", 2)).hasSize(2);
  }

  @Test
  void search_noMatch_returnsEmpty() {
    index.index(clientA, docA, List.of(chunk("a-0", docA, 1, "Shipping takes five days.")));

    assertThat(index.search(clientA, "refund", 10)).isEmpty();
  }

  @Test
  void chunksInOrder_returnsEveryChunkSortedByPosition() {
    index.index(
        clientA,
        docA,
        List.of(
            chunk("a-0", docA, 1, "first"),
            chunk("a-1", docA, 1, "second"),
            chunk("a-2", docA, 2, "third")));

    List<RankedChunk> chunks = index.chunksInOrder(docA);

    assertThat(chunks).extracting(RankedChunk::text).containsExactly("first", "second", "third");
    assertThat(chunks).extracting(RankedChunk::page).containsExactly(1, 1, 2);
  }

  @Test
  void deleteByDocument_removesOnlyThatDocument() {
    index.index(clientA, docA, List.of(chunk("a-0", docA, 1, "refund a")));
    UUID otherDoc = UUID.randomUUID();
    index.index(clientA, otherDoc, List.of(chunk("o-0", otherDoc, 1, "refund other")));

    index.deleteByDocument(docA);

    assertThat(index.chunksInOrder(docA)).isEmpty();
    assertThat(index.search(clientA, "refund", 10)).extracting(RankedChunk::chunkId)
        .containsExactly("o-0");
  }

  @Test
  void deleteByClient_removesEverythingTheClientOwnsAndNothingElse() {
    index.index(clientA, docA, List.of(chunk("a-0", docA, 1, "refund a")));
    index.index(clientB, docB, List.of(chunk("b-0", docB, 1, "refund b")));

    index.deleteByClient(clientA);

    assertThat(index.search(clientA, "refund", 10)).isEmpty();
    assertThat(index.search(clientB, "refund", 10)).hasSize(1);
  }

  @Test
  void reindexingTheSameChunkIdReplacesItInsteadOfDuplicating() {
    index.index(clientA, docA, List.of(chunk("a-0", docA, 1, "refund old text")));
    index.index(clientA, docA, List.of(chunk("a-0", docA, 1, "refund new text")));

    assertThat(index.search(clientA, "refund", 10)).hasSize(1);
    assertThat(index.chunksInOrder(docA)).extracting(RankedChunk::text)
        .containsExactly("refund new text");
  }

  private static RankedChunk chunk(String chunkId, UUID documentId, int page, String text) {
    return new RankedChunk(chunkId, documentId, page, text, 0, 0.0);
  }
}
