package dev.coms4156.knowledgedrop.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * End to end: real embeddings from the embedding service, stored in a real Elasticsearch index with
 * the production 384-dimension mapping, then searched. Needs {@code docker compose up -d}.
 */
@SpringBootTest
@ActiveProfiles("test")
class EmbeddingRetrievalEndToEndTest {

  private static final String TERMINATION = "e2e-termination";

  @Autowired private ElasticsearchClient elasticsearch;
  @Autowired private SentenceTransformersEmbeddingClient embeddings;

  private final String indexName = "kd-vectors-e2e-" + UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();
  private final UUID documentId = UUID.randomUUID();
  private ElasticsearchVectorStore store;

  @BeforeEach
  void setUp() {
    store = new ElasticsearchVectorStore(elasticsearch, indexName, embeddings.dimensions());
    assertThat(store.ping())
        .as("Elasticsearch is not running. Start it with: docker compose up -d")
        .isTrue();
    try {
      embeddings.embed("ping");
    } catch (EmbeddingException e) {
      fail("Embedding service is not running. Start it with: docker compose up -d", e);
    }
  }

  @AfterEach
  void tearDown() throws IOException {
    if (store != null && store.ping()) {
      elasticsearch.indices().delete(d -> d.index(indexName).ignoreUnavailable(true));
    }
  }

  @Test
  void questionFindsTheChunkThatAnswersItWithDescendingScores() {
    List<String> ids = List.of(TERMINATION, "e2e-security", "e2e-law", "e2e-holidays");
    List<String> texts =
        List.of(
            "You may terminate this agreement at any time by giving thirty days written notice.",
            "The provider maintains reasonable security measures to protect customer data.",
            "This agreement is governed by the laws of the State of New York.",
            "Our offices are closed on public holidays and weekends.");
    List<RankedChunk> chunks = new ArrayList<>();
    for (int i = 0; i < ids.size(); i++) {
      chunks.add(new RankedChunk(ids.get(i), documentId, 1, texts.get(i), 0, 0.0));
    }
    store.index(clientId, documentId, chunks, embeddings.embedAll(texts));

    List<RankedChunk> results =
        store.search(clientId, embeddings.embed("How do I cancel my subscription?"), 4);

    assertThat(results).hasSize(4);
    assertThat(results.get(0).chunkId()).isEqualTo(TERMINATION);
    assertThat(results).extracting(RankedChunk::rank).containsExactly(1, 2, 3, 4);
    for (int i = 1; i < results.size(); i++) {
      assertThat(results.get(i - 1).score()).isGreaterThanOrEqualTo(results.get(i).score());
    }
  }

  @Test
  void anotherClientDoesNotSeeTheChunks() {
    List<String> texts = List.of("You may terminate this agreement at any time.");
    store.index(
        clientId,
        documentId,
        List.of(new RankedChunk(TERMINATION, documentId, 1, texts.get(0), 0, 0.0)),
        embeddings.embedAll(texts));

    assertThat(store.search(UUID.randomUUID(), embeddings.embed("cancel"), 4)).isEmpty();
  }
}
