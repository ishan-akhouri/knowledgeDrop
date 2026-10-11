package dev.coms4156.knowledgedrop.api.dto;

import java.util.List;

/**
 * Response of compare: the same query run through every retrieval mode.
 *
 * @param vector top K chunks from vector search (Redis)
 * @param keyword top K chunks from keyword search (Elasticsearch BM25)
 * @param hybrid top K chunks from both lists merged with reciprocal rank fusion
 * @param overlap which chunks appeared in multiple modes
 */
public record CompareResponse(
    List<ScoredChunk> vector,
    List<ScoredChunk> keyword,
    List<ScoredChunk> hybrid,
    List<OverlapEntry> overlap) {}
