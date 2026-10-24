package dev.coms4156.knowledgedrop.retrieval;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch {@link VectorStore}.
 *
 * <p>Currently returns dummy results so ingestion can be exercised end to end: writes and deletes
 * do nothing, search finds nothing, and ping always succeeds.
 *
 * <p>TODO: vector-store: store chunks in an index with a {@code dense_vector} field whose {@code
 * dims} equals {@link EmbeddingClient#dimensions()}, and search with kNN filtered by clientId.
 */
@Component
public class ElasticsearchVectorStore implements VectorStore {

  @Override
  public void index(
      UUID clientId, UUID documentId, List<RankedChunk> chunks, List<float[]> vectors) {
    // TODO: vector-store: bulk-index one document per chunk (chunkId, clientId, documentId, page,
    //   position, text, vector).
  }

  @Override
  public List<RankedChunk> search(UUID clientId, float[] queryVector, int limit) {
    // TODO: vector-store: kNN query on the vector field, filtered by clientId, top `limit` hits.
    return List.of();
  }

  @Override
  public void deleteByDocument(UUID documentId) {
    // TODO: vector-store: delete-by-query on documentId.
  }

  @Override
  public void deleteByClient(UUID clientId) {
    // TODO: vector-store: delete-by-query on clientId.
  }

  @Override
  public boolean ping() {
    // TODO: vector-store: return client.ping().value(), false on any exception.
    return true;
  }
}
