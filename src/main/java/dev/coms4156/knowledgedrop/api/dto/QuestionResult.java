package dev.coms4156.knowledgedrop.api.dto;

/**
 * Per-question breakdown for eval. A rank is 1-based; null means the correct source was not found.
 *
 * @param question the question text
 * @param vectorRank rank of the correct source in vector search
 * @param keywordRank rank of the correct source in keyword search
 * @param hybridRank rank of the correct source in hybrid search
 */
public record QuestionResult(
    String question, Integer vectorRank, Integer keywordRank, Integer hybridRank) {}
