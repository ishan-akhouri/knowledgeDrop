package dev.coms4156.knowledgedrop.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Body of {@code POST /knowledgeDrop/eval}.
 *
 * @param questions the labeled question set
 * @param k cutoff for recall@K; optional, defaults to 5
 */
public record EvalRequest(@NotEmpty @Valid List<LabeledQuestion> questions, @Min(1) Integer k) {}
