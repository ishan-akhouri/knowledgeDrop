package dev.coms4156.knowledgedrop.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /knowledgeDrop/compare}.
 *
 * @param query the query text
 * @param k number of results per mode; optional, defaults to 5
 */
public record CompareRequest(@NotBlank String query, @Min(1) Integer k) {}
