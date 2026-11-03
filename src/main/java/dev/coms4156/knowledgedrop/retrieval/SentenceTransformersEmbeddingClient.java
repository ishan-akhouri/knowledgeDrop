package dev.coms4156.knowledgedrop.retrieval;

import dev.coms4156.knowledgedrop.config.KnowledgeDropProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link EmbeddingClient} backed by the local sentence-transformers service in {@code
 * embedding-service/} (started by {@code docker compose up -d}).
 *
 * <p>{@link #embedAll} splits large inputs into batches of {@value #BATCH_SIZE} texts, so one
 * document's chunks cost a handful of HTTP calls instead of one per chunk. Every returned vector is
 * checked against the configured dimension, so a service running a different model fails loudly
 * here instead of corrupting the vector index.
 */
@Component
public class SentenceTransformersEmbeddingClient implements EmbeddingClient {

  /** Texts sent per request. The service accepts up to 256. */
  static final int BATCH_SIZE = 64;

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

  private final RestClient http;
  private final String baseUrl;
  private final int dimensions;

  /**
   * Creates the client from the application settings.
   *
   * @param properties application settings (embedding base URL and dimension)
   */
  @Autowired
  public SentenceTransformersEmbeddingClient(KnowledgeDropProperties properties) {
    this(
        buildRestClient(properties.embedding().baseUrl()),
        properties.embedding().baseUrl(),
        properties.embedding().dimension());
  }

  /**
   * Creates the client with a specific HTTP client (unit tests pass one backed by a mock server).
   *
   * @param http the HTTP client, already pointed at the service
   * @param baseUrl the service URL, used in error messages
   * @param dimensions vector length every returned embedding must have
   */
  SentenceTransformersEmbeddingClient(RestClient http, String baseUrl, int dimensions) {
    this.http = http;
    this.baseUrl = baseUrl;
    this.dimensions = dimensions;
  }

  /** JSON body sent to {@code POST /embed}. */
  record EmbedRequest(List<String> texts) {}

  /** JSON body returned by {@code POST /embed}. */
  record EmbedResponse(List<List<Float>> embeddings) {}

  @Override
  public float[] embed(String text) {
    return embedAll(List.of(requireText(text))).get(0);
  }

  @Override
  public List<float[]> embedAll(List<String> texts) {
    if (texts == null) {
      throw new IllegalArgumentException("texts must not be null");
    }
    for (String text : texts) {
      requireText(text);
    }
    List<float[]> vectors = new ArrayList<>(texts.size());
    for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
      List<String> batch = texts.subList(start, Math.min(start + BATCH_SIZE, texts.size()));
      vectors.addAll(embedBatch(batch));
    }
    return vectors;
  }

  @Override
  public int dimensions() {
    return dimensions;
  }

  private List<float[]> embedBatch(List<String> batch) {
    EmbedResponse response;
    try {
      response =
          http.post()
              .uri("/embed")
              .body(new EmbedRequest(List.copyOf(batch)))
              .retrieve()
              .body(EmbedResponse.class);
    } catch (RestClientException e) {
      throw new EmbeddingException(
          "Embedding service at "
              + baseUrl
              + " is unavailable or returned an error ("
              + e.getMessage()
              + "). Start it with: docker compose up -d embedding",
          e);
    }
    return toVectors(response, batch.size());
  }

  private List<float[]> toVectors(EmbedResponse response, int expectedCount) {
    int actual = 0;
    if (response != null && response.embeddings() != null) {
      actual = response.embeddings().size();
    }
    if (actual != expectedCount) {
      throw new EmbeddingException(
          "Embedding service returned " + actual + " vectors for " + expectedCount + " texts");
    }
    List<float[]> vectors = new ArrayList<>(expectedCount);
    for (List<Float> row : response.embeddings()) {
      if (row == null || row.size() != dimensions) {
        int rowSize = row == null ? 0 : row.size();
        throw new EmbeddingException(
            "Embedding service returned a vector with "
                + rowSize
                + " dimensions but expected "
                + dimensions
                + ". Check that EMBEDDING_MODEL and EMBEDDING_DIMENSION match the service.");
      }
      float[] vector = new float[dimensions];
      for (int i = 0; i < dimensions; i++) {
        vector[i] = row.get(i);
      }
      vectors.add(vector);
    }
    return vectors;
  }

  private static String requireText(String text) {
    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }
    return text;
  }

  private static RestClient buildRestClient(String baseUrl) {
    // HTTP/1.1 only: the JDK client's default h2c upgrade attempt breaks POSTs to uvicorn.
    HttpClient jdkClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkClient);
    requestFactory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
  }
}
