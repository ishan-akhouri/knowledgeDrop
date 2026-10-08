package dev.coms4156.knowledgedrop.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /knowledgeDrop/ask}.
 *
 * @param question the question to answer from the client's documents
 * @param k number of chunks used as context; optional, defaults to 5
 */
public record AskRequest(@NotBlank String question, @Min(1) Integer k) {}
