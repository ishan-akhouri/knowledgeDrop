package dev.coms4156.knowledgedrop.service;

import dev.coms4156.knowledgedrop.api.dto.HealthResponse;
import dev.coms4156.knowledgedrop.exception.ApiException;
import org.springframework.stereotype.Service;

/** Dependency health checks. */
@Service
public class HealthService {

  /**
   * Pings Redis and Elasticsearch.
   *
   * @return the status of each dependency and the overall status
   */
  public HealthResponse check() {
    // TODO(health): ping Redis (for example via StringRedisTemplate / RedisConnectionFactory).
    // TODO(health): ping Elasticsearch (for example ElasticsearchClient.ping()).
    // TODO(health): never throw: report "down" for a dependency that cannot be reached. Overall
    //   status is HealthResponse.UP only if both are up.
    throw ApiException.notImplemented("GET /knowledgeDrop/health");
  }
}
