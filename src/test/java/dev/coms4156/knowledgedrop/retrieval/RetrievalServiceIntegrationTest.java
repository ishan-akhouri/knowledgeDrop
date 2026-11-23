package dev.coms4156.knowledgedrop.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Real Elasticsearch candidates checked against persisted document state. */
@SpringBootTest
@ActiveProfiles("test")
class RetrievalServiceIntegrationTest {
  @Autowired private ElasticsearchClient client;
  @Autowired private DocumentRepository documents;
  private final UUID owner = UUID.randomUUID();
  private final String keywordName = "kd-ready-keyword-" + UUID.randomUUID();
  private final String vectorName = "kd-ready-vector-" + UUID.randomUUID();
  private ElasticsearchKeywordIndex keywords;
  private ElasticsearchVectorStore vectors;
  private RetrievalService service;
  private final float[] embedding = {1f, 0f, 0f};

  @BeforeEach
  void setUp() {
    keywords = new ElasticsearchKeywordIndex(client, keywordName);
    vectors = new ElasticsearchVectorStore(client, vectorName, 3);
    EmbeddingClient embeddings = mock(EmbeddingClient.class);
    when(embeddings.embed("refund")).thenReturn(embedding);
    service = new RetrievalService(embeddings, vectors, keywords, documents,
        RetrievalServiceReadinessTest.properties());
  }

  @AfterEach
  void tearDown() throws IOException {
    client.indices().delete(d -> d.index(keywordName, vectorName).ignoreUnavailable(true));
    documents.deleteAll(documents.findByOwnerId(owner,
        org.springframework.data.domain.Pageable.unpaged()).getContent());
  }

  @Test
  void indexedChunksFollowDatabaseStatusAndDeletionInEveryMode() {
    DocumentRecord ready = save("ready");
    ready.markReady();
    documents.saveAndFlush(ready);
    DocumentRecord stale = save("stale");
    index(ready);
    index(stale);
    // Prove the stale candidate is actually searchable in both underlying indexes.
    assertThat(keywords.search(owner, "refund", 50)).hasSize(2);
    assertThat(vectors.search(owner, embedding, 50)).hasSize(2);
    assertOnlyReady(ready);
    stale.markFailed("interrupted ingestion");
    documents.saveAndFlush(stale);
    assertOnlyReady(ready);
    stale.markReady();
    documents.saveAndFlush(stale);
    assertThat(service.hybrid(owner, "refund", 5)).hasSize(2);
    stale.setDeletedAt(Instant.now());
    documents.saveAndFlush(stale);
    assertOnlyReady(ready);
    documents.delete(stale);
    documents.flush();
    assertOnlyReady(ready);
  }

  private DocumentRecord save(String name) {
    return documents.saveAndFlush(new DocumentRecord(owner, name, "text/plain", 6,
        "0".repeat(40)));
  }

  private void index(DocumentRecord document) {
    List<RankedChunk> chunks = List.of(new RankedChunk(document.getId() + "-0",
        document.getId(), 1, "refund", 0, 0));
    keywords.index(owner, document.getId(), chunks);
    vectors.index(owner, document.getId(), chunks, List.of(embedding));
  }

  private void assertOnlyReady(DocumentRecord ready) {
    for (List<RankedChunk> result : List.of(service.keyword(owner, "refund", 1),
        service.vector(owner, "refund", 1), service.hybrid(owner, "refund", 1))) {
      assertThat(result).extracting(RankedChunk::documentId).containsExactly(ready.getId());
      assertThat(result).extracting(RankedChunk::rank).containsExactly(1);
    }
    for (List<RankedChunk> result : List.of(service.keyword(owner, "refund", 50),
        service.vector(owner, "refund", 50), service.hybrid(owner, "refund", 50))) {
      assertThat(result).extracting(RankedChunk::documentId).containsExactly(ready.getId());
    }
  }
}
