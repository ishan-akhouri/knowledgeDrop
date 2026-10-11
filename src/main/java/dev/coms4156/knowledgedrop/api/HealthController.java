package dev.coms4156.knowledgedrop.api;

import dev.coms4156.knowledgedrop.api.dto.HealthResponse;
import dev.coms4156.knowledgedrop.service.HealthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service health endpoint (no token required). TODO(health). */
@RestController
@RequestMapping("/knowledgeDrop")
public class HealthController {

  private final HealthService healthService;

  /**
   * Creates the controller.
   *
   * @param healthService dependency health checks
   */
  public HealthController(HealthService healthService) {
    this.healthService = healthService;
  }

  /** Returns 200 if Redis and Elasticsearch are both reachable, 503 if either is down. */
  @GetMapping("/health")
  public ResponseEntity<HealthResponse> health() {
    HealthResponse body = healthService.check();
    HttpStatus status =
        HealthResponse.UP.equals(body.status()) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
    return ResponseEntity.status(status).body(body);
  }
}
