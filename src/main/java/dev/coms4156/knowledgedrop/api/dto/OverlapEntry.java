package dev.coms4156.knowledgedrop.api.dto;

import java.util.List;

/**
 * A chunk that appeared in more than one retrieval mode.
 *
 * @param chunkId identifier of the chunk
 * @param modes the modes that returned it: any of "vector", "keyword", "hybrid"
 */
public record OverlapEntry(String chunkId, List<String> modes) {}
