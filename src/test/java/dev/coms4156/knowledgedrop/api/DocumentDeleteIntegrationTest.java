package dev.coms4156.knowledgedrop.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.coms4156.knowledgedrop.api.dto.RegisterClientResponse;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.model.DocumentType;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import dev.coms4156.knowledgedrop.service.ClientService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Exercises header binding, authentication, ownership, and persisted soft deletion. */
@SpringBootTest
@ActiveProfiles("test")
class DocumentDeleteIntegrationTest {

  @Autowired private WebApplicationContext context;
  @Autowired private ClientService clients;
  @Autowired private DocumentRepository documents;

  private MockMvc mockMvc;
  private RegisterClientResponse owner;
  private DocumentRecord document;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    owner = clients.register("delete-test-" + UUID.randomUUID());
    document = documents.saveAndFlush(
        new DocumentRecord(owner.clientId(), "Meeting Notes", DocumentType.TEXT, "/files/notes"));
  }

  private MockHttpServletRequestBuilder request() {
    return delete("/knowledgeDrop/delete")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.clientToken());
  }

  @Test
  void deleteById_persistsFlagAndRetainsMetadata() throws Exception {
    mockMvc.perform(request().header("documentId", document.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Document deleted successfully"));

    DocumentRecord stored = documents.findById(document.getId()).orElseThrow();
    assertTrue(stored.isDeleted());
    assertEquals(document.getStoragePath(), stored.getStoragePath());
    assertEquals(document.getStatus(), stored.getStatus());
  }

  @Test
  void deleteByTitle_matchesCaseInsensitivelyAndOnlyForOwner() throws Exception {
    RegisterClientResponse other = clients.register("other-" + UUID.randomUUID());
    DocumentRecord otherDocument = documents.saveAndFlush(
        new DocumentRecord(other.clientId(), "Meeting Notes", DocumentType.TEXT, "/other"));

    mockMvc.perform(request().header("documentTitle", "mEeTiNg nOtEs"))
        .andExpect(status().isOk());

    assertTrue(documents.findById(document.getId()).orElseThrow().isDeleted());
    assertFalse(documents.findById(otherDocument.getId()).orElseThrow().isDeleted());
  }

  @Test
  void missingInvalidAndMalformedTokens_return401() throws Exception {
    mockMvc.perform(delete("/knowledgeDrop/delete").header("documentId", document.getId()))
        .andExpect(status().isUnauthorized());
    for (String authorization : new String[] {"Bearer invalid", "Basic invalid", "Bearer "}) {
      mockMvc.perform(delete("/knowledgeDrop/delete")
              .header("documentId", document.getId())
              .header(HttpHeaders.AUTHORIZATION, authorization))
          .andExpect(status().isUnauthorized());
    }
    assertFalse(documents.findById(document.getId()).orElseThrow().isDeleted());
  }

  @Test
  void missingBothBlankTitleBothSelectorsAndMalformedId_return400() throws Exception {
    mockMvc.perform(request()).andExpect(status().isBadRequest());
    mockMvc.perform(request().header("documentTitle", " "))
        .andExpect(status().isBadRequest());
    mockMvc.perform(request().header("documentId", document.getId())
            .header("documentTitle", document.getName()))
        .andExpect(status().isBadRequest());
    mockMvc.perform(request().header("documentId", "not-a-uuid"))
        .andExpect(status().isBadRequest());
    assertFalse(documents.findById(document.getId()).orElseThrow().isDeleted());
  }

  @Test
  void unknownIdAndTitle_return404() throws Exception {
    mockMvc.perform(request().header("documentId", UUID.randomUUID()))
        .andExpect(status().isNotFound());
    mockMvc.perform(request().header("documentTitle", "missing"))
        .andExpect(status().isNotFound());
  }

  @Test
  void otherClientsDocument_returns404() throws Exception {
    RegisterClientResponse other = clients.register("other-" + UUID.randomUUID());
    mockMvc.perform(delete("/knowledgeDrop/delete")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.clientToken())
            .header("documentId", document.getId()))
        .andExpect(status().isNotFound());
    mockMvc.perform(delete("/knowledgeDrop/delete")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.clientToken())
            .header("documentTitle", document.getName()))
        .andExpect(status().isNotFound());
    assertFalse(documents.findById(document.getId()).orElseThrow().isDeleted());
  }

  @Test
  void repeatedDeletionByIdOrTitle_returns404() throws Exception {
    mockMvc.perform(request().header("documentId", document.getId()))
        .andExpect(status().isOk());
    mockMvc.perform(request().header("documentId", document.getId()))
        .andExpect(status().isNotFound());
    mockMvc.perform(request().header("documentTitle", document.getName()))
        .andExpect(status().isNotFound());
  }

  @Test
  void ambiguousTitle_returns409() throws Exception {
    documents.saveAndFlush(
        new DocumentRecord(owner.clientId(), "MEETING NOTES", DocumentType.TEXT, "/other"));
    mockMvc.perform(request().header("documentTitle", "meeting notes"))
        .andExpect(status().isConflict());
    assertFalse(documents.findById(document.getId()).orElseThrow().isDeleted());
  }

  @Test
  void queryParametersDoNotReplaceRequiredHeaders() throws Exception {
    mockMvc.perform(request().param("documentId", document.getId().toString()))
        .andExpect(status().isBadRequest());
    assertFalse(documents.findById(document.getId()).orElseThrow().isDeleted());
  }
}
