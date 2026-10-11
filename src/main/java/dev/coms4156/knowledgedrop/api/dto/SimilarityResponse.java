package dev.coms4156.knowledgedrop.api.dto;

import java.util.List;

/**
 * Response of similarity.
 *
 * @param results the top K stored documents, most similar first
 */
public record SimilarityResponse(List<SimilarDocument> results) {}
