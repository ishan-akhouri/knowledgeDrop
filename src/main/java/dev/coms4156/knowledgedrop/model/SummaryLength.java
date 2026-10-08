package dev.coms4156.knowledgedrop.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import dev.coms4156.knowledgedrop.exception.ApiException;

/** Requested summary length. Serialized in lower case. */
public enum SummaryLength {
  SHORT("short"),
  DETAILED("detailed");

  private final String value;

  SummaryLength(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  /**
   * Parses a summary length case-insensitively.
   *
   * @param raw one of: short, detailed
   * @return the matching constant
   * @throws ApiException with status 400 if the value is not recognized
   */
  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static SummaryLength fromValue(String raw) {
    if (raw != null) {
      for (SummaryLength candidate : values()) {
        if (candidate.value.equalsIgnoreCase(raw.trim())) {
          return candidate;
        }
      }
    }
    throw ApiException.badRequest(
        "Unknown length '" + raw + "'; expected one of: short, detailed");
  }
}
