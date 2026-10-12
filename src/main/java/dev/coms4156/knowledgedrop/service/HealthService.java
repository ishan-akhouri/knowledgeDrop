package dev.coms4156.knowledgedrop.service;

import dev.coms4156.knowledgedrop.api.dto.HealthResponse;
import dev.coms4156.knowledgedrop.exception.ApiException;
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
    // TODO: health: ping Elasticsearch (for example ElasticsearchClient.ping()).
    // TODO: health: never throw: report "down" for a dependency that cannot be reached. Overall
    //   status is HealthResponse.UP only if Elasticsearch is up.
    throw ApiException.notImplemented("GET /knowledgeDrop/health");
  }
}
