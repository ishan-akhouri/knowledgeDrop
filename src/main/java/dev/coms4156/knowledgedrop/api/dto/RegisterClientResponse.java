package dev.coms4156.knowledgedrop.api.dto;

import java.util.UUID;

/**
 * Response of registerClient. The token is shown only once and is sent with every later request.
 *
 * @param clientId ID of the new client
 * @param clientToken secret token for the {@code Authorization: Bearer} header
 */
public record RegisterClientResponse(UUID clientId, String clientToken) {}
