package dev.coms4156.knowledgedrop.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import dev.coms4156.knowledgedrop.exception.ApiException;

/** Kinds of document a client can upload. Serialized in lower case. */
public enum DocumentType {
  PDF("pdf"),
  TEXT("text"),
  IMAGE("image");

  private final String value;

  DocumentType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  /**
   * Parses a document type case-insensitively.
   *
   * @param raw one of: pdf, text, image
   * @return the matching constant
   * @throws ApiException with status 400 if the value is not recognized
   */
  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static DocumentType fromValue(String raw) {
    if (raw != null) {
      for (DocumentType candidate : values()) {
        if (candidate.value.equalsIgnoreCase(raw.trim())) {
          return candidate;
        }
      }
    }
    throw ApiException.badRequest(
        "Unsupported document type '" + raw + "'; expected one of: pdf, text, image");
  }
}
