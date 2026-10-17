package dev.coms4156.knowledgedrop.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * JSON body for client registration.
 *
 * @param firstName client first name
 * @param lastName client last name
 */
public record RegisterClientRequest(
    @NotBlank @Size(max = 255) String firstName,
    @NotBlank @Size(max = 255) String lastName) {}
