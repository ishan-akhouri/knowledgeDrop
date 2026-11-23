package dev.coms4156.knowledgedrop.retrieval;

import dev.coms4156.knowledgedrop.config.KnowledgeDropProperties;
import dev.coms4156.knowledgedrop.model.DocumentStatus;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * The shared retrieval component: vector search, keyword search, and hybrid (RRF).
 *
 * <p>Shared retrieval path for ask, similarity, compare and eval. Document eligibility is checked
 * once per nonempty candidate pool, before truncation or fusion. A finite pool can return fewer
 * than k results when too few eligible candidates occur within that pool.
 */
@Service
public class RetrievalService {

  private final EmbeddingClient embeddings;
  private final VectorStore vectorStore;
  private final KeywordIndex keywordIndex;
  private final RrfMerger rrf;
  private final DocumentRepository documents;
  private final int candidatePool;

  /**
   * Creates the service.
   *
   * @param embeddings turns query text into a vector
   * @param vectorStore Elasticsearch kNN vector search
   * @param keywordIndex Elasticsearch BM25 search
   * @param documents authoritative document status records
   * @param properties candidate pool and rank fusion settings
   */
  public RetrievalService(
      EmbeddingClient embeddings,
      VectorStore vectorStore,
      KeywordIndex keywordIndex,
      DocumentRepository documents,
      KnowledgeDropProperties properties) {
    this.embeddings = embeddings;
    this.vectorStore = vectorStore;
    this.keywordIndex = keywordIndex;
    this.documents = documents;
    this.rrf = new RrfMerger(properties.retrieval().rrfK());
    this.candidatePool = properties.retrieval().candidatePool();
    if (candidatePool <= 0) {
      throw new IllegalArgumentException("candidatePool must be positive");
    }
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
   * @param clientId the calling client (all results are scoped to it)
   * @param query the query text
   * @param k how many chunks to return
   * @return top k chunks by RRF score
   */
  public List<RankedChunk> hybrid(UUID clientId, String query, int k) {
    if (k <= 0 || query == null || query.isBlank()) {
      return List.of();
    }
    int limit = Math.max(k, candidatePool);
    List<RankedChunk> vectors = vectorStore.search(clientId, embeddings.embed(query), limit);
    List<RankedChunk> keywords = keywordIndex.search(clientId, query, limit);
    Set<UUID> readyIds = readyDocumentIds(clientId, List.of(vectors, keywords));
    return rrf.merge(List.of(readyChunks(vectors, readyIds), readyChunks(keywords, readyIds)), k);
  }

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
