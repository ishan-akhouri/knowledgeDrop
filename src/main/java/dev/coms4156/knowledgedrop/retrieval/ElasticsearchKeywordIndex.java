package dev.coms4156.knowledgedrop.retrieval;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch implementation of {@link KeywordIndex}: one document per chunk, searched with BM25
 * over the {@code text} field.
 *
 * <p>Invariant that makes "only chunks of ready documents" hold: ingestion indexes a document's
 * chunks as its last step (one bulk request, right before the status becomes READY) and calls
 * {@link #deleteByDocument} if ingestion fails. A document that is still processing, failed, or
 * mid-update therefore has no searchable chunks. Elasticsearch never sees the Postgres status.
 *
 * <p>Every query filters by {@code clientId}, so one client can never read another's chunks.
 */
@Component
public class ElasticsearchKeywordIndex implements KeywordIndex {

  /** Default index name. */
  static final String DEFAULT_INDEX = "kd-chunks";

  /** Elasticsearch's default max_result_window; one document never has more chunks than this. */
  static final int MAX_CHUNKS_PER_DOCUMENT = 10_000;

  private static final String CHUNK_ID = "chunkId";
  private static final String CLIENT_ID = "clientId";
  private static final String DOCUMENT_ID = "documentId";
  private static final String PAGE = "page";
  private static final String POSITION = "position";
  private static final String TEXT = "text";

  private final ElasticsearchClient client;
  private final String indexName;
  private volatile boolean indexReady;

  /**
   * Creates the index adapter using the default index name.
   *
   * @param client the Spring-configured Elasticsearch client
   */
  @Autowired
  public ElasticsearchKeywordIndex(ElasticsearchClient client) {
    this(client, DEFAULT_INDEX);
  }

  /**
   * Creates the index adapter with a specific index name (tests use a throwaway index).
   *
   * @param client the Elasticsearch client
   * @param indexName the index to read and write
   */
  ElasticsearchKeywordIndex(ElasticsearchClient client, String indexName) {
    this.client = client;
    this.indexName = indexName;
  }

  /** The JSON shape stored in Elasticsearch for one chunk. */
  public record ChunkDocument(
      String chunkId, String clientId, String documentId, int page, int position, String text) {}

  @Override
  public void index(UUID clientId, UUID documentId, List<RankedChunk> chunks) {
    if (chunks.isEmpty()) {
      return;
    }
    ensureIndex();
    List<BulkOperation> operations = new ArrayList<>(chunks.size());
    for (int position = 0; position < chunks.size(); position++) {
      RankedChunk chunk = chunks.get(position);
      ChunkDocument document =
          new ChunkDocument(
              chunk.chunkId(),
              clientId.toString(),
              documentId.toString(),
              chunk.page(),
              position,
              chunk.text());
      operations.add(
          BulkOperation.of(
              op -> op.index(i -> i.index(indexName).id(chunk.chunkId()).document(document))));
    }
    try {
      BulkResponse response = client.bulk(b -> b.operations(operations).refresh(Refresh.True));
      if (response.errors()) {
        throw new IllegalStateException("Elasticsearch bulk index failed: " + firstError(response));
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Could not index chunks", e);
    }
  }

  @Override
  public List<RankedChunk> search(UUID clientId, String queryText, int limit) {
    if (limit <= 0 || queryText == null || queryText.isBlank()) {
      return List.of();
    }
    ensureIndex();
    try {
      SearchResponse<ChunkDocument> response =
          client.search(
              s -> s.index(indexName).size(limit).query(keywordQuery(clientId, queryText)),
              ChunkDocument.class);
      return toRankedChunks(response.hits().hits());
    } catch (IOException e) {
      throw new UncheckedIOException("Keyword search failed", e);
    }
  }

  @Override
  public List<RankedChunk> chunksInOrder(UUID documentId) {
    ensureIndex();
    try {
      SearchResponse<ChunkDocument> response =
          client.search(
              s ->
                  s.index(indexName)
                      .size(MAX_CHUNKS_PER_DOCUMENT)
                      .query(q -> q.term(t -> t.field(DOCUMENT_ID).value(documentId.toString())))
                      .sort(so -> so.field(f -> f.field(POSITION).order(SortOrder.Asc))),
              ChunkDocument.class);
      return toRankedChunks(response.hits().hits());
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read chunks for document " + documentId, e);
    }
  }

  @Override
  public void deleteByDocument(UUID documentId) {
    deleteWhere(DOCUMENT_ID, documentId);
  }

  @Override
  public void deleteByClient(UUID clientId) {
    deleteWhere(CLIENT_ID, clientId);
  }

  @Override
  public boolean ping() {
    try {
      return client.ping().value();
    } catch (IOException | RuntimeException e) {
      return false;
    }
  }

  /**
   * Builds the BM25 query: the text must match, and the chunk must belong to the client. The
   * filter clause does not affect scoring.
   *
   * @param clientId the calling client
   * @param queryText the raw query text
   * @return the Elasticsearch query
   */
  static Query keywordQuery(UUID clientId, String queryText) {
    return Query.of(
        q ->
            q.bool(
                b ->
                    b.must(m -> m.match(mt -> mt.field(TEXT).query(queryText)))
                        .filter(f -> f.term(t -> t.field(CLIENT_ID).value(clientId.toString())))));
  }

  /**
   * Converts Elasticsearch hits to ranked chunks. Ranks start at 1 in hit order; a hit without a
   * score (for example a sorted query) gets 0.
   *
   * @param hits the hits, best first
   * @return the chunks, ranked from 1
   */
  static List<RankedChunk> toRankedChunks(List<Hit<ChunkDocument>> hits) {
    List<RankedChunk> ranked = new ArrayList<>(hits.size());
    for (Hit<ChunkDocument> hit : hits) {
      ChunkDocument source = hit.source();
      if (source == null) {
        continue;
      }
      double score = hit.score() == null ? 0.0 : hit.score();
      ranked.add(
          new RankedChunk(
              source.chunkId(),
              UUID.fromString(source.documentId()),
              source.page(),
              source.text(),
              ranked.size() + 1,
              score));
    }
    return ranked;
  }

  private void deleteWhere(String field, UUID value) {
    ensureIndex();
    try {
      client.deleteByQuery(
          d -> d.index(indexName).query(q -> q.term(t -> t.field(field).value(value.toString())))
              .refresh(true));
    } catch (IOException e) {
      throw new UncheckedIOException("Could not delete chunks", e);
    }
  }

  private void ensureIndex() {
    if (indexReady) {
      return;
    }
    try {
      boolean exists = client.indices().exists(e -> e.index(indexName)).value();
      if (!exists) {
        createIndex();
      }
      indexReady = true;
    } catch (IOException e) {
      throw new UncheckedIOException("Could not prepare Elasticsearch index " + indexName, e);
    }
  }

  private void createIndex() throws IOException {
    try {
      client
          .indices()
          .create(
              c ->
                  c.index(indexName)
                      .mappings(
                          m ->
                              m.properties(CHUNK_ID, p -> p.keyword(k -> k))
                                  .properties(CLIENT_ID, p -> p.keyword(k -> k))
                                  .properties(DOCUMENT_ID, p -> p.keyword(k -> k))
                                  .properties(PAGE, p -> p.integer(i -> i))
                                  .properties(POSITION, p -> p.integer(i -> i))
                                  .properties(TEXT, p -> p.text(t -> t))));
    } catch (ElasticsearchException e) {
      // Another request created the index between our exists check and create: that is fine.
      if (e.error() == null || !"resource_already_exists_exception".equals(e.error().type())) {
        throw e;
      }
    }
  }

  private static String firstError(BulkResponse response) {
    for (BulkResponseItem item : response.items()) {
      if (item.error() != null) {
        return item.error().reason();
      }
    }
    return "unknown error";
  }
}
