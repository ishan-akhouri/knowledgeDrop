package dev.coms4156.knowledgedrop.retrieval;

import java.util.List;
import java.util.UUID;

/**
 * Stores chunk text and answers BM25 keyword queries (backed by Elasticsearch).
 *
 * <p>Every method is scoped to one client. Elasticsearch is also the source of chunk text in
 * document order, which summarize reads.
 *
 * <p>TODO(keyword-index): write {@code ElasticsearchKeywordIndex implements KeywordIndex}.
 * Index fields: chunkId, clientId (keyword), documentId (keyword), page, position (integer, for
 * ordering), text (analyzed text). Always filter by clientId in the query.
 */
public interface KeywordIndex {

  /**
   * Indexes a document's chunks.
   *
   * @param clientId the owning client
   * @param documentId the document the chunks belong to
   * @param chunks the chunks, in order (position = list index)
   */
  void index(UUID clientId, UUID documentId, List<RankedChunk> chunks);

  /**
   * BM25 search over this client's chunks.
   *
   * @param clientId the calling client
   * @param queryText the raw query text
   * @param limit maximum results (use knowledgedrop.retrieval.candidate-pool)
   * @return chunks ordered best first, ranks starting at 1
   */
  List<RankedChunk> search(UUID clientId, String queryText, int limit);

  /**
   * Reads every chunk of a document in order (used by summarize).
   *
   * @param documentId the document
   * @return the chunks sorted by position
   */
  List<RankedChunk> chunksInOrder(UUID documentId);

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
   * Checks that Elasticsearch responds (used by health).
   *
   * @return true if reachable
   */
  boolean ping();
}

