package dev.coms4156.knowledgedrop.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * JSON body for deleting the authenticated client system.
 *
 * @param clientId ID of the client to delete; must belong to the bearer token
 */
public record DeleteClientRequest(@NotNull UUID clientId) {}
