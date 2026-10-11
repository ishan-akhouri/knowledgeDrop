package dev.coms4156.knowledgedrop.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import dev.coms4156.knowledgedrop.exception.ApiException;

/** Lifecycle of a document: processing (being ingested), ready (searchable), or failed. */
public enum DocumentStatus {
  PROCESSING("processing"),
  READY("ready"),
  FAILED("failed");

  private final String value;

  DocumentStatus(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  /**
   * Parses a status case-insensitively.
   *
   * @param raw one of: processing, ready, failed
   * @return the matching constant
   * @throws ApiException with status 400 if the value is not recognized
   */
  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static DocumentStatus fromValue(String raw) {
    if (raw != null) {
      for (DocumentStatus candidate : values()) {
        if (candidate.value.equalsIgnoreCase(raw.trim())) {
          return candidate;
        }
      }
    }
    throw ApiException.badRequest(
        "Unknown status '" + raw + "'; expected one of: processing, ready, failed");
  }
}
