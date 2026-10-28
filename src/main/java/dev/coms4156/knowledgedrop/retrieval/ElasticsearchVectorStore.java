package dev.coms4156.knowledgedrop.retrieval;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import dev.coms4156.knowledgedrop.config.KnowledgeDropProperties;
import dev.coms4156.knowledgedrop.retrieval.ElasticsearchKeywordIndex.ChunkDocument;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch implementation of {@link VectorStore}: one document per chunk with a {@code
 * dense_vector} field, searched with kNN and cosine similarity.
 *
 * <p>The vector dimension comes from {@code knowledgedrop.embedding.dimension}, the same value the
 * embedding client reports. Changing the embedding model to one with a different dimension needs a
 * new index (delete {@code kd-vectors}) and re-embedding every chunk.
 *
 * <p>Readiness works like the keyword index: ingestion indexes a document's vectors as its last
 * step and calls {@link #deleteByDocument} if ingestion fails, so a document that is processing,
 * failed, or mid-update has no searchable vectors.
 *
 * <p>Every search filters by {@code clientId} inside the kNN query, so one client can never read
 * another's chunks.
 */
@Component
public class ElasticsearchVectorStore implements VectorStore {

  /** Default index name. */
  static final String DEFAULT_INDEX = "kd-vectors";

  /** Elasticsearch caps num_candidates at 10,000. */
  static final int MAX_NUM_CANDIDATES = 10_000;

  private static final int MIN_NUM_CANDIDATES = 100;
  private static final int CANDIDATES_PER_RESULT = 10;

  private static final String CHUNK_ID = "chunkId";
  private static final String CLIENT_ID = "clientId";
  private static final String DOCUMENT_ID = "documentId";
  private static final String PAGE = "page";
  private static final String POSITION = "position";
  private static final String TEXT = "text";
  private static final String EMBEDDING = "embedding";

  private final ElasticsearchClient client;
  private final String indexName;
  private final int dimensions;
  private volatile boolean indexReady;

  /**
   * Creates the store using the default index name and the configured embedding dimension.
   *
   * @param client the Spring-configured Elasticsearch client
   * @param properties application settings (embedding dimension)
   */
  @Autowired
  public ElasticsearchVectorStore(ElasticsearchClient client, KnowledgeDropProperties properties) {
    this(client, DEFAULT_INDEX, properties.embedding().dimension());
  }

  /**
   * Creates the store with a specific index name and dimension (tests use a throwaway index).
   *
   * @param client the Elasticsearch client
   * @param indexName the index to read and write
   * @param dimensions vector length every chunk embedding must have
   */
  ElasticsearchVectorStore(ElasticsearchClient client, String indexName, int dimensions) {
    this.client = client;
    this.indexName = indexName;
    this.dimensions = dimensions;
  }

  /** The JSON shape stored in Elasticsearch for one chunk and its embedding. */
  public record VectorDocument(
      String chunkId,
      String clientId,
      String documentId,
      int page,
      int position,
      String text,
      List<Float> embedding) {}

  @Override
  public void index(
      UUID clientId, UUID documentId, List<RankedChunk> chunks, List<float[]> vectors) {
    if (chunks.size() != vectors.size()) {
      throw new IllegalArgumentException(
          "Expected one vector per chunk but got " + chunks.size() + " chunks and "
              + vectors.size() + " vectors");
    }
    if (chunks.isEmpty()) {
      return;
    }
    for (float[] vector : vectors) {
      checkDimensions(vector);
    }
    ensureIndex();
    List<BulkOperation> operations = new ArrayList<>(chunks.size());
    for (int position = 0; position < chunks.size(); position++) {
      RankedChunk chunk = chunks.get(position);
      VectorDocument document =
          new VectorDocument(
              chunk.chunkId(),
              clientId.toString(),
              documentId.toString(),
              chunk.page(),
              position,
              chunk.text(),
              toList(vectors.get(position)));
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
      throw new UncheckedIOException("Could not index vectors", e);
    }
  }

  @Override
  public List<RankedChunk> search(UUID clientId, float[] queryVector, int limit) {
    if (limit <= 0) {
      return List.of();
    }
    checkDimensions(queryVector);
    ensureIndex();
    int numCandidates = numCandidates(limit);
    try {
      SearchResponse<ChunkDocument> response =
          client.search(
              s ->
                  s.index(indexName)
                      .size(limit)
                      .source(src -> src.filter(f -> f.excludes(EMBEDDING)))
                      .knn(
                          k ->
                              k.field(EMBEDDING)
                                  .queryVector(toList(queryVector))
                                  .k(limit)
                                  .numCandidates(numCandidates)
                                  .filter(clientFilter(clientId))),
              ChunkDocument.class);
      return toRankedChunks(response.hits().hits());
    } catch (IOException e) {
      throw new UncheckedIOException("Vector search failed", e);
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
   * The kNN pre-filter that restricts a search to one client's chunks.
   *
   * @param clientId the calling client
   * @return the filter query
   */
  static Query clientFilter(UUID clientId) {
    return Query.of(q -> q.term(t -> t.field(CLIENT_ID).value(clientId.toString())));
  }

  /**
   * How many nearest-neighbor candidates each shard should consider for a request of {@code limit}
   * results: ten times the limit, at least 100, at most Elasticsearch's maximum of 10,000.
   *
   * @param limit the number of results requested
   * @return the num_candidates value
   */
  static int numCandidates(int limit) {
    long wanted = (long) limit * CANDIDATES_PER_RESULT;
    return (int) Math.min(Math.max(wanted, MIN_NUM_CANDIDATES), MAX_NUM_CANDIDATES);
  }

  /**
   * Converts an Elasticsearch cosine score back to cosine similarity. For cosine similarity
   * Elasticsearch reports {@code (1 + cosine) / 2} so scores are never negative.
   *
   * @param elasticsearchScore the {@code _score} of a hit
   * @return the cosine similarity, between -1 and 1
   */
  static double toCosine(double elasticsearchScore) {
    return 2.0 * elasticsearchScore - 1.0;
  }

  /**
   * Converts kNN hits to ranked chunks: ranks start at 1 in hit order and scores are cosine
   * similarity.
   *
   * @param hits the hits, best first
   * @return the chunks, ranked from 1
   */
  static List<RankedChunk> toRankedChunks(List<Hit<ChunkDocument>> hits) {
    List<RankedChunk> converted = new ArrayList<>(hits.size());
    for (RankedChunk chunk : ElasticsearchKeywordIndex.toRankedChunks(hits)) {
      converted.add(
          new RankedChunk(
              chunk.chunkId(),
              chunk.documentId(),
              chunk.page(),
              chunk.text(),
              chunk.rank(),
              toCosine(chunk.score())));
    }
    return converted;
  }

  private void checkDimensions(float[] vector) {
    if (vector == null || vector.length != dimensions) {
      int actual = vector == null ? 0 : vector.length;
      throw new IllegalArgumentException(
          "Expected a vector with " + dimensions + " dimensions but got " + actual);
    }
  }

  private static List<Float> toList(float[] vector) {
    List<Float> values = new ArrayList<>(vector.length);
    for (float value : vector) {
      values.add(value);
    }
    return values;
  }

  private void deleteWhere(String field, UUID value) {
    ensureIndex();
    try {
      DeleteByQueryResponse response =
          client.deleteByQuery(
              d ->
                  d.index(indexName)
                      .query(q -> q.term(t -> t.field(field).value(value.toString())))
                      .refresh(true));
      ElasticsearchDeletes.requireComplete(response);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not delete vectors", e);
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
                                  .properties(TEXT, p -> p.text(t -> t.index(false)))
                                  .properties(
                                      EMBEDDING,
                                      p ->
                                          p.denseVector(
                                              d ->
                                                  d.dims(dimensions)
                                                      .index(true)
                                                      .similarity(DenseVectorSimilarity.Cosine)))));
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
