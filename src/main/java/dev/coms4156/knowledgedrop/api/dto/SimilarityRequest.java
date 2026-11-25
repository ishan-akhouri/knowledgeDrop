package dev.coms4156.knowledgedrop.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body of {@code POST /knowledgeDrop/similarity}.
 *
 * @param text the document content to compare against stored documents
 * @param k number of results; optional, defaults to 5
 * @param mode vector, keyword, or hybrid; optional, defaults to vector
 */
public record SimilarityRequest(
    @NotBlank String text,
    @Min(1) Integer k,
    @Pattern(regexp = "vector|keyword|hybrid",
        message = "must be one of: vector, keyword, hybrid") String mode) {

  /** Creates a request using the default vector mode. */
  public SimilarityRequest(String text, Integer k) {
    this(text, k, null);
  }
}
