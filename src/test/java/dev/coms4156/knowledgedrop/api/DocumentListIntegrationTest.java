package dev.coms4156.knowledgedrop.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.model.DocumentStatus;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * End-to-end tests for {@code GET /knowledgeDrop/document/list} through the real servlet stack and
 * an in-memory database. Documents are seeded straight into the repository so each test controls
 * status, upload time and deletion without running ingestion.
 */
@SpringBootTest
@ActiveProfiles("test")
class DocumentListIntegrationTest {

  private static final String LIST_URL = "/knowledgeDrop/document/list";
  private static final String HELLO_SHA1 = "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d";
  private static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");

  @Autowired private WebApplicationContext context;

  @Autowired private DocumentRepository documentRepository;

  private MockMvc mockMvc;
  private String token;
  private UUID clientId;

  @BeforeEach
  void setUp() throws Exception {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    Registered registered = registerClient();
    token = registered.token();
    clientId = registered.clientId();
  }

  // ---------------------------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------------------------

  private record Registered(UUID clientId, String token) {}

  private Registered registerClient() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/knowledgeDrop/client/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"client-" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String body = result.getResponse().getContentAsString();
    return new Registered(
        UUID.fromString(JsonPath.read(body, "$.clientId")), JsonPath.read(body, "$.clientToken"));
  }

  /** Stores a document straight in the repository with full control over its state. */
  private DocumentRecord seed(
      UUID ownerId, String name, DocumentStatus status, Instant uploadedAt, boolean deleted) {
    DocumentRecord record = new DocumentRecord(ownerId, name, "text/plain", 5L, HELLO_SHA1);
    ReflectionTestUtils.setField(record, "uploadedAt", uploadedAt);
    if (status == DocumentStatus.READY) {
      record.markReady();
    } else if (status == DocumentStatus.FAILED) {
      record.markFailed("ingestion failed");
    }
    if (deleted) {
      record.setDeletedAt(Instant.now());
    }
    return documentRepository.saveAndFlush(record);
  }

  /** Seeds a live document for the test client; {@code minute} orders uploads (higher = newer). */
  private DocumentRecord seed(String name, DocumentStatus status, int minute) {
    return seed(clientId, name, status, BASE_TIME.plus(minute, ChronoUnit.MINUTES), false);
  }

  /** GETs the list endpoint as the given token, with alternating name/value query params. */
  private ResultActions listAs(String bearerToken, String... queryParams) throws Exception {
    MockHttpServletRequestBuilder request =
        get(LIST_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
    for (int i = 0; i + 1 < queryParams.length; i += 2) {
      request.param(queryParams[i], queryParams[i + 1]);
    }
    return mockMvc.perform(request);
  }

  /** GETs the list endpoint as the test client. */
  private ResultActions list(String... queryParams) throws Exception {
    return listAs(token, queryParams);
  }

  // ---------------------------------------------------------------------------------------------
  // authentication
  // ---------------------------------------------------------------------------------------------

  @Test
  void list_withoutToken_returns401() throws Exception {
    mockMvc
        .perform(get(LIST_URL))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.reason").isNotEmpty());
  }

  @Test
  void list_invalidToken_returns401() throws Exception {
    listAs("not-a-real-token").andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------------------------------------
  // basic listing
  // ---------------------------------------------------------------------------------------------

  @Test
  void list_clientWithNoDocuments_returns200AndEmptyArray() throws Exception {
    list().andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void list_returnsSummaryFieldsForEachDocument() throws Exception {
    DocumentRecord doc = seed("notes.txt", DocumentStatus.READY, 1);

    list().andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].documentId").value(doc.getId().toString()))
        .andExpect(jsonPath("$[0].filename").value("notes.txt"))
        .andExpect(jsonPath("$[0].type").value("text/plain"))
        .andExpect(jsonPath("$[0].uploadTime").isNotEmpty())
        .andExpect(jsonPath("$[0].status").value("ready"));
  }

  @Test
  void list_reportsEachStatusInItsApiSpelling() throws Exception {
    seed("p.txt", DocumentStatus.PROCESSING, 1);
    seed("r.txt", DocumentStatus.READY, 2);
    seed("f.txt", DocumentStatus.FAILED, 3);

    list().andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.filename=='p.txt')].status").value(contains("processing")))
        .andExpect(jsonPath("$[?(@.filename=='r.txt')].status").value(contains("ready")))
        .andExpect(jsonPath("$[?(@.filename=='f.txt')].status").value(contains("failed")));
  }

  @Test
  void list_doesNotExposeInternalFields() throws Exception {
    seed("notes.txt", DocumentStatus.FAILED, 1);

    list().andExpect(status().isOk())
        .andExpect(jsonPath("$[0].failureReason").doesNotExist())
        .andExpect(jsonPath("$[0].contentHash").doesNotExist())
        .andExpect(jsonPath("$[0].ownerId").doesNotExist())
        .andExpect(jsonPath("$[0].deletedAt").doesNotExist());
  }

  @Test
  void list_returnsNewestUploadsFirst() throws Exception {
    seed("oldest.txt", DocumentStatus.READY, 1);
    seed("newest.txt", DocumentStatus.READY, 30);
    seed("middle.txt", DocumentStatus.READY, 15);

    list().andExpect(status().isOk())
        .andExpect(
            jsonPath("$[*].filename")
                .value(contains("newest.txt", "middle.txt", "oldest.txt")));
  }

  // ---------------------------------------------------------------------------------------------
  // isolation and soft delete
  // ---------------------------------------------------------------------------------------------

  @Test
  void list_neverIncludesOtherClientsDocuments() throws Exception {
    Registered other = registerClient();
    seed("mine.txt", DocumentStatus.READY, 1);
    seed(other.clientId(), "theirs.txt", DocumentStatus.READY, BASE_TIME, false);

    list().andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].filename").value("mine.txt"));
  }

  @Test
  void list_eachClientSeesOnlyItsOwnDocumentsEvenWithSameName() throws Exception {
    Registered other = registerClient();
    DocumentRecord mine = seed("shared-name.txt", DocumentStatus.READY, 1);
    DocumentRecord theirs =
        seed(other.clientId(), "shared-name.txt", DocumentStatus.READY, BASE_TIME, false);

    list().andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].documentId").value(mine.getId().toString()));
    listAs(other.token())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].documentId").value(theirs.getId().toString()));
  }

  @Test
  void list_otherClientsDocumentsDoNotLeakThroughStatusFilter() throws Exception {
    Registered other = registerClient();
    seed(other.clientId(), "theirs.txt", DocumentStatus.READY, BASE_TIME, false);

    list("status", "ready").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void list_excludesSoftDeletedDocuments() throws Exception {
    seed("live.txt", DocumentStatus.READY, 1);
    seed(clientId, "deleted.txt", DocumentStatus.READY, BASE_TIME, true);

    list().andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].filename").value("live.txt"));
  }

  @Test
  void list_excludesSoftDeletedDocumentsFromStatusFilterToo() throws Exception {
    seed("live.txt", DocumentStatus.READY, 1);
    seed(clientId, "deleted.txt", DocumentStatus.READY, BASE_TIME, true);

    list("status", "ready")
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].filename").value("live.txt"));
  }

  @Test
  void list_replacedDocument_showsOnlyTheLatestUpload() throws Exception {
    DocumentRecord replacement = seed("notes.txt", DocumentStatus.PROCESSING, 10);
    seed(clientId, "notes.txt", DocumentStatus.READY, BASE_TIME, true);

    list().andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].documentId").value(replacement.getId().toString()));
  }

  @Test
  void list_clientWithOnlyDeletedDocuments_returnsEmptyArray() throws Exception {
    seed(clientId, "gone.txt", DocumentStatus.READY, BASE_TIME, true);

    list().andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
  }

  // ---------------------------------------------------------------------------------------------
  // status filter
  // ---------------------------------------------------------------------------------------------

  private void seedOneOfEachStatus() {
    seed("p.txt", DocumentStatus.PROCESSING, 1);
    seed("r1.txt", DocumentStatus.READY, 2);
    seed("r2.txt", DocumentStatus.READY, 3);
    seed("f.txt", DocumentStatus.FAILED, 4);
  }

  @Test
  void list_statusReady_returnsOnlyReadyDocuments() throws Exception {
    seedOneOfEachStatus();

    list("status", "ready")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].filename").value(containsInAnyOrder("r1.txt", "r2.txt")))
        .andExpect(jsonPath("$[*].status").value(containsInAnyOrder("ready", "ready")));
  }

  @Test
  void list_statusProcessing_returnsOnlyProcessingDocuments() throws Exception {
    seedOneOfEachStatus();

    list("status", "processing")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].filename").value("p.txt"));
  }

  @Test
  void list_statusFailed_returnsOnlyFailedDocuments() throws Exception {
    seedOneOfEachStatus();

    list("status", "failed")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].filename").value("f.txt"));
  }

  @Test
  void list_statusAll_returnsEveryStatus() throws Exception {
    seedOneOfEachStatus();

    list("status", "all")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(4)));
  }

  @Test
  void list_noStatusParam_defaultsToAll() throws Exception {
    seedOneOfEachStatus();

    list().andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(4)));
  }

  @Test
  void list_blankStatusParam_defaultsToAll() throws Exception {
    seedOneOfEachStatus();

    list("status", "").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(4)));
  }

  @Test
  void list_statusIsCaseInsensitive() throws Exception {
    seedOneOfEachStatus();

    list("status", "READY").andExpect(jsonPath("$", hasSize(2)));
    list("status", "Failed").andExpect(jsonPath("$", hasSize(1)));
    list("status", "ALL").andExpect(jsonPath("$", hasSize(4)));
  }

  @Test
  void list_statusWithNoMatches_returnsEmptyArray() throws Exception {
    seed("r.txt", DocumentStatus.READY, 1);

    list("status", "failed")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void list_unknownStatus_returns400WithReason() throws Exception {
    seedOneOfEachStatus();

    list("status", "done")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").isNotEmpty());
  }

  // ---------------------------------------------------------------------------------------------
  // pagination
  // ---------------------------------------------------------------------------------------------

  /** Seeds d1..d5, with d5 the newest, so the default order is d5, d4, d3, d2, d1. */
  private void seedFiveNumberedDocuments() {
    for (int i = 1; i <= 5; i++) {
      seed("d" + i + ".txt", DocumentStatus.READY, i);
    }
  }

  @Test
  void list_limit_returnsAtMostThatManyDocuments() throws Exception {
    seedFiveNumberedDocuments();

    list("limit", "2")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].filename").value(contains("d5.txt", "d4.txt")));
  }

  @Test
  void list_limitLargerThanTotal_returnsEverything() throws Exception {
    seedFiveNumberedDocuments();

    list("limit", "100").andExpect(jsonPath("$", hasSize(5)));
  }

  @Test
  void list_offset_skipsThatManyNewestDocuments() throws Exception {
    seedFiveNumberedDocuments();

    list("offset", "3")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].filename").value(contains("d2.txt", "d1.txt")));
  }

  @Test
  void list_limitAndOffset_returnTheRequestedWindow() throws Exception {
    seedFiveNumberedDocuments();

    list("limit", "2", "offset", "1")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].filename").value(contains("d4.txt", "d3.txt")));
  }

  @Test
  void list_offsetNotMultipleOfLimit_isHonouredExactly() throws Exception {
    seedFiveNumberedDocuments();

    list("limit", "3", "offset", "2")
        .andExpect(jsonPath("$[*].filename").value(contains("d3.txt", "d2.txt", "d1.txt")));
  }

  @Test
  void list_consecutivePages_coverEveryDocumentExactlyOnce() throws Exception {
    seedFiveNumberedDocuments();

    list("limit", "2", "offset", "0")
        .andExpect(jsonPath("$[*].filename").value(contains("d5.txt", "d4.txt")));
    list("limit", "2", "offset", "2")
        .andExpect(jsonPath("$[*].filename").value(contains("d3.txt", "d2.txt")));
    list("limit", "2", "offset", "4")
        .andExpect(jsonPath("$[*].filename").value(contains("d1.txt")));
  }

  @Test
  void list_offsetPastTheEnd_returnsEmptyArray() throws Exception {
    seedFiveNumberedDocuments();

    list("offset", "5").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    list("offset", "500").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void list_limitZeroOrNegative_isTreatedAsNoLimit() throws Exception {
    seedFiveNumberedDocuments();

    list("limit", "0").andExpect(jsonPath("$", hasSize(5)));
    list("limit", "-3").andExpect(jsonPath("$", hasSize(5)));
  }

  @Test
  void list_negativeOffset_isTreatedAsZero() throws Exception {
    seedFiveNumberedDocuments();

    list("limit", "2", "offset", "-4")
        .andExpect(jsonPath("$[*].filename").value(contains("d5.txt", "d4.txt")));
  }

  @Test
  void list_statusFilterAndPaginationCombine() throws Exception {
    seed("r1.txt", DocumentStatus.READY, 1);
    seed("f1.txt", DocumentStatus.FAILED, 2);
    seed("r2.txt", DocumentStatus.READY, 3);
    seed("r3.txt", DocumentStatus.READY, 4);
    seed("f2.txt", DocumentStatus.FAILED, 5);

    list("status", "ready", "limit", "2", "offset", "1")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].filename").value(contains("r2.txt", "r1.txt")));
  }

  @Test
  void list_paginationDoesNotCountDeletedDocuments() throws Exception {
    seed("a.txt", DocumentStatus.READY, 1);
    seed(
        clientId,
        "deleted.txt",
        DocumentStatus.READY,
        BASE_TIME.plus(2, ChronoUnit.MINUTES),
        true);
    seed("b.txt", DocumentStatus.READY, 3);

    list("limit", "1", "offset", "1")
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].filename").value("a.txt"));
  }

  @Test
  void list_nonNumericLimit_returns400() throws Exception {
    list("limit", "abc")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").isNotEmpty());
  }

  @Test
  void list_nonNumericOffset_returns400() throws Exception {
    list("offset", "abc")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").isNotEmpty());
  }
}
