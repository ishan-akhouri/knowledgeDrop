package dev.coms4156.knowledgedrop.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.coms4156.knowledgedrop.exception.ApiException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Unit tests with a mocked HTTP server, so no API key or network is needed. */
class GeminiLlmClientTest {

  private static final String BASE_URL = "https://gemini.test";
  private static final String URL = BASE_URL + "/v1beta/models/test-model:generateContent";
  private static final String API_KEY = "test-key-123";
  private static final String ANSWER =
      "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"Hello\"}]},"
          + "\"finishReason\":\"STOP\"}]}";

  private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final GeminiLlmClient client = clientWith("test-model", API_KEY);

  @Test
  void complete_sendsBothPromptsWithKeyInHeaderAndReturnsText() {
    server
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("x-goog-api-key", API_KEY))
        .andExpect(
            request ->
                assertThat(((MockClientHttpRequest) request).getBodyAsString())
                    .contains("\"systemInstruction\"")
                    .contains("\"text\":\"Answer only from the context\"")
                    .contains("\"role\":\"user\"")
                    .contains("\"text\":\"What is RRF?\""))
        .andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));

    String answer = client.complete("Answer only from the context", "What is RRF?");

    assertThat(answer).isEqualTo("Hello");
    server.verify();
  }

  @Test
  void complete_joinsMultipleTextParts() {
    String body =
        "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hel\"},{\"text\":\"lo\"}]},"
            + "\"finishReason\":\"STOP\"}]}";
    server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertThat(client.complete("s", "u")).isEqualTo("Hello");
  }

  @Test
  void complete_blankApiKey_isServiceUnavailableWithoutCallingGemini() {
    for (String key : new String[] {null, "", "   "}) {
      GeminiLlmClient unconfigured = clientWith("test-model", key);

      assertThatThrownBy(() -> unconfigured.complete("s", "u"))
          .isInstanceOfSatisfying(
              ApiException.class,
              e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
          .hasMessageContaining("LLM_API_KEY");
    }
    server.verify();
  }

  @Test
  void complete_blankModel_isServiceUnavailable() {
    GeminiLlmClient unconfigured = clientWith("  ", API_KEY);

    assertThatThrownBy(() -> unconfigured.complete("s", "u"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
  }

  @Test
  void complete_nullPrompt_isRejected() {
    assertThatThrownBy(() -> client.complete(null, "u"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> client.complete("s", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void complete_rateLimitedThenSuccess_retriesOnce() {
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
    server.expect(requestTo(URL)).andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));

    assertThat(client.complete("s", "u")).isEqualTo("Hello");
    server.verify();
  }

  @Test
  void complete_serviceUnavailableThenSuccess_retriesOnce() {
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    server.expect(requestTo(URL)).andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));

    assertThat(client.complete("s", "u")).isEqualTo("Hello");
    server.verify();
  }

  @Test
  void complete_rateLimitedTwice_isBadGatewayAfterOneRetry() {
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

    assertThatThrownBy(() -> client.complete("s", "u"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY))
        .hasMessageContaining("429");
    server.verify();
  }

  @Test
  void complete_serverError_isBadGatewayWithoutRetryOrKeyInTheMessage() {
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> client.complete("s", "u"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY))
        .hasMessageContaining("500")
        .hasMessageNotContaining(API_KEY);
    server.verify();
  }

  @Test
  void complete_clientError_isBadGatewayWithoutRetry() {
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(() -> client.complete("s", "u"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY))
        .hasMessageContaining("400");
    server.verify();
  }

  @Test
  void complete_timeout_isBadGateway() {
    server
        .expect(requestTo(URL))
        .andRespond(withException(new SocketTimeoutException("Read timed out")));

    assertThatThrownBy(() -> client.complete("s", "u"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY))
        .hasMessageContaining("Could not reach Gemini")
        .hasMessageNotContaining(API_KEY);
  }

  @Test
  void complete_noCandidates_isBadGateway() {
    server
        .expect(requestTo(URL))
        .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.complete("s", "u"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY))
        .hasMessageContaining("no candidates");
  }

  @Test
  void complete_blockedPrompt_isBadGatewayNamingTheReason() {
    String body = "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}";
    server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.complete("s", "u"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY))
        .hasMessageContaining("blocked")
        .hasMessageContaining("SAFETY");
  }

  @Test
  void complete_truncatedAnswerWithText_isBadGatewayNotPartial() {
    String body =
        "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"The answer starts\"}]},"
            + "\"finishReason\":\"MAX_TOKENS\"}]}";
    server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.complete("s", "u"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY))
        .hasMessageContaining("MAX_TOKENS")
        .hasMessageContaining("incomplete");
  }

  @Test
  void complete_stoppedForSafetyWithPartialText_isBadGateway() {
    String body =
        "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Partial\"}]},"
            + "\"finishReason\":\"SAFETY\"}]}";
    server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.complete("s", "u"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY))
        .hasMessageContaining("SAFETY");
  }

  @Test
  void complete_missingFinishReason_isBadGateway() {
    String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello\"}]}}]}";
    server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.complete("s", "u"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY))
        .hasMessageContaining("missing");
  }

  @Test
  void complete_stopWithBlankText_isBadGateway() {
    String body =
        "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"  \"}]},"
            + "\"finishReason\":\"STOP\"}]}";
    server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.complete("s", "u"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY))
        .hasMessageContaining("no text");
  }

  @Test
  void complete_candidateWithoutText_isBadGateway() {
    String body = "{\"candidates\":[{\"content\":{\"parts\":[]},\"finishReason\":\"MAX_TOKENS\"}]}";
    server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.complete("s", "u"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY))
        .hasMessageContaining("MAX_TOKENS");
  }

  private GeminiLlmClient clientWith(String model, String apiKey) {
    return new GeminiLlmClient(builder.build(), model, apiKey, Duration.ZERO);
  }
}
