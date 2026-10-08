package dev.coms4156.knowledgedrop.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

/**
 * A question with the ground-truth sources that contain its answer.
 *
 * @param question the question text
 * @param documentIds IDs of documents containing the answer; may be empty if chunkIds is set
 * @param chunkIds IDs of chunks containing the answer; may be empty if documentIds is set
 */
public record LabeledQuestion(
    @NotBlank String question, List<UUID> documentIds, List<String> chunkIds) {}
