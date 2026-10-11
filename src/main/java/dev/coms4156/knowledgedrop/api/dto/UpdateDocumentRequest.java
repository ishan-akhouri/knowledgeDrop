package dev.coms4156.knowledgedrop.api.dto;

import java.util.UUID;

/**
 * The JSON {@code metadata} part of {@code PUT /knowledgeDrop/update}. Identify the document by
 * exactly one of {@code documentId} or {@code documentName}. The new content is the binary
 * {@code file} part of the same multipart request, or the optional {@code text} field here.
 *
 * @param documentId ID of the document to update, or null if documentName is used
 * @param documentName name of the document to update, or null if documentId is used
 * @param text new raw text content, or null when a file part is sent
 */
public record UpdateDocumentRequest(UUID documentId, String documentName, String text) {}
