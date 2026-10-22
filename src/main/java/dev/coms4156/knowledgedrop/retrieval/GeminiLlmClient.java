package dev.coms4156.knowledgedrop.retrieval;

import dev.coms4156.knowledgedrop.config.KnowledgeDropProperties;
import dev.coms4156.knowledgedrop.exception.ApiException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link LlmClient} backed by the Gemini REST API ({@code generateContent}).
 *
 * <p>The model comes from {@code knowledgedrop.llm.model} and the key from {@code
 * knowledgedrop.llm.api-key}. The key is sent in the {@code x-goog-api-key} header, never in the
 * URL, so it cannot leak through logged URLs or exception messages.
 *
 * <p>Every failure surfaces as an {@link ApiException}: 503 when the client is not configured, 502
 * for anything that goes wrong talking to Gemini (HTTP errors, timeouts, empty or blocked
 * answers). A 429 or 503 from Gemini is retried once after a short pause.
 */
@Component
public class GeminiLlmClient implements LlmClient {

  /** Base URL of the Gemini API. */
  static final String BASE_URL = "https://generativelanguage.googleapis.com";

  private static final Logger LOG = LoggerFactory.getLogger(GeminiLlmClient.class);
  private static final String API_KEY_HEADER = "x-goog-api-key";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
  private static final Duration RETRY_BACKOFF = Duration.ofSeconds(2);
  private static final int TOO_MANY_REQUESTS = 429;
  private static final int SERVICE_UNAVAILABLE = 503;

  private final RestClient http;
  private final String model;
  private final String apiKey;
  private final Duration retryBackoff;

  /**
   * Creates the client from the application settings.
   *
   * @param properties application settings (LLM model and API key)
   */
  @Autowired
  public GeminiLlmClient(KnowledgeDropProperties properties) {
    this(
        buildRestClient(),
        properties.llm().model(),
        properties.llm().apiKey(),
        RETRY_BACKOFF);
  }

  /**
   * Creates the client with a specific HTTP client (unit tests pass one backed by a mock server).
   *
   * @param http the HTTP client, already pointed at the Gemini base URL
   * @param model the Gemini model name
   * @param apiKey the Gemini API key
   * @param retryBackoff how long to wait before the single retry
   */
  GeminiLlmClient(RestClient http, String model, String apiKey, Duration retryBackoff) {
    this.http = http;
    this.model = model;
    this.apiKey = apiKey;
    this.retryBackoff = retryBackoff;
  }

  /** Part of a message. */
  record Part(String text) {}

  /** The system prompt. */
  record SystemInstruction(List<Part> parts) {}

  /** One message (the user prompt in a request, the model's answer in a response). */
  record Content(String role, List<Part> parts) {}

  /** JSON body sent to {@code generateContent}. */
  record GenerateRequest(SystemInstruction systemInstruction, List<Content> contents) {}

  /** One candidate answer. */
  record Candidate(Content content, String finishReason) {}

  /** Why a prompt was blocked, if it was. */
  record PromptFeedback(String blockReason) {}

  /** JSON body returned by {@code generateContent}. */
  record GenerateResponse(List<Candidate> candidates, PromptFeedback promptFeedback) {}

  @Override
  public String complete(String systemPrompt, String userPrompt) {
    if (isBlank(apiKey) || isBlank(model)) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "LLM is not configured, set LLM_API_KEY in .env");
    }
    if (systemPrompt == null || userPrompt == null) {
      throw new IllegalArgumentException("systemPrompt and userPrompt must not be null");
    }
    GenerateRequest request =
        new GenerateRequest(
            new SystemInstruction(List.of(new Part(systemPrompt))),
            List.of(new Content("user", List.of(new Part(userPrompt)))));
    return extractText(send(request, true));
  }

  private GenerateResponse send(GenerateRequest request, boolean mayRetry) {
    try {
      return http.post()
          .uri("/v1beta/models/{model}:generateContent", model)
          .header(API_KEY_HEADER, apiKey)
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve()
          .body(GenerateResponse.class);
    } catch (RestClientResponseException e) {
      int status = e.getStatusCode().value();
      if (mayRetry && (status == TOO_MANY_REQUESTS || status == SERVICE_UNAVAILABLE)) {
        pause();
        return send(request, false);
      }
      throw badGateway(describeHttpFailure(status));
    } catch (RestClientException e) {
      LOG.warn("Gemini request failed: {}", e.getMessage());
      throw badGateway("Could not reach Gemini (timeout or connection failure)");
    }
  }

  private void pause() {
    try {
      Thread.sleep(retryBackoff.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw badGateway("Interrupted while waiting to retry the Gemini request");
    }
  }

  private static String describeHttpFailure(int status) {
    if (status == TOO_MANY_REQUESTS) {
      return "Gemini is rate limiting requests (HTTP 429, free tier limit), try again shortly";
    }
    return "Gemini returned HTTP " + status;
  }

  private static String extractText(GenerateResponse response) {
    if (response == null) {
      throw badGateway("Gemini returned an empty response");
    }
    List<Candidate> candidates = response.candidates();
    if (candidates == null || candidates.isEmpty()) {
      String reason = null;
      if (response.promptFeedback() != null) {
        reason = response.promptFeedback().blockReason();
      }
      if (reason == null) {
        throw badGateway("Gemini returned no candidates");
      }
      throw badGateway("Gemini blocked the prompt (" + reason + ")");
    }
    Candidate first = candidates.get(0);
    String text = joinText(first.content());
    if (text.isBlank()) {
      throw badGateway("Gemini returned no text (finish reason: " + first.finishReason() + ")");
    }
    return text;
  }

  private static String joinText(Content content) {
    StringBuilder text = new StringBuilder();
    if (content != null && content.parts() != null) {
      for (Part part : content.parts()) {
        if (part != null && part.text() != null) {
          text.append(part.text());
        }
      }
    }
    return text.toString();
  }

  private static ApiException badGateway(String reason) {
    return new ApiException(HttpStatus.BAD_GATEWAY, reason);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static RestClient buildRestClient() {
    HttpClient jdkClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkClient);
    requestFactory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder().baseUrl(BASE_URL).requestFactory(requestFactory).build();
  }
}
