package dev.coms4156.knowledgedrop.retrieval;

import dev.coms4156.knowledgedrop.config.KnowledgeDropProperties;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The shared retrieval component: vector search, keyword search, and hybrid (RRF).
 *
 * <p>Ask, similarity, compare and eval all go through this class so they share one
 * retrieval path.
 */
@Service
public class RetrievalService {

  private final EmbeddingClient embeddings;
  private final VectorStore vectorStore;
  private final KeywordIndex keywordIndex;
  private final RrfMerger rrf;
  private final int candidatePool;

  /**
   * Creates the service from the application settings.
   *
   * <p>The RRF constant comes from knowledgedrop.retrieval.rrf-k and the candidate pool from
   * knowledgedrop.retrieval.candidate-pool.
   *
   * @param embeddings turns query text into a vector
   * @param vectorStore Elasticsearch kNN vector search
   * @param keywordIndex Elasticsearch BM25 search
   * @param properties application settings
   */
  @Autowired
  public RetrievalService(
      EmbeddingClient embeddings,
      VectorStore vectorStore,
      KeywordIndex keywordIndex,
      KnowledgeDropProperties properties) {
    this(
        embeddings,
        vectorStore,
        keywordIndex,
        new RrfMerger(properties.retrieval().rrfK()),
        properties.retrieval().candidatePool());
  }

  /**
   * Creates the service with explicit collaborators (used by tests).
   *
   * @param embeddings turns query text into a vector
   * @param vectorStore Elasticsearch kNN vector search
   * @param keywordIndex Elasticsearch BM25 search
   * @param rrf merges the two ranked lists
   * @param candidatePool how many results each mode fetches before fusion
   * @throws IllegalArgumentException if {@code candidatePool} is not positive
   */
  RetrievalService(
      EmbeddingClient embeddings,
      VectorStore vectorStore,
      KeywordIndex keywordIndex,
      RrfMerger rrf,
      int candidatePool) {
    if (candidatePool <= 0) {
      throw new IllegalArgumentException("candidatePool must be positive, was " + candidatePool);
    }
    this.embeddings = embeddings;
    this.vectorStore = vectorStore;
    this.keywordIndex = keywordIndex;
    this.rrf = rrf;
    this.candidatePool = candidatePool;
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
    if (k <= 0 || query == null || query.isBlank()) {
      return List.of();
    }
    // Vector-only mode needs just the top k, so ask for k directly. Hybrid mode uses the
    // larger candidate pool.
    return vectorStore.search(clientId, embeddings.embed(query), k);
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
   * <p>Each mode is asked for the candidate pool (never fewer than k) so a chunk that ranks low
   * in one mode can still win by ranking well in the other.
   *
   * @param clientId the calling client (all results are scoped to it)
   * @param query the query text
   * @param k how many chunks to return
   * @return top k chunks by RRF score
   */
  public List<RankedChunk> hybrid(UUID clientId, String query, int k) {
    if (k <= 0 || query == null || query.isBlank()) {
      return List.of();
    }
    int pool = Math.max(candidatePool, k);
    List<RankedChunk> vectorHits = vectorStore.search(clientId, embeddings.embed(query), pool);
    List<RankedChunk> keywordHits = keywordIndex.search(clientId, query, pool);
    return rrf.merge(List.of(vectorHits, keywordHits), k);
  }

  // TODO: similarity: a method that runs vector search on the input text and groups chunk scores
  //   by document (best chunk per document), for POST similarity.
  // TODO: compare: a method returning all three modes plus the overlap summary input, for POST
  //   compare. Keep rank and score per chunk per mode.
}
