package dev.coms4156.knowledgedrop.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.jayway.jsonpath.JsonPath;
import dev.coms4156.knowledgedrop.config.KnowledgeDropProperties;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.model.DocumentStatus;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import dev.coms4156.knowledgedrop.retrieval.EmbeddingClient;
import dev.coms4156.knowledgedrop.service.IngestionService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Runs summarize for real, end to end: upload through the API, real ingestion (Tika, the embedding
 * service, Elasticsearch), then {@code POST /knowledgeDrop/summarize} against the real Gemini API.
 *
 * <p>Skipped unless an LLM key is configured ({@code LLM_API_KEY} in {@code .env} or the
 * environment), so CI and anyone without a key are not affected. Fails with a clear message if the
 * key is set but Elasticsearch or the embedding service is down. To run it:
 *
 * <pre>
 * docker compose up -d
 * ./mvnw -Dtest=SummarizeLiveTest test
 * </pre>
 *
 * <p>Makes about 5 Gemini calls in total, small enough for the free tier.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("live")
class SummarizeLiveTest {

  private static final Duration INGEST_TIMEOUT = Duration.ofMinutes(3);
  private static final String SUMMARIZE_URL = "/knowledgeDrop/summarize";

  /** A short document with distinctive, made-up facts the summary should mention. */
  private static final String SHORT_DOCUMENT =
      "Annual report of the Zephyrine Lake Otter Sanctuary.\n\n"
          + "The Zephyrine Lake Otter Sanctuary was founded in 1987 by marine biologist Ilsa "
          + "Varnholt to protect the lake's declining river otter population. In its first year "
          + "the sanctuary counted only 14 otters. After a decade of wetland restoration and a ban "
          + "on gill nets, the population recovered to 212 otters by 2024.\n\n"
          + "The sanctuary's main programs are wetland restoration, a volunteer water-quality "
          + "monitoring network of 300 residents, and a school outreach program that reached "
          + "9,000 students last year. Its budget of 1.2 million dollars comes mostly from "
          + "private donations.\n\n"
          + "The biggest current threat is agricultural runoff, which raised nitrate levels by "
          + "18 percent since 2019. The board plans to buy 40 hectares of buffer land along the "
          + "northern shore by 2027 to filter runoff before it reaches the lake.";

  @Autowired private WebApplicationContext context;
  @Autowired private KnowledgeDropProperties properties;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private IngestionService ingestionService;
  @Autowired private ElasticsearchClient elasticsearch;
  @Autowired private EmbeddingClient embeddings;

