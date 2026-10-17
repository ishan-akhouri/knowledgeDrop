package dev.coms4156.knowledgedrop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

  @InjectMocks private ClientService clientService;

  /** Verifies registration trims names, creates an active client, and stores only the token hash. */
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

    final RegisterClientResponse response = clientService.register("  Ada ", " Lovelace  ");

    ArgumentCaptor<ClientRecord> captor = ArgumentCaptor.forClass(ClientRecord.class);
    verify(clientRepository).saveAndFlush(captor.capture());
    ClientRecord stored = captor.getValue();
    assertEquals("Ada", stored.getFirstName());
    assertEquals("Lovelace", stored.getLastName());
    assertTrue(stored.isActive());
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
    ApiException first = assertThrows(ApiException.class,
        () -> clientService.register(name, "Lovelace"));
    ApiException last = assertThrows(ApiException.class,
        () -> clientService.register("Ada", name));
    assertEquals(HttpStatus.BAD_REQUEST, first.getStatus());
    assertEquals(HttpStatus.BAD_REQUEST, last.getStatus());
    verifyNoInteractions(clientRepository);
  }

  /** Verifies either name exceeding 255 characters is rejected before accessing storage. */
  @Test
  void register_oversizedName_rejectsBeforeSaving() {
    assertThrows(ApiException.class, () -> clientService.register("a".repeat(256), "Last"));
    assertThrows(ApiException.class, () -> clientService.register("First", "b".repeat(256)));
    verifyNoInteractions(clientRepository);
  }

  /** Verifies a valid token cannot authenticate an inactive client. */
  @Test
  void authenticate_inactiveClient_returnsEmpty() {
    ClientRecord client = new ClientRecord("Ada", "Lovelace", TokenUtils.hash("secret"));
    ReflectionTestUtils.setField(client, "active", false);
    when(clientRepository.findByTokenHash(TokenUtils.hash("secret")))
        .thenReturn(Optional.of(client));
    assertTrue(clientService.authenticate("secret").isEmpty());
  }

  /** Verifies authentication hashes the supplied token and returns its active client. */
  @Test
  void authenticate_knownToken_returnsClient() {
    ClientRecord client = new ClientRecord("Ada", "Lovelace", TokenUtils.hash("secret"));
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
}
