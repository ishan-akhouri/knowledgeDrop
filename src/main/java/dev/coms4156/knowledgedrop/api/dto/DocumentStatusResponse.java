package dev.coms4156.knowledgedrop.api.dto;

import dev.coms4156.knowledgedrop.model.DocumentStatus;
import java.util.UUID;

/**
 * Response of create and update.
 *
 * @param documentId ID of the document
 * @param status current status (processing, ready, or failed)
 */
public record DocumentStatusResponse(UUID documentId, DocumentStatus status) {}
