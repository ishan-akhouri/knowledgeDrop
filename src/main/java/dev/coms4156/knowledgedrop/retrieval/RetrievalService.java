package dev.coms4156.knowledgedrop.retrieval;

import dev.coms4156.knowledgedrop.api.dto.SimilarDocument;
import dev.coms4156.knowledgedrop.config.KnowledgeDropProperties;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.model.DocumentStatus;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The shared retrieval component: vector search, keyword search, and hybrid (RRF).
 *
 * <p>Ask, similarity, compare and eval all go through this class so they share one
 * retrieval path.
 *
 * <p>Document eligibility is checked once per nonempty candidate pool, before truncation or
 * fusion. Similarity rechecks eligibility when loading filenames for its response.
 * A finite pool can return fewer than k results when too few eligible candidates occur
 * within that pool.
 */
@Service
public class RetrievalService {

  private final EmbeddingClient embeddings;
  private final VectorStore vectorStore;
  private final KeywordIndex keywordIndex;
  private final DocumentRepository documents;
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
   * @param documents authoritative document status records
   * @param properties application settings
   */
  @Autowired
  public RetrievalService(
      EmbeddingClient embeddings,
      VectorStore vectorStore,
      KeywordIndex keywordIndex,
      DocumentRepository documents,
      KnowledgeDropProperties properties) {
    this(
        embeddings,
        vectorStore,
        keywordIndex,
        documents,
        new RrfMerger(properties.retrieval().rrfK()),
        properties.retrieval().candidatePool());
  }

  /**
   * Creates the service with explicit collaborators (used by tests).
   *
   * @param embeddings turns query text into a vector
   * @param vectorStore Elasticsearch kNN vector search
   * @param keywordIndex Elasticsearch BM25 search
   * @param documents authoritative document status records
   * @param rrf merges the two ranked lists
   * @param candidatePool how many results each mode fetches before status filtering and fusion
   * @throws IllegalArgumentException if {@code candidatePool} is not positive
   */
  RetrievalService(
      EmbeddingClient embeddings,
      VectorStore vectorStore,
      KeywordIndex keywordIndex,
      DocumentRepository documents,
      RrfMerger rrf,
      int candidatePool) {
    if (candidatePool <= 0) {
      throw new IllegalArgumentException("candidatePool must be positive, was " + candidatePool);
    }
    this.embeddings = embeddings;
    this.vectorStore = vectorStore;
    this.keywordIndex = keywordIndex;
    this.documents = documents;
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
    List<RankedChunk> candidates =
        vectorStore.search(clientId, embeddings.embed(query), Math.max(k, candidatePool));
    return readyChunks(candidates, readyDocumentIds(clientId, List.of(candidates))).stream()
        .limit(k).toList();
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
    if (k <= 0 || query == null || query.isBlank()) {
      return List.of();
    }
    List<RankedChunk> candidates = keywordIndex.search(clientId, query, Math.max(k, candidatePool));
    return readyChunks(candidates, readyDocumentIds(clientId, List.of(candidates))).stream()
        .limit(k).toList();
  }

  /**
   * Hybrid search: both lists merged with reciprocal rank fusion.
   *
   * <p>Each mode is asked for the candidate pool (never fewer than k) so a chunk that ranks low
   * in one mode can still win by ranking well in the other. Unready documents are dropped from
   * both lists before fusion.
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
    return hybridCandidates(clientId, query, Math.max(candidatePool, k), k);
  }

  // Similarity needs the whole fused union before grouping; chunk endpoints keep their limit.
  private List<RankedChunk> hybridCandidates(UUID clientId, String query, int pool, int limit) {
    List<RankedChunk> vectorHits = vectorStore.search(clientId, embeddings.embed(query), pool);
    List<RankedChunk> keywordHits = keywordIndex.search(clientId, query, pool);
    Set<UUID> readyIds = readyDocumentIds(clientId, List.of(vectorHits, keywordHits));
    return rrf.merge(
        List.of(readyChunks(vectorHits, readyIds), readyChunks(keywordHits, readyIds)), limit);
  }

  /**
   * Ranks distinct ready documents using their best vector-search chunk.
   *
   * <p>Grouping happens over the full candidate pool before applying the document limit.
   * Like the other retrieval modes, this can return fewer than k eligible results.
   *
   * @param clientId the authenticated owner
   * @param text text to embed
   * @param k maximum number of documents
   * @return documents ordered by descending cosine score, with stable ID tie-breaking
   */
  public List<SimilarDocument> similarity(UUID clientId, String text, int k) {
    return similarity(clientId, text, k, "vector");
  }

