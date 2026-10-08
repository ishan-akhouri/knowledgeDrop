package dev.coms4156.knowledgedrop.service;

import dev.coms4156.knowledgedrop.api.dto.MessageResponse;
import dev.coms4156.knowledgedrop.api.dto.RegisterClientResponse;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.repository.ClientRepository;
import dev.coms4156.knowledgedrop.security.TokenUtils;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
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
   * @param clientName the requested client name
   * @return the new client ID and the plain token (shown only once)
   * @throws ApiException with status 409 if the name is already registered
   */
  public RegisterClientResponse register(String clientName) {
    String name = clientName.trim();
    if (clientRepository.existsByName(name)) {
      throw ApiException.conflict("Client name '" + name + "' is already registered");
    }
    String token = TokenUtils.generateToken();
    ClientRecord saved;
    try {
      saved = clientRepository.saveAndFlush(new ClientRecord(name, TokenUtils.hash(token)));
    } catch (DataIntegrityViolationException e) {
      // Lost a race with a concurrent registration of the same name.
      throw ApiException.conflict("Client name '" + name + "' is already registered");
    }
    return new RegisterClientResponse(saved.getId(), token);
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
    return clientRepository.findByTokenHash(TokenUtils.hash(token));
  }

  /**
   * Deletes a client and all of its data.
   *
   * @param client the authenticated caller
   * @param clientId the client ID the caller wants to drop
   * @return confirmation message
   */
  public MessageResponse drop(ClientRecord client, UUID clientId) {
    // TODO(clientDrop): verify clientId equals client.getId(); otherwise throw a 4xx
    //   "token and client ID do not match".
    // TODO(clientDrop): delete every document the client owns, including the stored file, the
    //   Redis embeddings, and the Elasticsearch entries (reuse the DocumentService delete logic).
    // TODO(clientDrop): delete the client record, which also invalidates the token.
    throw ApiException.notImplemented("DELETE /knowledgeDrop/clientDrop");
  }
}
