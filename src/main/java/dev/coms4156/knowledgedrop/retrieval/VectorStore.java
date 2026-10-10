package dev.coms4156.knowledgedrop.retrieval;

import java.util.List;
import java.util.UUID;

/**
 * Stores chunk embeddings and answers nearest-neighbor queries (backed by Redis Stack).
 *
 * <p>Every method is scoped to one client so one client can never see another's chunks.
 *
 * <p>TODO(vector-store): write {@code RedisVectorStore implements VectorStore}. Needs the
 * RediSearch module (Redis Stack, see docker-compose.yml): create an index with a VECTOR field
 * whose DIM equals {@link EmbeddingClient#dimensions()}, plus TAG fields for clientId and
 * documentId so queries can filter.
 */
public interface VectorStore {

  /**
   * Inserts the embeddings for a document's chunks.
   *
   * @param clientId the owning client
   * @param documentId the document the chunks belong to
   * @param chunks the chunks, in order
   * @param vectors one embedding per chunk, same order
   */
  void index(UUID clientId, UUID documentId, List<RankedChunk> chunks, List<float[]> vectors);

  /**
   * Finds the nearest chunks to a query vector among this client's ready documents.
   *
   * @param clientId the calling client
   * @param queryVector the embedded query
   * @param limit maximum results (use knowledgedrop.retrieval.candidate-pool)
   * @return chunks ordered best first, ranks starting at 1
   */
  List<RankedChunk> search(UUID clientId, float[] queryVector, int limit);

  /**
   * Removes all chunks of one document (used by update and delete).
   *
   * @param documentId the document whose chunks should be removed
   */
  void deleteByDocument(UUID documentId);

  /**
   * Removes everything a client owns (used by clientDrop).
   *
   * @param clientId the client whose chunks should be removed
   */
  void deleteByClient(UUID clientId);

  /**
   * Checks that Redis responds (used by health).
   *
   * @return true if reachable
   */
  boolean ping();
}

