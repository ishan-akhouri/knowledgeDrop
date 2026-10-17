package dev.coms4156.knowledgedrop.service;

import dev.coms4156.knowledgedrop.api.dto.MessageResponse;
import dev.coms4156.knowledgedrop.api.dto.RegisterClientResponse;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.repository.ClientRepository;
import dev.coms4156.knowledgedrop.security.TokenUtils;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Client registration and token authentication.
 *
 * <p>{@link #register} and {@link #authenticate} are implemented and serve as the reference
 * pattern (controller, service, repository, DTO, tests) for the TODO endpoints.
 */
@Service
public class ClientService {

  private final ClientRepository clientRepository;

  /**
   * Creates the service.
   *
   * @param clientRepository persistence for client records
   */
  public ClientService(ClientRepository clientRepository) {
    this.clientRepository = clientRepository;
  }

  /**
   * Registers a new client and returns its token. Only a hash of the token is stored.
   *
   * @param firstName client first name
   * @param lastName client last name
   * @return the new client ID and the plain token (shown only once)
   * @throws ApiException with status 400 if either name is blank or longer than 255 characters
   */
  public RegisterClientResponse register(String firstName, String lastName) {
    String first = validateName(firstName, "firstName");
    String last = validateName(lastName, "lastName");
    String token = TokenUtils.generateToken();
    ClientRecord saved = clientRepository.saveAndFlush(
        new ClientRecord(first, last, TokenUtils.hash(token)));
    return new RegisterClientResponse(saved.getId(), token);
  }

  private static String validateName(String value, String field) {
    if (value == null || value.isBlank() || value.length() > 255) {
      throw ApiException.badRequest(field + " must be nonblank and at most 255 characters");
    }
    return value.strip();
  }

  /**
   * Finds the client that owns a token.
   *
   * @param token the plain client token
   * @return the client, or empty if the token is unknown
   */
  public Optional<ClientRecord> authenticate(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    return clientRepository.findByTokenHash(TokenUtils.hash(token))
        .filter(ClientRecord::isActive);
  }

  /**
   * Deletes a client and all of its data.
   *
   * @param client the authenticated caller
   * @param clientId the client ID the caller wants to drop
   * @return confirmation message
   */
  public MessageResponse drop(ClientRecord client, UUID clientId) {
    // TODO: clientDrop: verify clientId equals client.getId(); otherwise throw a 4xx
    //   "token and client ID do not match".
    // TODO: clientDrop: delete every document the client owns, including the stored file and the
    //   Elasticsearch chunk entries and embeddings (reuse the DocumentService delete logic).
    // TODO: clientDrop: delete the client record, which also invalidates the token.
    throw ApiException.notImplemented("DELETE /knowledgeDrop/client/drop");
  }
}
