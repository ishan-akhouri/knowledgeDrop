package dev.coms4156.knowledgedrop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit tests for {@link ClientService} with a mocked repository. */
@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

  @Mock private ClientRepository clientRepository;

  @InjectMocks private ClientService clientService;

  @Test
  void register_newName_returnsTokenAndStoresOnlyItsHash() {
    UUID id = UUID.randomUUID();
    when(clientRepository.existsByName("acme")).thenReturn(false);
    when(clientRepository.saveAndFlush(any(ClientRecord.class)))
        .thenAnswer(
            invocation -> {
              ClientRecord entity = invocation.getArgument(0);
              ReflectionTestUtils.setField(entity, "id", id);
              return entity;
            });

    RegisterClientResponse response = clientService.register("  acme ");

    ArgumentCaptor<ClientRecord> captor = ArgumentCaptor.forClass(ClientRecord.class);
    verify(clientRepository).saveAndFlush(captor.capture());
    ClientRecord stored = captor.getValue();
    assertEquals("acme", stored.getName());
    assertEquals(id, response.clientId());
    assertNotEquals(response.clientToken(), stored.getTokenHash());
    assertEquals(TokenUtils.hash(response.clientToken()), stored.getTokenHash());
  }

  @Test
  void register_duplicateName_throwsConflict() {
    when(clientRepository.existsByName("acme")).thenReturn(true);

    ApiException ex = assertThrows(ApiException.class, () -> clientService.register("acme"));

    assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    verify(clientRepository, never()).saveAndFlush(any());
  }

  @Test
  void register_lostRaceOnUniqueConstraint_throwsConflict() {
    when(clientRepository.existsByName("acme")).thenReturn(false);
    when(clientRepository.saveAndFlush(any(ClientRecord.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate"));

    ApiException ex = assertThrows(ApiException.class, () -> clientService.register("acme"));

    assertEquals(HttpStatus.CONFLICT, ex.getStatus());
  }

  @Test
  void authenticate_knownToken_returnsClient() {
    ClientRecord client = new ClientRecord("acme", TokenUtils.hash("secret"));
    when(clientRepository.findByTokenHash(TokenUtils.hash("secret")))
        .thenReturn(Optional.of(client));

    Optional<ClientRecord> result = clientService.authenticate("secret");

    assertTrue(result.isPresent());
    assertSame(client, result.get());
  }

  @Test
  void authenticate_blankToken_returnsEmptyWithoutLookup() {
    assertTrue(clientService.authenticate("  ").isEmpty());
    assertTrue(clientService.authenticate(null).isEmpty());

    verifyNoInteractions(clientRepository);
  }
}
