package dev.coms4156.knowledgedrop.api.dto;

import java.util.UUID;

/**
 * One ranked document in a similarity response.
 *
 * @param documentId ID of the stored document
 * @param filename name of the stored document
 * @param score similarity score
 * @param snippet the best-matching snippet
 */
public record SimilarDocument(UUID documentId, String filename, double score, String snippet) {}
