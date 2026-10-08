package dev.coms4156.knowledgedrop.api.dto;

import java.util.List;

/**
 * Response of eval.
 *
 * @param k the cutoff used
 * @param vector metrics for vector-only retrieval
 * @param keyword metrics for keyword-only retrieval
 * @param hybrid metrics for hybrid retrieval
 * @param perQuestion rank of the correct source for each question in each mode
 */
public record EvalResponse(
    int k,
    ModeMetrics vector,
    ModeMetrics keyword,
    ModeMetrics hybrid,
    List<QuestionResult> perQuestion) {}
