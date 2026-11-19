package dev.coms4156.knowledgedrop.retrieval;

/**
 * Thrown when the embedding service cannot be reached, answers with an error, or returns vectors
 * that do not match what the application expects.
 */
public class EmbeddingException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception with a message.
   *
   * @param message what went wrong
   */
  public EmbeddingException(String message) {
    super(message);
  }

  /**
   * Creates the exception with a message and the underlying cause.
   *
   * @param message what went wrong
   * @param cause the original failure
   */
  public EmbeddingException(String message, Throwable cause) {
    super(message, cause);
  }
}
