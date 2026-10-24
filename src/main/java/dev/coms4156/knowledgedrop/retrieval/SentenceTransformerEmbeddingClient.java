package dev.coms4156.knowledgedrop.retrieval;

import dev.coms4156.knowledgedrop.config.KnowledgeDropProperties;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@link EmbeddingClient} for the local sentence-transformers service at {@code
 * knowledgedrop.embedding.base-url}.
 *
 * <p>Currently returns dummy vectors so ingestion can be exercised end to end: every vector has
 * the configured length ({@code knowledgedrop.embedding.dimension}) and every value is {@link
 * #DUMMY_VALUE}.
 *
 * <p>TODO: embedding: call the service instead of returning dummy vectors.
 */
@Component
public class SentenceTransformerEmbeddingClient implements EmbeddingClient {

  /** The value every component of a dummy vector has. */
  static final float DUMMY_VALUE = 0.1f;

  private final int dimension;

  /**
   * Creates the client.
   *
   * @param properties supplies the vector length from {@code knowledgedrop.embedding.dimension}
   */
  public SentenceTransformerEmbeddingClient(KnowledgeDropProperties properties) {
    this.dimension = properties.embedding().dimension();
  }

  @Override
  public float[] embed(String text) {
    // TODO: embedding: POST the text to the service and return its vector.
    float[] vector = new float[dimension];
    Arrays.fill(vector, DUMMY_VALUE);
    return vector;
  }

  @Override
  public List<float[]> embedAll(List<String> texts) {
    // TODO: embedding: send texts to the service in batches instead of one embed call each.
    return texts.stream().map(this::embed).toList();
  }

  @Override
  public int dimensions() {
    return dimension;
  }
}
