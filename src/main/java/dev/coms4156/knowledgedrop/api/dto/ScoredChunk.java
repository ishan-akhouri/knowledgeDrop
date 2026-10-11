package dev.coms4156.knowledgedrop.api.dto;

import java.util.UUID;

/**
 * A chunk returned by one retrieval mode.
 *
 * <p>TODO(compare): fix the chunk ID format, for example {@code <documentId>:<chunkIndex>}.
 *
 * @param chunkId identifier of the chunk
 * @param documentId ID of the source document
 * @param filename name of the source document
 * @param page page number, or null if not applicable
 * @param snippet chunk text
 * @param score score assigned by the retrieval mode
 */
public record ScoredChunk(
    String chunkId, UUID documentId, String filename, Integer page, String snippet, double score) {}
