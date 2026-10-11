package dev.coms4156.knowledgedrop.api.dto;

/**
 * Retrieval quality for one mode.
 *
 * @param recallAtK fraction of questions where a correct source appears in the top K
 * @param mrr mean reciprocal rank of the first correct source
 */
public record ModeMetrics(double recallAtK, double mrr) {}
