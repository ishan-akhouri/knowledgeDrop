package dev.coms4156.knowledgedrop.service;

import dev.coms4156.knowledgedrop.api.dto.MessageResponse;
import dev.coms4156.knowledgedrop.api.dto.RegisterClientResponse;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.repository.ClientRepository;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import dev.coms4156.knowledgedrop.security.TokenUtils;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Client registration and token authentication.
 *
 * <p>{@link #register} and {@link #authenticate} are implemented and serve as the reference
 * pattern (controller, service, repository, DTO, tests) for the TODO endpoints.
 */
@Service
public class ClientService {

  private final ClientRepository clientRepository;
  private final DocumentRepository documentRepository;

  /**
   * Creates the service.
   *
   * @param clientRepository persistence for client records
   * @param documentRepository persistence for owned documents
   */
  public ClientService(ClientRepository clientRepository, DocumentRepository documentRepository) {
    this.clientRepository = clientRepository;
    this.documentRepository = documentRepository;
  }

  /**
   * Registers a new client and returns its token. Only a hash of the token is stored.
   *
   * @param name name of the client system
   * @return the new client ID and the plain token (shown only once)
   * @throws ApiException with status 400 if the name is blank or longer than 255 characters
   */
  public RegisterClientResponse register(String name) {
    String validatedName = validateName(name);
    String token = TokenUtils.generateToken();
    ClientRecord saved = clientRepository.saveAndFlush(
        new ClientRecord(validatedName, TokenUtils.hash(token)));
    return new RegisterClientResponse(saved.getId(), token);
  }

  private static String validateName(String value) {
    if (value == null || value.isBlank() || value.length() > 255) {
      throw ApiException.badRequest("name must be nonblank and at most 255 characters");
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
        .filter(client -> client.getDeletedAt() == null);
  }

  /**
   * Soft-deletes the caller and all owned documents at one timestamp in a single transaction.
   *
   * @param client the authenticated caller
   * @param clientId the client ID to delete, which must match the caller
   * @return confirmation message
   */
  @Transactional
  public MessageResponse delete(ClientRecord client, UUID clientId) {
    if (!client.getId().equals(clientId)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Clients can only delete themselves");
    }
    ClientRecord stored = clientRepository.findByIdForUpdate(clientId)
        .orElseThrow(() -> ApiException.notFound("Client not found"));
    if (stored.getDeletedAt() != null) {
      throw ApiException.unauthorized("Client has been deleted");
    }
    Instant deletedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    stored.setDeletedAt(deletedAt);
    clientRepository.saveAndFlush(stored);
    documentRepository.softDeleteByOwnerId(clientId, deletedAt);
    return new MessageResponse("Client and owned documents deleted");
  }
}
