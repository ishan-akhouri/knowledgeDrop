package dev.coms4156.knowledgedrop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.coms4156.knowledgedrop.config.KnowledgeDropProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Smoke tests: the application context starts and configuration binds. */
@SpringBootTest
@ActiveProfiles("test")
class KnowledgeDropApplicationTests {

  @Autowired private KnowledgeDropProperties properties;

  @Test
  void contextLoads() {
    assertNotNull(properties);
  }

  @Test
  void retrievalDefaultsMatchTheApiSpec() {
    assertEquals(5, properties.retrieval().defaultK());
    assertEquals(60, properties.retrieval().rrfK());
  }
}
