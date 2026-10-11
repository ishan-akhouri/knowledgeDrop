package dev.coms4156.knowledgedrop.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /knowledgeDrop/similarity}.
 *
 * @param text the document content to compare against stored documents
 * @param k number of results; optional, defaults to 5
 */
public record SimilarityRequest(@NotBlank String text, @Min(1) Integer k) {}
