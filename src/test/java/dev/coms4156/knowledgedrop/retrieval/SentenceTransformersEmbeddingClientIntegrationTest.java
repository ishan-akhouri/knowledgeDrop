package dev.coms4156.knowledgedrop.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs against the real embedding service from docker-compose.yml ({@code docker compose up -d}).
 * Fails with a clear message when the service is not reachable.
 */
@SpringBootTest
@ActiveProfiles("test")
class SentenceTransformersEmbeddingClientIntegrationTest {

  @Autowired private SentenceTransformersEmbeddingClient client;

  @BeforeEach
  void requireService() {
    try {
      client.embed("ping");
    } catch (EmbeddingException e) {
      fail("Embedding service is not running. Start it with: docker compose up -d", e);
    }
  }

  @Test
  void embed_returnsUnitLengthVectorOfConfiguredDimension() {
    float[] vector = client.embed("How do I cancel my subscription?");

    assertThat(vector).hasSize(client.dimensions());
    assertThat(client.dimensions()).isEqualTo(384);
    assertThat(dot(vector, vector)).isCloseTo(1.0, within(0.01));
  }

  @Test
  void embed_identicalTextsGiveIdenticalVectors() {
    float[] first = client.embed("The quick brown fox");
    float[] second = client.embed("The quick brown fox");

    assertThat(first).isEqualTo(second);
  }

  @Test
  void embedAll_returnsOneVectorPerTextInOrderAcrossBatches() {
    int total = SentenceTransformersEmbeddingClient.BATCH_SIZE + 6;
    List<String> texts = new ArrayList<>();
    for (int i = 0; i < total; i++) {
      texts.add("Sentence number " + i + " about topic " + (i * 7));
    }

    List<float[]> vectors = client.embedAll(texts);

    assertThat(vectors).hasSize(total);
    assertThat(dot(vectors.get(0), client.embed(texts.get(0)))).isGreaterThan(0.999);
    assertThat(dot(vectors.get(total - 1), client.embed(texts.get(total - 1))))
        .isGreaterThan(0.999);
  }

  @Test
  void embed_relatedTextsAreCloserThanUnrelatedOnes() {
    float[] question = client.embed("How do I cancel my subscription?");
    float[] related = client.embed("Steps to terminate your plan");
    float[] unrelated = client.embed("The cat sat on the mat");

    assertThat(dot(question, related)).isGreaterThan(dot(question, unrelated));
  }

  private static double dot(float[] a, float[] b) {
    double sum = 0;
    for (int i = 0; i < a.length; i++) {
      sum += (double) a[i] * b[i];
    }
    return sum;
  }
}
