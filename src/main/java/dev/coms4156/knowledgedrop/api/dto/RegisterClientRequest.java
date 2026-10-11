package dev.coms4156.knowledgedrop.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /knowledgeDrop/registerClient}.
 *
 * @param clientName unique name for the client program
 */
public record RegisterClientRequest(@NotBlank @Size(max = 100) String clientName) {}