  /**
   * Ranks documents by their best chunk using the selected shared retrieval method.
   *
   * @param clientId the authenticated owner
   * @param text query text
   * @param k maximum number of documents
   * @param mode vector, keyword, or hybrid
   * @return documents ranked by cosine, BM25, or RRF score respectively
   */
  public List<SimilarDocument> similarity(UUID clientId, String text, int k, String mode) {
    if (k <= 0 || text == null || text.isBlank()) {
      return List.of();
    }
    int pool = Math.max(k, candidatePool);
    List<RankedChunk> candidates = switch (mode) {
      case "vector" -> vector(clientId, text, pool);
      case "keyword" -> keyword(clientId, text, pool);
      case "hybrid" -> hybridCandidates(clientId, text, pool, Integer.MAX_VALUE);
      default -> throw new IllegalArgumentException("Unknown retrieval mode: " + mode);
    };
    Set<UUID> ids = candidates.stream().map(RankedChunk::documentId).collect(Collectors.toSet());
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<UUID, DocumentRecord> eligible = documents.findAllByOwnerIdAndIdIn(clientId, ids).stream()
        .filter(document -> document.getStatus() == DocumentStatus.READY
            && document.getDeletedAt() == null)
        .collect(Collectors.toMap(DocumentRecord::getId, document -> document));
    Map<UUID, RankedChunk> best = new HashMap<>();
    Comparator<RankedChunk> order = Comparator.comparingDouble(RankedChunk::score).reversed()
        .thenComparing(RankedChunk::chunkId);
    for (RankedChunk chunk : candidates) {
      if (eligible.containsKey(chunk.documentId())) {
        best.merge(chunk.documentId(), chunk,
            (left, right) -> order.compare(left, right) <= 0 ? left : right);
      }
    }
    return best.values().stream()
        .sorted(Comparator.comparingDouble(RankedChunk::score).reversed()
            .thenComparing(chunk -> chunk.documentId().toString()))
        .limit(k)
        .map(chunk -> new SimilarDocument(chunk.documentId(),
            eligible.get(chunk.documentId()).getName(), chunk.score(), chunk.text()))
        .toList();
  }

  // TODO: compare: a method returning all three modes plus the overlap summary input, for POST
  //   compare. Keep rank and score per chunk per mode.

  // One database snapshot for the union of candidates, including both hybrid lists.
  private Set<UUID> readyDocumentIds(UUID clientId, List<List<RankedChunk>> lists) {
    Set<UUID> ids = lists.stream().flatMap(List::stream)
        .map(RankedChunk::documentId).collect(Collectors.toSet());
    if (ids.isEmpty()) {
      return Set.of();
    }
    return documents.findAllByOwnerIdAndIdIn(clientId, ids).stream()
        .filter(document -> document.getStatus() == DocumentStatus.READY
            && document.getDeletedAt() == null)
        .map(document -> document.getId()).collect(Collectors.toSet());
  }

  private List<RankedChunk> readyChunks(List<RankedChunk> candidates, Set<UUID> readyIds) {
    List<RankedChunk> result = new ArrayList<>();
    for (RankedChunk chunk : candidates) {
      if (readyIds.contains(chunk.documentId())) {
        result.add(new RankedChunk(chunk.chunkId(), chunk.documentId(), chunk.page(), chunk.text(),
            result.size() + 1, chunk.score()));
      }
    }
    return List.copyOf(result);
  }
}
