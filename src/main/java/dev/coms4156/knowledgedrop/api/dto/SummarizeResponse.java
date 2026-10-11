package dev.coms4156.knowledgedrop.api.dto;

import java.util.UUID;

/**
 * Response of summarize.
 *
 * @param summary the summary text
 * @param documentId ID of the summarized document
 * @param chunksUsed number of chunks that went into the summary
 */
public record SummarizeResponse(String summary, UUID documentId, int chunksUsed) {}
