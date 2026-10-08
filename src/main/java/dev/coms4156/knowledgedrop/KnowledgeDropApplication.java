package dev.coms4156.knowledgedrop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point for the KnowledgeDrop REST API. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class KnowledgeDropApplication {

  /**
   * Starts the application.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(KnowledgeDropApplication.class, args);
  }
}
