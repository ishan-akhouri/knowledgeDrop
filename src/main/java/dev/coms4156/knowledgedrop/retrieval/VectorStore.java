package dev.coms4156.knowledgedrop.retrieval;

import java.util.List;
import java.util.UUID;

/**
 * Stores chunk embeddings and answers nearest-neighbor queries (backed by the Elasticsearch
 * semantic vector store).
 *
 * <p>Every method is scoped to one client so one client can never see another's chunks.
 *
 * <p>Implemented by {@link ElasticsearchVectorStore}. Callers index a document's vectors only
 * once ingestion has succeeded, and delete them if it fails, so only chunks of ready documents
 * are ever searchable.
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
   * Removes everything a client owns (reserved for future permanent cleanup).
   *
   * @param clientId the client whose chunks should be removed
   */
  void deleteByClient(UUID clientId);

  /**
   * Checks that Elasticsearch responds (used by health).
   *
   * @return true if reachable
   */
  boolean ping();
}

