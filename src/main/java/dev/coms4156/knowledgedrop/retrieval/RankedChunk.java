package dev.coms4156.knowledgedrop.retrieval;

import java.util.UUID;

/**
 * One chunk returned by a retrieval mode, with its rank and score in that mode.
 *
 * <p>TODO(retrieval): align the fields with {@code api.dto.ScoredChunk} so the services can map
 * between them without losing information (document ID, filename, page, snippet, score).
 *
 * @param chunkId stable ID of the chunk (the same ID in Redis and Elasticsearch)
 * @param documentId the document the chunk belongs to
 * @param page page number, or 0 when the source has no pages
 * @param text the chunk text, used for snippets and as LLM context
 * @param rank 1-based rank within the list it came from
 * @param score the score in the list it came from (cosine similarity, BM25, or RRF)
 */
public record RankedChunk(
    String chunkId, UUID documentId, int page, String text, int rank, double score) {}