  private MockMvc mockMvc;
  private String token;
  private final List<UUID> uploaded = new ArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    String apiKey = properties.llm().apiKey();
    assumeTrue(
        apiKey != null && !apiKey.isBlank(),
        "LLM_API_KEY is not set (in .env or the environment), skipping live summarize tests");
    requireServicesRunning();
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    token = registerClient();
  }

  @AfterEach
  void tearDown() {
    for (UUID documentId : uploaded) {
      ingestionService.deleteChunks(documentId);
    }
  }

  @Test
  void smallDocument_shortAndDetailedSummariesCoverTheFacts() throws Exception {
    UUID documentId = uploadAndWaitUntilReady("otters.txt", SHORT_DOCUMENT);

    String shortBody = summarize(documentId, "short");
    String shortSummary = JsonPath.read(shortBody, "$.summary");
    int chunksUsed = JsonPath.read(shortBody, "$.chunksUsed");
    assertThat((String) JsonPath.read(shortBody, "$.documentId"))
        .isEqualTo(documentId.toString());
    assertThat(chunksUsed).isPositive();
    assertThat(shortSummary).containsIgnoringCase("otter").containsIgnoringCase("Zephyrine");
    assertThat(shortSummary).doesNotStartWithIgnoringCase("here is");

    String detailedSummary = JsonPath.read(summarize(documentId, "detailed"), "$.summary");
    assertThat(detailedSummary).containsIgnoringCase("otter");
    assertThat(detailedSummary.length())
        .as("a detailed summary should be longer than a short one")
        .isGreaterThan(shortSummary.length());
  }

  @Test
  void longDocument_mapReduceCoversTheBeginningAndTheEnd() throws Exception {
    // Over the 100k-character budget, so summarize must split it into groups (map) and combine
    // the partial summaries (reduce). The opening and closing topics sit in different groups, so
    // both showing up in the final summary means every group made it through.
    String document = longDocument();
    assertThat(document.length()).isGreaterThan(110_000);
    UUID documentId = uploadAndWaitUntilReady("history.txt", document);

    String body = summarize(documentId, "short");
    String summary = JsonPath.read(body, "$.summary");
    int chunksUsed = JsonPath.read(body, "$.chunksUsed");

    assertThat(chunksUsed).isGreaterThan(125);
    assertThat(summary).containsIgnoringCase("observatory");
    assertThat(summary).containsIgnoringCase("harbor");
  }

  @Test
  void documentOfAnotherClient_isNotFound() throws Exception {
    UUID documentId = uploadAndWaitUntilReady("private.txt", SHORT_DOCUMENT);
    String otherToken = registerClient();

    mockMvc
        .perform(
            post(SUMMARIZE_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentId\":\"" + documentId + "\"}"))
        .andExpect(status().isNotFound());
  }

  // ---------------------------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------------------------

  private void requireServicesRunning() {
    try {
      if (!elasticsearch.ping().value()) {
        fail("Elasticsearch is not running. Start it with: docker compose up -d");
      }
    } catch (Exception e) {
      fail("Elasticsearch is not running. Start it with: docker compose up -d", e);
    }
    try {
      embeddings.embed("ping");
    } catch (RuntimeException e) {
      fail("Embedding service is not running. Start it with: docker compose up -d", e);
    }
  }

  private String registerClient() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/knowledgeDrop/client/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"live-" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.clientToken");
  }

  /** Uploads a text file through the API and waits for background ingestion to finish. */
  private UUID uploadAndWaitUntilReady(String fileName, String text) throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", fileName, MediaType.TEXT_PLAIN_VALUE, text.getBytes(StandardCharsets.UTF_8));
    MvcResult result =
        mockMvc
            .perform(
                multipart("/knowledgeDrop/document/create")
                    .file(file)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    UUID documentId =
        UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.documentId"));
    uploaded.add(documentId);

    Instant deadline = Instant.now().plus(INGEST_TIMEOUT);
    while (Instant.now().isBefore(deadline)) {
      DocumentRecord record = documentRepository.findById(documentId).orElseThrow();
      if (record.getStatus() == DocumentStatus.READY) {
        return documentId;
      }
      if (record.getStatus() == DocumentStatus.FAILED) {
        fail("Ingestion failed: " + record.getFailureReason());
      }
      Thread.sleep(500);
    }
    return fail("Document was not ready within " + INGEST_TIMEOUT);
  }

  /** Calls summarize as the test client and returns the JSON body; fails on a non-200 answer. */
  private String summarize(UUID documentId, String length) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post(SUMMARIZE_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"documentId\":\"" + documentId + "\",\"length\":\"" + length + "\"}"))
            .andReturn();
    String body = result.getResponse().getContentAsString();
    assertThat(result.getResponse().getStatus()).as("summarize response: %s", body).isEqualTo(200);
    return body;
  }

  /**
   * Builds a long document: a section about an observatory, many varied middle sections about a
   * town's history, and a closing section about a harbor.
   */
  private static String longDocument() {
    StringBuilder text = new StringBuilder();
    text.append("Part one: the Aurora Ridge Observatory.\n\n");
    for (int i = 0; i < 20; i++) {
      text.append("The Aurora Ridge Observatory, built in 1902 on a granite ridge, houses a ")
          .append("refracting telescope with a lens of ")
          .append(60 + i)
          .append(" centimeters. Astronomers there catalogued comet number ")
          .append(1000 + i)
          .append(" and measured the brightness of variable stars over many winters.\n");
    }
    String[] trades = {"weaving", "milling", "brewing", "printing", "shipbuilding", "masonry"};
    for (int year = 1830; year < 2000; year++) {
      String trade = trades[year % trades.length];
      text.append("\nIn ")
          .append(year)
          .append(", the town council recorded that the ")
          .append(trade)
          .append(" guild had ")
          .append(year % 97 + 20)
          .append(" members. The market square hosted a fair in the month of ")
          .append(year % 12 + 1)
          .append(", and the council debated repairs to the river bridge, the cost of oil ")
          .append("lamps for the main street, and a petition from farmers about grain prices. ")
          .append("Records from that year also mention a census of ")
          .append(year * 3 % 5000 + 2000)
          .append(" residents and new rules for the weekly market. The ")
          .append(trade)
          .append(" workshops expanded along the eastern road, and the school added a room ")
          .append("for older pupils. Letters from the period describe floods in spring, a ")
          .append("good harvest in autumn, and long arguments about whether to pave the lanes ")
          .append("near the church with cobblestones or leave them as packed earth.\n");
    }
    text.append("\nFinal part: the Basalt Point Harbor.\n\n");
    for (int i = 0; i < 20; i++) {
      text.append("The Basalt Point Harbor was rebuilt in 1995 with a breakwater of ")
          .append(400 + i * 10)
          .append(" meters, and it now handles fishing boats, ferries, and a marina of ")
          .append(150 + i)
          .append(" berths. The harbor authority plans a new ferry terminal for 2030.\n");
    }
    return text.toString();
  }
}
