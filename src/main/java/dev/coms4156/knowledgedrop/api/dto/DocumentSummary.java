package dev.coms4156.knowledgedrop.api.dto;

import dev.coms4156.knowledgedrop.model.DocumentStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * One entry in the list response.
 *
 * @param documentId ID of the document
 * @param filename document name
 * @param type document MIME type
 * @param uploadTime when the document was uploaded
 * @param status current status
 */
public record DocumentSummary(
    UUID documentId,
    String filename,
    String type,
    Instant uploadTime,
    DocumentStatus status) {}
