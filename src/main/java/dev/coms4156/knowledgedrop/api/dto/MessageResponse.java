package dev.coms4156.knowledgedrop.api.dto;

/**
 * Simple confirmation message, used by delete and clientDrop.
 *
 * @param message what happened
 */
public record MessageResponse(String message) {}
