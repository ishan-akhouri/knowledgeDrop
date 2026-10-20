package dev.coms4156.knowledgedrop.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * JSON body for registering a client system.
 *
 * @param name name of the client system
 */
public record RegisterClientRequest(@NotBlank @Size(max = 255) String name) {}
