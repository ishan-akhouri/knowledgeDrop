package dev.coms4156.knowledgedrop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Async support so document ingestion can run in the background while the document status is
 * {@code processing}.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

  /**
   * Thread pool used by {@code @Async("ingestionExecutor")} methods.
   *
   * @return the configured executor
   */
  @Bean(name = "ingestionExecutor")
  public ThreadPoolTaskExecutor ingestionExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("ingest-");
    return executor;
  }
}
