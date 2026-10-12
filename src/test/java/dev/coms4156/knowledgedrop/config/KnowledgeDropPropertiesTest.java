package dev.coms4156.knowledgedrop.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** Unit tests for binding {@link KnowledgeDropProperties}, including the provider settings. */
class KnowledgeDropPropertiesTest {

  private static KnowledgeDropProperties bind(Map<String, String> source) {
    return new Binder(new MapConfigurationPropertySource(source))
        .bind("knowledgedrop", KnowledgeDropProperties.class)
        .get();
  }

  @Test
  void providerDefaultsApplyWhenNothingIsConfigured() {
    KnowledgeDropProperties props = bind(Map.of("knowledgedrop.storage-dir", "./data/files"));

    assertThat(props.embedding().baseUrl()).isEqualTo("http://localhost:5000");
    assertThat(props.embedding().model()).isEqualTo("all-MiniLM-L6-v2");
    assertThat(props.embedding().dimension()).isEqualTo(384);
    assertThat(props.llm().provider()).isEmpty();
    assertThat(props.llm().apiKey()).isEmpty();
  }

  @Test
  void configuredProviderValuesOverrideDefaults() {
    KnowledgeDropProperties props =
        bind(
            Map.of(
                "knowledgedrop.embedding.dimension", "768",
                "knowledgedrop.llm.provider", "other",
                "knowledgedrop.llm.api-key", "secret-key"));

    assertThat(props.embedding().dimension()).isEqualTo(768);
    assertThat(props.llm().provider()).isEqualTo("other");
    assertThat(props.llm().apiKey()).isEqualTo("secret-key");
  }

  @Test
  void llmToStringNeverContainsTheApiKey() {
    KnowledgeDropProperties.Llm llm =
        new KnowledgeDropProperties.Llm("anthropic", "secret-key", "m");

    assertThat(llm.toString()).doesNotContain("secret-key").contains("<redacted>");
  }

  @Test
  void llmToStringShowsUnsetWhenNoKey() {
    KnowledgeDropProperties.Llm llm = new KnowledgeDropProperties.Llm("anthropic", "", "m");

    assertThat(llm.toString()).contains("<unset>");
  }
}
