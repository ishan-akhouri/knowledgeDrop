package dev.coms4156.knowledgedrop.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Hit;
import dev.coms4156.knowledgedrop.retrieval.ElasticsearchKeywordIndex.ChunkDocument;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests that need no running Elasticsearch. */
class ElasticsearchKeywordIndexTest {

  private final ElasticsearchClient client = mock(ElasticsearchClient.class);
  private final ElasticsearchKeywordIndex index = new ElasticsearchKeywordIndex(client, "test");

  @Test
  void keywordQuery_filtersByClientAndMatchesText() {
    UUID clientId = UUID.randomUUID();

    Query query = ElasticsearchKeywordIndex.keywordQuery(clientId, "refund policy");
    String json = query.toString();

    assertThat(json).contains("filter").contains("clientId").contains(clientId.toString());
    assertThat(json).contains("must").contains("text").contains("refund policy");
  }

  @Test
  void search_blankQuery_returnsEmptyWithoutCallingElasticsearch() {
    assertThat(index.search(UUID.randomUUID(), "   ", 5)).isEmpty();
    assertThat(index.search(UUID.randomUUID(), null, 5)).isEmpty();

    verifyNoInteractions(client);
  }

  @Test
  void search_nonPositiveLimit_returnsEmptyWithoutCallingElasticsearch() {
    assertThat(index.search(UUID.randomUUID(), "refund", 0)).isEmpty();
    assertThat(index.search(UUID.randomUUID(), "refund", -1)).isEmpty();

    verifyNoInteractions(client);
  }

  @Test
  void index_noChunks_doesNothing() {
    index.index(UUID.randomUUID(), UUID.randomUUID(), List.of());

    verifyNoInteractions(client);
  }

  @Test
  void toRankedChunks_ranksFromOneInHitOrderAndKeepsScores() {
    UUID documentId = UUID.randomUUID();
    List<Hit<ChunkDocument>> hits =
        List.of(
            hit("c-b", documentId, 2, "second chunk", 7.5),
            hit("c-a", documentId, 1, "x", 3.0));

    List<RankedChunk> ranked = ElasticsearchKeywordIndex.toRankedChunks(hits);

    assertThat(ranked).hasSize(2);
    assertThat(ranked.get(0))
        .isEqualTo(new RankedChunk("c-b", documentId, 2, "second chunk", 1, 7.5));
    assertThat(ranked.get(1)).isEqualTo(new RankedChunk("c-a", documentId, 1, "x", 2, 3.0));
  }

  @Test
  void toRankedChunks_missingScore_becomesZero() {
    ChunkDocument source =
        new ChunkDocument(
            "c-1", UUID.randomUUID().toString(), UUID.randomUUID().toString(), 0, 0, "text");
    Hit<ChunkDocument> unscored = Hit.of(h -> h.index("test").id("c-1").source(source));

    List<RankedChunk> ranked = ElasticsearchKeywordIndex.toRankedChunks(List.of(unscored));

    assertThat(ranked).singleElement().satisfies(c -> assertThat(c.score()).isZero());
  }

  @Test
  void toRankedChunks_emptyHits_returnsEmptyList() {
    assertThat(ElasticsearchKeywordIndex.toRankedChunks(List.of())).isEmpty();
  }

  @Test
  void ping_returnsFalseWhenElasticsearchIsUnreachable() throws IOException {
    when(client.ping()).thenThrow(new IOException("connection refused"));

    assertThat(index.ping()).isFalse();
  }

  private static Hit<ChunkDocument> hit(
      String chunkId, UUID documentId, int page, String text, double score) {
    ChunkDocument source =
        new ChunkDocument(
            chunkId, UUID.randomUUID().toString(), documentId.toString(), page, 0, text);
    return Hit.of(h -> h.index("test").id(chunkId).score(score).source(source));
  }
}
