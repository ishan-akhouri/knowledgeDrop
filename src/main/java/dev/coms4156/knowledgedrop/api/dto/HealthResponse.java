package dev.coms4156.knowledgedrop.api.dto;

/**
 * Response of health.
 *
 * @param status overall status: "up" only if every dependency is up
 * @param elasticsearch status of Elasticsearch: "up" or "down"
 */
public record HealthResponse(String status, String elasticsearch) {

  /** Value used for a healthy component. */
  public static final String UP = "up";

  /** Value used for an unreachable component. */
  public static final String DOWN = "down";
}
