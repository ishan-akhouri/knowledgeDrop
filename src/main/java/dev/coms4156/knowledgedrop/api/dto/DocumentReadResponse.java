package dev.coms4156.knowledgedrop.api.dto;

import dev.coms4156.knowledgedrop.model.DocumentStatus;
import dev.coms4156.knowledgedrop.model.DocumentType;
import java.time.Instant;
import java.util.UUID;

/**
 * Response of read: the stored content plus metadata.
 *
 * <p>TODO(read): decide how binary content (pdf, image) is returned in JSON, for example base64
 * with an added {@code contentEncoding} field.
 *
 * @param content the document content
 * @param documentId ID of the document
 * @param filename document name
 * @param type document type
 * @param uploadTime when the document was uploaded
 * @param status current status
 */
public record DocumentReadResponse(
    String content,
    UUID documentId,
    String filename,
    DocumentType type,
    Instant uploadTime,
    DocumentStatus status) {}
