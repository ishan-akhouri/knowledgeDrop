package dev.coms4156.knowledgedrop.retrieval;

import java.util.List;

/**
 * Turns text into embedding vectors.
 *
 * <p>DEFERRED: the embedding provider has not been chosen. Write the interface first, then one
 * implementation (for example {@code OpenAiEmbeddingClient}) once the team decides. Tests use a
 * Mockito mock or a tiny fake, so no network is needed.
 */
public interface EmbeddingClient {

  /**
   * Embeds one piece of text.
   *
   * @param text the text to embed
   * @return the embedding vector
   */
  float[] embed(String text);

  /**
   * Embeds many pieces of text in one call (used by ingestion, one embedding per chunk).
   *
   * <p>TODO(embedding): batch the provider request instead of calling {@link #embed} in a loop.
   *
   * @param texts the texts to embed
   * @return one vector per input, in the same order
   */
  List<float[]> embedAll(List<String> texts);

  /**
   * The vector length this provider returns. The Redis index must be created with the same value.
   *
   * @return the number of dimensions
   */
  int dimensions();
}

