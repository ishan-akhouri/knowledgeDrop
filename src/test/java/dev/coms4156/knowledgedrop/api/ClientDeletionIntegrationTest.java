package dev.coms4156.knowledgedrop.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.coms4156.knowledgedrop.api.dto.RegisterClientResponse;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.repository.ClientRepository;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import dev.coms4156.knowledgedrop.service.ClientService;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Tests client deletion through the API and real persistence against the test database. */
@SpringBootTest
@ActiveProfiles("test")
class ClientDeletionIntegrationTest {

  @Autowired private WebApplicationContext context;
  @Autowired private ClientService clientService;
  @Autowired private ClientRepository clientRepository;
  @MockitoSpyBean private DocumentRepository documentRepository;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  /**
   * Verifies all owned rows share a timestamp, other owners are untouched, and the token expires.
   */
  @Test
  void deleteClient_marksAllOwnedDocumentsAndInvalidatesToken() throws Exception {
    RegisterClientResponse owner = clientService.register("billing-system");
    RegisterClientResponse other = clientService.register("search-system");
    UUID current = document(owner.clientId(), null);
    final UUID previous = document(owner.clientId(), Instant.EPOCH);
    final UUID unrelated = document(other.clientId(), null);

    mockMvc.perform(deleteRequest(owner.clientId())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.clientToken()))
        .andExpect(status().isOk());

    Instant deletedAt = clientRepository.findById(owner.clientId()).orElseThrow().getDeletedAt();
    assertNotNull(deletedAt);
    assertEquals(deletedAt, documentRepository.findById(current).orElseThrow().getDeletedAt());
    assertEquals(deletedAt, documentRepository.findById(previous).orElseThrow().getDeletedAt());
    assertNull(documentRepository.findById(unrelated).orElseThrow().getDeletedAt());
    assertNull(clientRepository.findById(other.clientId()).orElseThrow().getDeletedAt());
    mockMvc.perform(get("/knowledgeDrop/document/list")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.clientToken()))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(deleteRequest(owner.clientId())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.clientToken()))
        .andExpect(status().isUnauthorized());
    assertEquals(deletedAt,
        clientRepository.findById(owner.clientId()).orElseThrow().getDeletedAt());
  }

  /** Verifies a client with no documents can still delete itself. */
  @Test
  void deleteClient_withoutDocuments_succeeds() throws Exception {
    RegisterClientResponse owner = clientService.register("billing-system");
    mockMvc.perform(deleteRequest(owner.clientId())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.clientToken()))
        .andExpect(status().isOk());
    assertNotNull(clientRepository.findById(owner.clientId()).orElseThrow().getDeletedAt());
  }

  /** Verifies another client's token cannot delete the target client or its documents. */
  @Test
  void deleteClient_otherOwner_isForbidden() throws Exception {
    RegisterClientResponse owner = clientService.register("billing-system");
    RegisterClientResponse other = clientService.register("search-system");
    final UUID documentId = document(owner.clientId(), null);
    mockMvc.perform(deleteRequest(owner.clientId())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.clientToken()))
        .andExpect(status().isForbidden());
    assertNull(clientRepository.findById(owner.clientId()).orElseThrow().getDeletedAt());
    assertNull(documentRepository.findById(documentId).orElseThrow().getDeletedAt());
  }

  /** Verifies missing or invalid credentials and malformed IDs are rejected before deletion. */
  @Test
  void deleteClient_invalidRequests_areRejected() throws Exception {
    RegisterClientResponse owner = clientService.register("billing-system");
    mockMvc.perform(deleteRequest(owner.clientId()))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(deleteRequest(owner.clientId())
            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(delete("/knowledgeDrop/client/delete").contentType(MediaType.APPLICATION_JSON)
            .content("{\"clientId\":\"not-a-uuid\"}")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.clientToken()))
        .andExpect(status().isBadRequest());
    assertNull(clientRepository.findById(owner.clientId()).orElseThrow().getDeletedAt());
  }

  /** Verifies missing, null, empty, and malformed deletion bodies leave the client unchanged. */
  @ParameterizedTest
  @ValueSource(strings = {"", "{}", "null", "{\"clientId\":null}",
      "{\"clientId\":\"\"}", "not json"})
  void deleteClient_invalidBody_returnsBadRequest(String body) throws Exception {
    RegisterClientResponse owner = clientService.register("billing-system");
    mockMvc.perform(delete("/knowledgeDrop/client/delete").contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.clientToken()))
        .andExpect(status().isBadRequest());
    assertNull(clientRepository.findById(owner.clientId()).orElseThrow().getDeletedAt());
  }

  private static MockHttpServletRequestBuilder deleteRequest(UUID clientId) {
    return delete("/knowledgeDrop/client/delete").contentType(MediaType.APPLICATION_JSON)
        .content("{\"clientId\":\"" + clientId + "\"}");
  }

  /** Verifies the service's own transaction restores the client if updating documents fails. */
  @Test
  void deleteClient_documentUpdateFailure_rollsBackClient() {
    RegisterClientResponse owner = clientService.register("billing-system");
    final UUID documentId = document(owner.clientId(), null);
    ClientRecord client = clientRepository.findById(owner.clientId()).orElseThrow();
    doThrow(new IllegalStateException("Simulate document update failure"))
        .when(documentRepository).softDeleteByOwnerId(eq(owner.clientId()), any(Instant.class));

    assertThrows(IllegalStateException.class, () -> clientService.delete(client, owner.clientId()));

    assertNull(clientRepository.findById(owner.clientId()).orElseThrow().getDeletedAt());
    assertNull(documentRepository.findById(documentId).orElseThrow().getDeletedAt());
    assertTrue(clientService.authenticate(owner.clientToken()).isPresent());
  }

  /** Verifies two callers authenticated before deletion cannot both delete or change timestamps. */
  @Test
  void deleteClient_concurrentRequests_onlyOneSucceeds() throws Exception {
    RegisterClientResponse owner = clientService.register("billing-system");
    final UUID documentId = document(owner.clientId(), null);
    ClientRecord client = clientService.authenticate(owner.clientToken()).orElseThrow();
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    Callable<Integer> deletion = () -> {
      if (!start.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting to start deletion");
      }
      try {
        clientService.delete(client, owner.clientId());
        return 200;
      } catch (ApiException ex) {
        return ex.getStatus().value();
      }
    };
    try {
      Future<Integer> first = executor.submit(deletion);
      Future<Integer> second = executor.submit(deletion);
      start.countDown();
      assertEquals(Set.of(200, 401),
          Set.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)));
    } finally {
      executor.shutdownNow();
    }
    Instant deletedAt = clientRepository.findById(owner.clientId()).orElseThrow().getDeletedAt();
    assertNotNull(deletedAt);
    assertEquals(deletedAt, documentRepository.findById(documentId).orElseThrow().getDeletedAt());
  }

  private UUID document(UUID ownerId, Instant deletedAt) {
    DocumentRecord record = new DocumentRecord(ownerId, UUID.randomUUID().toString(), "text/plain");
    record.setDeletedAt(deletedAt);
    return documentRepository.saveAndFlush(record).getId();
  }
}
