package dev.coms4156.knowledgedrop.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.repository.ClientRepository;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import dev.coms4156.knowledgedrop.retrieval.EmbeddingClient;
import dev.coms4156.knowledgedrop.retrieval.KeywordIndex;
import dev.coms4156.knowledgedrop.retrieval.RankedChunk;
import dev.coms4156.knowledgedrop.retrieval.VectorStore;
import dev.coms4156.knowledgedrop.security.TokenUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Exercises auth, validation, services, and owner/status filtering with the real H2 repository. */
@SpringBootTest
@ActiveProfiles("test")
class SimilarityControllerTest {
  @Autowired private WebApplicationContext context;
  @Autowired private ClientRepository clients;
  @Autowired private DocumentRepository documents;
  @MockitoBean private EmbeddingClient embeddings;
  @MockitoBean private VectorStore vectors;
  @MockitoBean private KeywordIndex keywords;

  private MockMvc mvc;
  private UUID owner;
  private String token;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).build();
    token = UUID.randomUUID().toString();
    owner = clients.save(new ClientRecord("similarity-test", TokenUtils.hash(token))).getId();
    when(embeddings.embed("sample")).thenReturn(new float[] {1, 0});
  }

  @Test
  void returnsBestChunksAsDistinctDocumentsAndDefaultsToFive() throws Exception {
    List<RankedChunk> hits = new ArrayList<>();
    DocumentRecord first = document(owner, "first.txt", "ready");
    hits.add(chunk(first, "best", 0.99));
    hits.add(chunk(first, "weaker", 0.98));
    for (int i = 0; i < 5; i++) {
      hits.add(chunk(document(owner, "other-" + i, "ready"), "match " + i, 0.9 - i * 0.1));
    }
    when(vectors.search(eq(owner), any(), eq(50))).thenReturn(hits);

    mvc.perform(post("/knowledgeDrop/similarity").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"sample\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.results.length()").value(5))
        .andExpect(jsonPath("$.results[0].documentId").value(first.getId().toString()))
        .andExpect(jsonPath("$.results[0].filename").value("first.txt"))
        .andExpect(jsonPath("$.results[0].score").value(0.99))
        .andExpect(jsonPath("$.results[0].snippet").value("best"))
        .andExpect(jsonPath("$.results[1].filename").value("other-0"));
    verifyNoInteractions(keywords);
  }

  @ParameterizedTest
  @ValueSource(strings = {"vector", "keyword", "hybrid"})
  void filtersForeignProcessingFailedAndDeletedDocumentsBeforeLimit(String mode) throws Exception {
    DocumentRecord ready = document(owner, "visible.txt", "ready");
    List<RankedChunk> hits = List.of(
        chunk(document(UUID.randomUUID(), "foreign", "ready"), "private", 1),
        chunk(document(owner, "processing", "processing"), "pending", 0.99),
        chunk(document(owner, "failed", "failed"), "failed", 0.98),
        chunk(document(owner, "deleted", "deleted"), "deleted", 0.97),
        chunk(ready, "visible", 0.8));
    when(vectors.search(eq(owner), any(), eq(50))).thenReturn(hits);
    when(keywords.search(owner, "sample", 50)).thenReturn(hits);
    mvc.perform(post("/knowledgeDrop/similarity").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"sample\",\"k\":1,\"mode\":\"" + mode + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.results.length()").value(1))
        .andExpect(jsonPath("$.results[0].documentId").value(ready.getId().toString()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"vector", "keyword", "hybrid"})
  void selectsRequestedModeAndKeepsBestChunkPerDocument(String mode) throws Exception {
    DocumentRecord first = document(owner, "first.txt", "ready");
    DocumentRecord second = document(owner, "second.txt", "ready");
    RankedChunk best = chunk(first, "best", 0.9);
    RankedChunk weaker = chunk(first, "weaker", 0.8);
    RankedChunk other = chunk(second, "other", 0.7);
    List<RankedChunk> hits = List.of(best, weaker, other);
    when(vectors.search(eq(owner), any(), eq(50))).thenReturn(hits);
    when(keywords.search(owner, "sample", 50)).thenReturn(hits);
    double expectedScore = mode.equals("hybrid") ? 2.0 / 61 : 0.9;

    mvc.perform(post("/knowledgeDrop/similarity").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"sample\",\"k\":2,\"mode\":\"" + mode + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.results.length()").value(2))
        .andExpect(jsonPath("$.results[0].documentId").value(first.getId().toString()))
        .andExpect(jsonPath("$.results[0].score").value(expectedScore))
        .andExpect(jsonPath("$.results[0].snippet").value("best"))
        .andExpect(jsonPath("$.results[1].documentId").value(second.getId().toString()));
    if (mode.equals("keyword")) {
      verifyNoInteractions(embeddings, vectors);
    } else {
      verify(embeddings).embed("sample");
      verify(vectors).search(eq(owner), any(), eq(50));
    }
    if (mode.equals("vector")) {
      verifyNoInteractions(keywords);
    } else {
      verify(keywords).search(owner, "sample", 50);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"unknown", "", "VECTOR"})
  void invalidModeReturnsClearError(String mode) throws Exception {
    mvc.perform(post("/knowledgeDrop/similarity").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"sample\",\"mode\":\"" + mode + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("mode must be one of: vector, keyword, hybrid"));
    verifyNoInteractions(embeddings, vectors, keywords);
  }

  @Test
  void emptyCollectionReturnsEmptyResults() throws Exception {
    mvc.perform(post("/knowledgeDrop/similarity").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"sample\",\"k\":null}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.results").isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"text\":null}", "{\"text\":\"\"}", "{\"text\":\"  \\t\"}"})
  void emptyTextReturnsClearError(String body) throws Exception {
    mvc.perform(post("/knowledgeDrop/similarity").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("text must not be blank"));
    verifyNoInteractions(embeddings, vectors, keywords);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"text\":\"sample\",\"k\":0}", "{\"text\":\"sample\",\"k\":-1}",
      "not json", ""})
  void invalidRequestsReturn400(String body) throws Exception {
    mvc.perform(post("/knowledgeDrop/similarity").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").isNotEmpty());
    verifyNoInteractions(embeddings, vectors, keywords);
  }

  @Test
  void absentAuthorizationHeaderReturns401() throws Exception {
    mvc.perform(post("/knowledgeDrop/similarity")
            .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"sample\"}"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(embeddings, vectors, keywords);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "Bearer invalid", "Basic invalid"})
  void missingOrInvalidTokenReturns401(String authorization) throws Exception {
    mvc.perform(post("/knowledgeDrop/similarity").header("Authorization", authorization)
            .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"sample\"}"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(embeddings, vectors, keywords);
  }

  private DocumentRecord document(UUID clientId, String name, String state) {
    DocumentRecord record = new DocumentRecord(clientId, name, "text/plain", 1, "0".repeat(40));
    if (state.equals("ready") || state.equals("deleted")) {
      record.markReady();
    } else if (state.equals("failed")) {
      record.markFailed("test failure");
    }
    if (state.equals("deleted")) {
      record.setDeletedAt(Instant.now());
    }
    return documents.save(record);
  }

  private RankedChunk chunk(DocumentRecord document, String text, double score) {
    return new RankedChunk(UUID.randomUUID().toString(), document.getId(), 0, text, 1, score);
  }
}
