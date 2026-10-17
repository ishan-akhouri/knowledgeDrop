package dev.coms4156.knowledgedrop.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.repository.ClientRepository;
import dev.coms4156.knowledgedrop.security.TokenUtils;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * End-to-end tests (through the real servlet stack, MockMvc, and an in-memory DB) for the pieces
 * that are built: client registration, token authentication, and request binding/validation.
 * Endpoints that are still TODO are only checked for "not rejected before reaching the service".
 */
@SpringBootTest
@ActiveProfiles("test")
class ApiAuthIntegrationTest {

  @Autowired private WebApplicationContext context;

  @Autowired private ClientRepository clientRepository;

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
        .perform(post("/knowledgeDrop/client/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"firstName\":\"" + name + "\",\"lastName\":\"Last\"}"))
        .andExpect(status().isCreated())
        .andReturn();
  }

  private String registerAndGetToken() throws Exception {
    MvcResult result = register(uniqueName());
    return JsonPath.read(result.getResponse().getContentAsString(), "$.clientToken");
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  @Test
  void registerClient_returnsCredentialsAndPersistsRecord() throws Exception {
    MvcResult result = mockMvc
        .perform(post("/knowledgeDrop/client/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"firstName\":\" Ada \",\"lastName\":\" Lovelace \"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.clientId").isNotEmpty())
        .andExpect(jsonPath("$.clientToken").isNotEmpty())
        .andExpect(jsonPath("$.tokenHash").doesNotExist())
        .andReturn();
    String body = result.getResponse().getContentAsString();
    UUID id = UUID.fromString(JsonPath.read(body, "$.clientId"));
    final String token = JsonPath.read(body, "$.clientToken");
    ClientRecord stored = clientRepository.findById(id).orElseThrow();
    assertEquals("Ada", stored.getFirstName());
    assertEquals("Lovelace", stored.getLastName());
    assertTrue(stored.isActive());
    assertNotNull(stored.getCreatedAt());
    assertEquals(TokenUtils.hash(token), stored.getTokenHash());
    assertNotEquals(token, stored.getTokenHash());
  }

  @Test
  void registerClient_sameNames_generateDistinctCredentials() throws Exception {
    String first = register("Ada").getResponse().getContentAsString();
    String second = register("Ada").getResponse().getContentAsString();
    assertNotEquals((String) JsonPath.read(first, "$.clientId"),
        (String) JsonPath.read(second, "$.clientId"));
    assertNotEquals((String) JsonPath.read(first, "$.clientToken"),
        (String) JsonPath.read(second, "$.clientToken"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"firstName", "lastName"})
  void registerClient_missingOrInvalidField_returns400(String field) throws Exception {
    String other = field.equals("firstName") ? "lastName" : "firstName";
    long count = clientRepository.count();
    mockMvc.perform(post("/knowledgeDrop/client/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"" + other + "\":\"Valid\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").isNotEmpty());
    for (String invalid : List.of("null", "\"\"", "\"  \"", "\"" + "a".repeat(256) + "\"")) {
      mockMvc.perform(post("/knowledgeDrop/client/register").contentType(MediaType.APPLICATION_JSON)
              .content("{\"" + other + "\":\"Valid\",\"" + field + "\":" + invalid + "}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.reason").isNotEmpty());
    }
    assertEquals(count, clientRepository.count());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "null", "not json", "{}"})
  void registerClient_missingOrMalformedBody_returns400(String body) throws Exception {
    mockMvc.perform(post("/knowledgeDrop/client/register").contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").isNotEmpty());
  }

  @Test
  void registerClient_maximumLengthNames_succeed() throws Exception {
    mockMvc.perform(post("/knowledgeDrop/client/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"firstName\":\"" + "a".repeat(255)
                + "\",\"lastName\":\"" + "b".repeat(255) + "\"}"))
        .andExpect(status().isCreated());
  }

  // ------------------------------------------------------------ authentication

  @Test
  void clientDrop_requiresAuthenticationAndReachesService()
      throws Exception {
    mockMvc.perform(delete("/knowledgeDrop/client/drop")
            .param("clientId", UUID.randomUUID().toString()))
        .andExpect(status().isUnauthorized());
    String token = registerAndGetToken();
    mockMvc.perform(delete("/knowledgeDrop/client/drop")
            .param("clientId", UUID.randomUUID().toString())
            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isNotImplemented());
  }

  @Test
  void protectedEndpoint_missingToken_returns401() throws Exception {
    mockMvc
        .perform(get("/knowledgeDrop/document/list"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.reason").isNotEmpty());
  }

  @Test
  void protectedEndpoint_invalidToken_returns401() throws Exception {
    mockMvc
        .perform(
            get("/knowledgeDrop/document/list")
                .header(HttpHeaders.AUTHORIZATION, bearer("nope")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void protectedEndpoint_wrongAuthScheme_returns401() throws Exception {
    mockMvc
        .perform(
            get("/knowledgeDrop/document/list")
                .header(HttpHeaders.AUTHORIZATION, "Basic abc123"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void protectedEndpoint_validToken_isNotRejectedByAuth() throws Exception {
    String token = registerAndGetToken();

    mockMvc
        .perform(
            get("/knowledgeDrop/document/list")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
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
  void read_withoutName_returns400() throws Exception {
    String token = registerAndGetToken();

    mockMvc
        .perform(
            get("/knowledgeDrop/document/read")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").isNotEmpty());
  }

  @Test
  void delete_blankName_returns400() throws Exception {
    String token = registerAndGetToken();

    mockMvc
        .perform(
            delete("/knowledgeDrop/document/delete")
                .param("documentName", "  ")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").isNotEmpty());
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

  // ------------------------------------------------------------ create request binding

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
  void create_binaryFile_reachesTheService() throws Exception {
    String token = registerAndGetToken();

    mockMvc
        .perform(
            multipart("/knowledgeDrop/document/create")
                .file(binaryFilePart("hello world"))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(ApiAuthIntegrationTest::assertNotRejectedBeforeService);
  }

  @Test
  void create_missingFilePart_returns400() throws Exception {
    String token = registerAndGetToken();

    mockMvc
        .perform(
            multipart("/knowledgeDrop/document/create")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void create_withoutToken_returns401() throws Exception {
    mockMvc
        .perform(
            multipart("/knowledgeDrop/document/create").file(binaryFilePart("hello")))
        .andExpect(status().isUnauthorized());
  }
}
