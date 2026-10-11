package dev.coms4156.knowledgedrop.api.dto;

import java.util.List;

/**
 * Response of ask.
 *
 * @param answer the answer text, or a statement that the answer was not found in the documents
 * @param citations the source chunks used, empty if nothing relevant was found
 */
public record AskResponse(String answer, List<Citation> citations) {}
