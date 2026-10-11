package dev.coms4156.knowledgedrop.api.dto;

import dev.coms4156.knowledgedrop.model.SummaryLength;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Body of {@code POST /knowledgeDrop/summarize}.
 *
 * @param documentId the document to summarize
 * @param length "short" or "detailed"; optional
 */
public record SummarizeRequest(@NotNull UUID documentId, SummaryLength length) {}
