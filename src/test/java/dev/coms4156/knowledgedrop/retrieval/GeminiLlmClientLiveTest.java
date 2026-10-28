package dev.coms4156.knowledgedrop.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Calls the real Gemini API. Runs only when {@code LLM_API_KEY} is set in the environment, so CI
 * and anyone without a key skip it. To run it locally:
 *
 * <pre>
 * set -a; . ./.env; set +a
 * ./mvnw -Dtest=GeminiLlmClientLiveTest test
 * </pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "LLM_API_KEY", matches = ".+")
class GeminiLlmClientLiveTest {

  @Autowired private GeminiLlmClient client;

  @Test
  void complete_answersSimpleQuestion() {
    String answer =
        client.complete(
            "Answer with a single word and nothing else.", "What is the capital of France?");

    assertThat(answer).containsIgnoringCase("paris");
  }
}
