package dev.coms4156.knowledgedrop.service;

import dev.coms4156.knowledgedrop.exception.ApiException;
import java.util.UUID;

/**
 * Identifies a document by ID or by name ("Document ID or document name" in the API spec). Exactly
 * one of the two is set.
 *
 * @param id the document ID, or null
 * @param name the document name, or null
 */
public record DocumentRef(UUID id, String name) {

  /**
   * Builds a reference from the two optional request parameters.
   *
   * @param id document ID, may be null
   * @param name document name, may be null or blank
   * @return the reference
   * @throws ApiException with status 400 unless exactly one of the two is provided
   */
  public static DocumentRef of(UUID id, String name) {
    boolean hasId = id != null;
    boolean hasName = name != null && !name.isBlank();
    if (hasId == hasName) {
      throw ApiException.badRequest("Provide exactly one of documentId or documentName");
    }
    return new DocumentRef(hasId ? id : null, hasName ? name.trim() : null);
  }
}
