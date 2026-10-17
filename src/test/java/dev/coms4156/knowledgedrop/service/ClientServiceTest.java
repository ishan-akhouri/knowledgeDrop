package dev.coms4156.knowledgedrop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.coms4156.knowledgedrop.api.dto.RegisterClientResponse;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.repository.ClientRepository;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import dev.coms4156.knowledgedrop.security.TokenUtils;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit tests for {@link ClientService} with a mocked repository. */
@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

  @Mock private ClientRepository clientRepository;
  @Mock private DocumentRepository documentRepository;

  @InjectMocks private ClientService clientService;

  /**
   * Verifies registration trims the name, creates an active client, and stores only the token hash.
   */
  @Test
  void register_newName_returnsTokenAndStoresOnlyItsHash() {
    UUID id = UUID.randomUUID();
    when(clientRepository.saveAndFlush(any(ClientRecord.class)))
        .thenAnswer(
            invocation -> {
              ClientRecord entity = invocation.getArgument(0);
              ReflectionTestUtils.setField(entity, "id", id);
              return entity;
            });

    final RegisterClientResponse response = clientService.register("  billing-system  ");

    ArgumentCaptor<ClientRecord> captor = ArgumentCaptor.forClass(ClientRecord.class);
    verify(clientRepository).saveAndFlush(captor.capture());
    ClientRecord stored = captor.getValue();
    assertEquals("billing-system", stored.getName());
    assertNull(stored.getDeletedAt());
    assertNotNull(stored.getCreatedAt());
    assertEquals(id, response.clientId());
    assertNotEquals(response.clientToken(), stored.getTokenHash());
    assertEquals(TokenUtils.hash(response.clientToken()), stored.getTokenHash());
  }

  /** Verifies null, empty, or whitespace-only names return HTTP 400 without accessing storage. */
  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "\t", "\u2003"})
  void register_invalidName_rejectsBeforeSaving(String name) {
    ApiException error = assertThrows(ApiException.class, () -> clientService.register(name));
    assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    verifyNoInteractions(clientRepository);
  }

  /** Verifies a name exceeding 255 characters is rejected before accessing storage. */
  @Test
  void register_oversizedName_rejectsBeforeSaving() {
    assertThrows(ApiException.class, () -> clientService.register("a".repeat(256)));
    verifyNoInteractions(clientRepository);
  }

  /** Verifies a valid token cannot authenticate a deleted client. */
  @Test
  void authenticate_deletedClient_returnsEmpty() {
    ClientRecord client = new ClientRecord("billing-system", TokenUtils.hash("secret"));
    client.setDeletedAt(java.time.Instant.now());
    when(clientRepository.findByTokenHash(TokenUtils.hash("secret")))
        .thenReturn(Optional.of(client));
    assertTrue(clientService.authenticate("secret").isEmpty());
  }

  /** Verifies authentication hashes the supplied token and returns its active client. */
  @Test
  void authenticate_knownToken_returnsClient() {
    ClientRecord client = new ClientRecord("billing-system", TokenUtils.hash("secret"));
    when(clientRepository.findByTokenHash(TokenUtils.hash("secret")))
        .thenReturn(Optional.of(client));

    Optional<ClientRecord> result = clientService.authenticate("secret");

    assertTrue(result.isPresent());
    assertSame(client, result.get());
  }

  /** Verifies null or whitespace-only tokens return no client without a database lookup. */
  @Test
  void authenticate_blankToken_returnsEmptyWithoutLookup() {
    assertTrue(clientService.authenticate("  ").isEmpty());
    assertTrue(clientService.authenticate(null).isEmpty());

    verifyNoInteractions(clientRepository);
  }

  /** Verifies deleting oneself saves the same timestamp for the client and every owned document. */
  @Test
  void delete_self_usesOneTimestamp() {
    ClientRecord client = clientWithId();
    when(clientRepository.findByIdForUpdate(client.getId())).thenReturn(Optional.of(client));

    clientService.delete(client, client.getId());

    assertNotNull(client.getDeletedAt());
    verify(clientRepository).saveAndFlush(client);
    verify(documentRepository).softDeleteByOwnerId(client.getId(), client.getDeletedAt());
  }

  /**
   * Verifies a caller cannot delete another client or access either repository for that request.
   */
  @Test
  void delete_otherClient_returnsForbidden() {
    ClientRecord client = clientWithId();
    ApiException error = assertThrows(ApiException.class,
        () -> clientService.delete(client, UUID.randomUUID()));
    assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    verifyNoInteractions(clientRepository, documentRepository);
  }

  /**
   * Verifies a client removed since authentication returns not found without touching documents.
   */
  @Test
  void delete_missingClient_returnsNotFound() {
    ClientRecord client = clientWithId();
    when(clientRepository.findByIdForUpdate(client.getId())).thenReturn(Optional.empty());
    ApiException error = assertThrows(ApiException.class,
        () -> clientService.delete(client, client.getId()));
    assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
    verifyNoInteractions(documentRepository);
  }

  /** Verifies a concurrent repeat deletion cannot overwrite the original deletion timestamp. */
  @Test
  void delete_alreadyDeleted_returnsUnauthorized() {
    ClientRecord client = clientWithId();
    client.setDeletedAt(java.time.Instant.EPOCH);
    when(clientRepository.findByIdForUpdate(client.getId())).thenReturn(Optional.of(client));
    ApiException error = assertThrows(ApiException.class,
        () -> clientService.delete(client, client.getId()));
    assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
    assertEquals(java.time.Instant.EPOCH, client.getDeletedAt());
    verifyNoInteractions(documentRepository);
  }

  private static ClientRecord clientWithId() {
    ClientRecord client = new ClientRecord("billing-system", TokenUtils.hash("secret"));
    ReflectionTestUtils.setField(client, "id", UUID.randomUUID());
    return client;
  }

}
