package dev.coms4156.knowledgedrop.api.dto;

import dev.coms4156.knowledgedrop.model.DocumentStatus;
import dev.coms4156.knowledgedrop.model.DocumentType;
import java.time.Instant;
import java.util.UUID;

/**
 * One entry in the list response.
 *
 * @param documentId ID of the document
 * @param filename document name
 * @param type document type
 * @param uploadTime when the document was uploaded
 * @param status current status
 */
public record DocumentSummary(
    UUID documentId,
    String filename,
    DocumentType type,
    Instant uploadTime,
    DocumentStatus status) {}
