package dev.coms4156.knowledgedrop.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The JSON {@code metadata} part of {@code POST /knowledgeDrop/create}. The document content is
 * the binary {@code file} part of the same multipart request, or the optional {@code text} field
 * here for raw text. Provide exactly one of the two.
 *
 * @param name document name, unique per client
 * @param type "pdf", "text", or "image"
 * @param text raw text content, or null when a file part is sent
 */
public record CreateDocumentRequest(
    @NotBlank @Size(max = 255) String name, @NotBlank String type, String text) {}
