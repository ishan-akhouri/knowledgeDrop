package dev.coms4156.knowledgedrop.api.dto;

/**
 * Simple confirmation message, used by delete and client deletion.
 *
 * @param message what happened
 */
public record MessageResponse(String message) {}
