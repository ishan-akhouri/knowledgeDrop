package dev.coms4156.knowledgedrop.service;

import dev.coms4156.knowledgedrop.api.dto.HealthResponse;
import org.springframework.stereotype.Service;

/** Dependency health checks. */
@Service
public class HealthService {

  /**
   * Pings Elasticsearch (keyword index and vector store).
   *
   * @return the status of the dependency and the overall status
   */
  public HealthResponse check() {
    // TODO: health: stub, always healthy. Ping Elasticsearch (for example
    //   ElasticsearchClient.ping()) and report "down" for a dependency that cannot be reached.
    //   Never throw. Overall status is HealthResponse.UP only if Elasticsearch is up.
    return new HealthResponse(HealthResponse.UP, HealthResponse.UP);
  }
}
