package dev.coms4156.knowledgedrop.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * End-to-end tests (through the real servlet stack, MockMvc, and an in-memory DB) for the pieces
 * that are built: client registration, token authentication, and request binding/validation.
 * Endpoints that are still TODO are only checked for "not rejected before reaching the service".
 */
@SpringBootTest
@ActiveProfiles("test")
class ApiAuthIntegrationTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  private static String uniqueName() {
    return "client-" + UUID.randomUUID();
  }

  private MvcResult register(String name) throws Exception {
    return mockMvc
        .perform(
            post("/knowledgeDrop/registerClient")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientName\":\"" + name + "\"}"))
        .andReturn();
  }

  private String registerAndGetToken() throws Exception {
    MvcResult result = register(uniqueName());
    return JsonPath.read(result.getResponse().getContentAsString(), "$.clientToken");
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  // ------------------------------------------------------------ registerClient

  @Test
  void registerClient_returnsIdAndToken() throws Exception {
    mockMvc
        .perform(
            post("/knowledgeDrop/registerClient")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientName\":\"" + uniqueName() + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.clientId").isNotEmpty())
        .andExpect(jsonPath("$.clientToken").isNotEmpty());
  }

  @Test
  void registerClient_duplicateName_returns409WithReason() throws Exception {
    String name = uniqueName();
    register(name);

    mockMvc
        .perform(
            post("/knowledgeDrop/registerClient")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientName\":\"" + name + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.reason").isNotEmpty());
  }

  @Test
  void registerClient_blankName_returns400() throws Exception {
    mockMvc
        .perform(
            post("/knowledgeDrop/registerClient")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientName\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").isNotEmpty());
  }

  @Test
  void registerClient_malformedBody_returns400() throws Exception {
    mockMvc
        .perform(
            post("/knowledgeDrop/registerClient")
                .contentType(MediaType.APPLICATION_JSON)
                .content("this is not json"))
        .andExpect(status().isBadRequest());
  }

  // ------------------------------------------------------------ authentication

  @Test
  void protectedEndpoint_missingToken_returns401() throws Exception {
    mockMvc
        .perform(get("/knowledgeDrop/list"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.reason").isNotEmpty());
  }

  @Test
  void protectedEndpoint_invalidToken_returns401() throws Exception {
    mockMvc
        .perform(get("/knowledgeDrop/list").header(HttpHeaders.AUTHORIZATION, bearer("nope")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void protectedEndpoint_wrongAuthScheme_returns401() throws Exception {
    mockMvc
        .perform(get("/knowledgeDrop/list").header(HttpHeaders.AUTHORIZATION, "Basic abc123"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void protectedEndpoint_validToken_isNotRejectedByAuth() throws Exception {
    String token = registerAndGetToken();

    mockMvc
        .perform(get("/knowledgeDrop/list").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(result -> assertNotEquals(401, result.getResponse().getStatus()));
  }

  @Test
  void health_requiresNoToken() throws Exception {
    mockMvc
        .perform(get("/knowledgeDrop/health"))
        .andExpect(result -> assertNotEquals(401, result.getResponse().getStatus()));
  }

  // ------------------------------------------------------------ request validation

  @Test
  void read_withoutIdOrName_returns400() throws Exception {
    String token = registerAndGetToken();

    mockMvc
        .perform(get("/knowledgeDrop/read").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").isNotEmpty());
  }

  @Test
  void delete_malformedDocumentId_returns400() throws Exception {
    String token = registerAndGetToken();

    mockMvc
        .perform(
            delete("/knowledgeDrop/delete")
                .param("documentId", "not-a-uuid")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void ask_blankQuestion_returns400() throws Exception {
    String token = registerAndGetToken();

    mockMvc
        .perform(
            post("/knowledgeDrop/ask")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  // ------------------------------------------------------------ create / update request binding

  private static MockMultipartFile metadataPart(String json) {
    return new MockMultipartFile(
        "metadata",
        "metadata.json",
        MediaType.APPLICATION_JSON_VALUE,
        json.getBytes(StandardCharsets.UTF_8));
  }

  private static MockMultipartFile binaryFilePart(String content) {
    return new MockMultipartFile(
        "file",
        "notes.txt",
        MediaType.APPLICATION_OCTET_STREAM_VALUE,
        content.getBytes(StandardCharsets.UTF_8));
  }

  /** Passes once the request got past auth and binding (a TODO stub may still answer 501). */
  private static void assertNotRejectedBeforeService(MvcResult result) {
    int actual = result.getResponse().getStatus();
    assertFalse(
        List.of(400, 401, 415).contains(actual),
        "Request was rejected before reaching the service: HTTP " + actual);
  }

  @Test
  void create_jsonMetadataAndBinaryFile_reachesTheService() throws Exception {
    String token = registerAndGetToken();

    mockMvc
        .perform(
            multipart("/knowledgeDrop/create")
                .file(metadataPart("{\"name\":\"notes.txt\",\"type\":\"text\"}"))
                .file(binaryFilePart("hello world"))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(ApiAuthIntegrationTest::assertNotRejectedBeforeService);
  }

  @Test
  void create_rawTextInMetadataWithoutFile_reachesTheService() throws Exception {
    String token = registerAndGetToken();

    mockMvc
        .perform(
            multipart("/knowledgeDrop/create")
                .file(metadataPart("{\"name\":\"n\",\"type\":\"text\",\"text\":\"hi\"}"))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(ApiAuthIntegrationTest::assertNotRejectedBeforeService);
  }

  @Test
  void create_blankName_returns400() throws Exception {
    String token = registerAndGetToken();

    mockMvc
        .perform(
            multipart("/knowledgeDrop/create")
                .file(metadataPart("{\"name\":\"\",\"type\":\"text\"}"))
                .file(binaryFilePart("hello"))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").isNotEmpty());
  }

  @Test
  void create_missingMetadataPart_returns400() throws Exception {
    String token = registerAndGetToken();

    mockMvc
        .perform(
            multipart("/knowledgeDrop/create")
                .file(binaryFilePart("hello"))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void create_withoutToken_returns401() throws Exception {
    mockMvc
        .perform(
            multipart("/knowledgeDrop/create")
                .file(metadataPart("{\"name\":\"a\",\"type\":\"text\"}"))
                .file(binaryFilePart("hello")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void update_documentIdInMetadataAndBinaryFile_reachesTheService() throws Exception {
    String token = registerAndGetToken();
    String metadata = "{\"documentId\":\"" + UUID.randomUUID() + "\"}";

    mockMvc
        .perform(
            multipart(HttpMethod.PUT, "/knowledgeDrop/update")
                .file(metadataPart(metadata))
                .file(binaryFilePart("new content"))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(ApiAuthIntegrationTest::assertNotRejectedBeforeService);
  }

  @Test
  void update_withoutIdOrName_returns400() throws Exception {
    String token = registerAndGetToken();

    mockMvc
        .perform(
            multipart(HttpMethod.PUT, "/knowledgeDrop/update")
                .file(metadataPart("{\"text\":\"new content\"}"))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").isNotEmpty());
  }
}
