package dev.coms4156.knowledgedrop.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.coms4156.knowledgedrop.api.dto.RegisterClientResponse;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.repository.ClientRepository;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import dev.coms4156.knowledgedrop.service.ClientService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/** Tests client deletion through the API and real persistence against the test database. */
@SpringBootTest
@ActiveProfiles("test")
class ClientDeletionIntegrationTest {

  @Autowired private WebApplicationContext context;
  @Autowired private ClientService clientService;
  @Autowired private ClientRepository clientRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private PlatformTransactionManager transactionManager;

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
    RegisterClientResponse owner = clientService.register("Ada", "Lovelace");
    RegisterClientResponse other = clientService.register("Grace", "Hopper");
    UUID current = document(owner.clientId(), null);
    final UUID previous = document(owner.clientId(), Instant.EPOCH);
    final UUID unrelated = document(other.clientId(), null);

    mockMvc.perform(delete("/knowledgeDrop/client/{clientId}", owner.clientId())
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
    mockMvc.perform(delete("/knowledgeDrop/client/{clientId}", owner.clientId())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.clientToken()))
        .andExpect(status().isUnauthorized());
    assertEquals(deletedAt,
        clientRepository.findById(owner.clientId()).orElseThrow().getDeletedAt());
  }

  /** Verifies a client with no documents can still delete itself. */
  @Test
  void deleteClient_withoutDocuments_succeeds() throws Exception {
    RegisterClientResponse owner = clientService.register("Ada", "Lovelace");
    mockMvc.perform(delete("/knowledgeDrop/client/{clientId}", owner.clientId())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.clientToken()))
        .andExpect(status().isOk());
    assertNotNull(clientRepository.findById(owner.clientId()).orElseThrow().getDeletedAt());
  }

  /** Verifies another client's token cannot delete the target client or its documents. */
  @Test
  void deleteClient_otherOwner_isForbidden() throws Exception {
    RegisterClientResponse owner = clientService.register("Ada", "Lovelace");
    RegisterClientResponse other = clientService.register("Grace", "Hopper");
    UUID documentId = document(owner.clientId(), null);
    mockMvc.perform(delete("/knowledgeDrop/client/{clientId}", owner.clientId())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.clientToken()))
        .andExpect(status().isForbidden());
    assertNull(clientRepository.findById(owner.clientId()).orElseThrow().getDeletedAt());
    assertNull(documentRepository.findById(documentId).orElseThrow().getDeletedAt());
  }

  /** Verifies missing or invalid credentials and malformed IDs are rejected before deletion. */
  @Test
  void deleteClient_invalidRequests_areRejected() throws Exception {
    RegisterClientResponse owner = clientService.register("Ada", "Lovelace");
    mockMvc.perform(delete("/knowledgeDrop/client/{clientId}", owner.clientId()))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(delete("/knowledgeDrop/client/{clientId}", owner.clientId())
            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(delete("/knowledgeDrop/client/not-a-uuid")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.clientToken()))
        .andExpect(status().isBadRequest());
    assertNull(clientRepository.findById(owner.clientId()).orElseThrow().getDeletedAt());
  }

  /** Verifies client and document changes both roll back when their transaction fails. */
  @Test
  void deleteClient_transactionFailure_rollsBackBothTables() {
    RegisterClientResponse owner = clientService.register("Ada", "Lovelace");
    UUID documentId = document(owner.clientId(), null);
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
      clientService.delete(
          clientRepository.findById(owner.clientId()).orElseThrow(), owner.clientId());
      throw new IllegalStateException("Force rollback after both updates");
    }));
    assertNull(clientRepository.findById(owner.clientId()).orElseThrow().getDeletedAt());
    assertNull(documentRepository.findById(documentId).orElseThrow().getDeletedAt());
  }

  private UUID document(UUID ownerId, Instant deletedAt) {
    DocumentRecord record = new DocumentRecord(ownerId, UUID.randomUUID().toString(), "text/plain");
    record.setDeletedAt(deletedAt);
    return documentRepository.saveAndFlush(record).getId();
  }
}
