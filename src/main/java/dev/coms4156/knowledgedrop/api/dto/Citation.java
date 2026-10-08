package dev.coms4156.knowledgedrop.api.dto;

import java.util.UUID;

/**
 * A source chunk used to produce an answer.
 *
 * @param documentId ID of the source document
 * @param filename name of the source document
 * @param page page number in the source document, or null if not applicable
 * @param snippet the supporting text
 */
public record Citation(UUID documentId, String filename, Integer page, String snippet) {}
