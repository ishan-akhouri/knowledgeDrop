package dev.coms4156.knowledgedrop.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Unit tests with a mocked HTTP server, so no embedding service needs to be running. */
class SentenceTransformersEmbeddingClientTest {

  private static final String BASE_URL = "http://embedding.test";
  private static final int DIMENSIONS = 2;

  private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final SentenceTransformersEmbeddingClient client =
      new SentenceTransformersEmbeddingClient(builder.build(), BASE_URL, DIMENSIONS);

  @Test
  void embed_postsTheTextAndReturnsTheVector() {
    server
        .expect(requestTo(BASE_URL + "/embed"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string("{\"texts\":[\"hello\"]}"))
        .andRespond(withSuccess("{\"embeddings\":[[0.25,0.5]]}", MediaType.APPLICATION_JSON));

    float[] vector = client.embed("hello");

    assertThat(vector).containsExactly(0.25f, 0.5f);
    server.verify();
  }

  @Test
  void embedAll_splitsLargeInputsIntoBatchesAndKeepsOrder() {
    int batch = SentenceTransformersEmbeddingClient.BATCH_SIZE;
    String first = response(batch, 0);
    String second = response(batch, 1);
    String third = response(2, 2);
    server
        .expect(requestTo(BASE_URL + "/embed"))
        .andRespond(withSuccess(first, MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(BASE_URL + "/embed"))
        .andRespond(withSuccess(second, MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(BASE_URL + "/embed"))
        .andRespond(withSuccess(third, MediaType.APPLICATION_JSON));
    int total = batch * 2 + 2;
    List<String> texts = new ArrayList<>();
    for (int i = 0; i < total; i++) {
      texts.add("text " + i);
    }

    List<float[]> vectors = client.embedAll(texts);

    assertThat(vectors).hasSize(total);
    assertThat(vectors.get(0)).containsExactly(0f, 0.5f);
    assertThat(vectors.get(batch)).containsExactly(1f, 0.5f);
    assertThat(vectors.get(total - 1)).containsExactly(2f, 0.5f);
    server.verify();
  }

  @Test
  void embedAll_noTexts_returnsEmptyWithoutCallingTheService() {
    assertThat(client.embedAll(List.of())).isEmpty();

    server.verify();
  }

  @Test
  void embedAll_nullListOrNullText_isRejected() {
    assertThatThrownBy(() -> client.embedAll(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> client.embed(null)).isInstanceOf(IllegalArgumentException.class);
    List<String> withNull = new ArrayList<>();
    withNull.add(null);
    assertThatThrownBy(() -> client.embedAll(withNull))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void embed_serviceReturnsAnError_isReportedWithHowToFix() {
    server.expect(requestTo(BASE_URL + "/embed")).andRespond(withServerError());

    assertThatThrownBy(() -> client.embed("hello"))
        .isInstanceOf(EmbeddingException.class)
        .hasMessageContaining(BASE_URL)
        .hasMessageContaining("docker compose up -d embedding");
  }

  @Test
  void embed_serviceNotRunning_isReportedWithHowToFix() {
    SentenceTransformersEmbeddingClient unreachable =
        new SentenceTransformersEmbeddingClient(
            RestClient.create("http://localhost:1"), "http://localhost:1", DIMENSIONS);

    assertThatThrownBy(() -> unreachable.embed("hello"))
        .isInstanceOf(EmbeddingException.class)
        .hasMessageContaining("docker compose up -d embedding");
  }

  @Test
  void embed_vectorOfTheWrongSize_isRejected() {
    server
        .expect(requestTo(BASE_URL + "/embed"))
        .andRespond(withSuccess("{\"embeddings\":[[0.1,0.2,0.3]]}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.embed("hello"))
        .isInstanceOf(EmbeddingException.class)
        .hasMessageContaining("3 dimensions but expected 2");
  }

  @Test
  void embedAll_wrongNumberOfVectors_isRejected() {
    server
        .expect(requestTo(BASE_URL + "/embed"))
        .andRespond(withSuccess("{\"embeddings\":[[0.1,0.2]]}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.embedAll(List.of("one", "two")))
        .isInstanceOf(EmbeddingException.class)
        .hasMessageContaining("1 vectors for 2 texts");
  }

  @Test
  void dimensions_isTheConfiguredValue() {
    assertThat(client.dimensions()).isEqualTo(DIMENSIONS);
  }

  /** A response with {@code rows} two-dimensional vectors whose first value is {@code marker}. */
  private static String response(int rows, int marker) {
    StringBuilder json = new StringBuilder("{\"embeddings\":[");
    for (int i = 0; i < rows; i++) {
      if (i > 0) {
        json.append(',');
      }
      json.append('[').append(marker).append(".0,0.5]");
    }
    return json.append("]}").toString();
  }
}
