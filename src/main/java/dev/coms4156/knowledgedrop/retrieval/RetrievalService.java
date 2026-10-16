package dev.coms4156.knowledgedrop.retrieval;

import java.util.List;
import java.util.UUID;

/**
 * The shared retrieval component: vector search, keyword search, and hybrid (RRF).
 *
 * <p>Ask, similarity, compare and eval all go through this class so they share one
 * retrieval path.
 *
 * <p>TODO: add {@code @Service} once VectorStore, KeywordIndex, EmbeddingClient and an
 * RrfMerger bean exist. Until then Spring must not try to build this class.
 */
@SuppressWarnings("PMD.UnusedPrivateField")
public class RetrievalService {

  private final EmbeddingClient embeddings;
  private final VectorStore vectorStore;
  private final KeywordIndex keywordIndex;
  private final RrfMerger rrf;

  /**
   * Creates the service.
   *
   * <p>TODO: retrieval: build {@code RrfMerger} from knowledgedrop.retrieval.rrf-k (a @Bean in
   * config, or construct it here from KnowledgeDropProperties).
   *
   * @param embeddings turns query text into a vector
   * @param vectorStore Elasticsearch kNN vector search
   * @param keywordIndex Elasticsearch BM25 search
   * @param rrf merges the two ranked lists
   */
  public RetrievalService(
      EmbeddingClient embeddings,
      VectorStore vectorStore,
      KeywordIndex keywordIndex,
      RrfMerger rrf) {
    this.embeddings = embeddings;
    this.vectorStore = vectorStore;
    this.keywordIndex = keywordIndex;
    this.rrf = rrf;
  }

  /**
   * Vector-only search.
   *
   * @param clientId the calling client (all results are scoped to it)
   * @param query the query text
   * @param k how many chunks to return
   * @return top k chunks by embedding similarity
   */
  public List<RankedChunk> vector(UUID clientId, String query, int k) {
    // TODO: retrieval: embed the query, call vectorStore.search with the candidate pool, cut to k.
    throw new UnsupportedOperationException("vector search");
  }

  /**
   * Keyword-only (BM25) search.
   *
   * @param clientId the calling client (all results are scoped to it)
   * @param query the query text
   * @param k how many chunks to return
   * @return top k chunks by BM25 score
   */
  public List<RankedChunk> keyword(UUID clientId, String query, int k) {
    return keywordIndex.search(clientId, query, k);
  }

  /**
   * Hybrid search: both lists merged with reciprocal rank fusion.
   *
   * @param clientId the calling client (all results are scoped to it)
   * @param query the query text
   * @param k how many chunks to return
   * @return top k chunks by RRF score
   */
  public List<RankedChunk> hybrid(UUID clientId, String query, int k) {
    // TODO: retrieval: fetch the vector and keyword candidate lists (candidate-pool each), pass
    //   both to rrf.merge(..., k).
    throw new UnsupportedOperationException("hybrid search");
  }

  // TODO: similarity: a method that runs vector search on the input text and groups chunk scores
  //   by document (best chunk per document), for POST similarity.
  // TODO: compare: a method returning all three modes plus the overlap summary input, for POST
  //   compare. Keep rank and score per chunk per mode.
}

