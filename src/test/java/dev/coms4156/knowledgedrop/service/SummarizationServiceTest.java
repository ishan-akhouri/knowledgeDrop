package dev.coms4156.knowledgedrop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.coms4156.knowledgedrop.api.dto.SummarizeRequest;
import dev.coms4156.knowledgedrop.api.dto.SummarizeResponse;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.model.SummaryLength;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import dev.coms4156.knowledgedrop.retrieval.KeywordIndex;
import dev.coms4156.knowledgedrop.retrieval.LlmClient;
import dev.coms4156.knowledgedrop.retrieval.RankedChunk;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit tests for summarize with mocked storage, index and LLM. */
class SummarizationServiceTest {

  private static final UUID CLIENT_ID = UUID.randomUUID();
  private static final UUID DOC_ID = UUID.randomUUID();
  private static final String HASH = "a".repeat(40);

  private final LlmClient llm = mock(LlmClient.class);
  private final DocumentRepository documents = mock(DocumentRepository.class);
  private final KeywordIndex index = mock(KeywordIndex.class);
  private final SummarizationService service = new SummarizationService(llm, documents, index);
  private ClientRecord client;
  private DocumentRecord document;

  @BeforeEach
  void setUp() {
    client = new ClientRecord("client", "hash");
    ReflectionTestUtils.setField(client, "id", CLIENT_ID);
    document = new DocumentRecord(CLIENT_ID, "doc.txt", "text/plain", 10, HASH);
    ReflectionTestUtils.setField(document, "id", DOC_ID);
    document.markReady();
    when(documents.findByIdAndOwnerId(DOC_ID, CLIENT_ID)).thenReturn(Optional.of(document));
  }

  @Test
  void summarize_smallDocument_usesOneCallWithLengthPrompt() {
    when(index.chunksInOrder(DOC_ID)).thenReturn(List.of(chunk(0, "first"), chunk(1, "second")));
    when(llm.complete(anyString(), anyString())).thenReturn("summary");

    SummarizeResponse response =
        service.summarize(client, new SummarizeRequest(DOC_ID, SummaryLength.DETAILED));

    assertThat(response.summary()).isEqualTo("summary");
    assertThat(response.documentId()).isEqualTo(DOC_ID);
    assertThat(response.chunksUsed()).isEqualTo(2);
    verify(llm)
        .complete(
            SummarizationService.systemPrompt(SummaryLength.DETAILED),
            "first" + SummarizationService.CONTEXT_SEPARATOR + "second");
    verify(llm, times(1)).complete(anyString(), anyString());
  }

  @Test
  void summarize_largeDocument_mapsEachGroupThenReduces() {
    // Each chunk is over half the budget, so every chunk lands in its own group.
    int size = SummarizationService.MAX_CONTEXT_CHARS / 2 + 1;
    String a = "a".repeat(size);
    String b = "b".repeat(size);
    String c = "c".repeat(size);
    when(index.chunksInOrder(DOC_ID)).thenReturn(List.of(chunk(0, a), chunk(1, b), chunk(2, c)));
    when(llm.complete(SummarizationService.MAP_SYSTEM_PROMPT, a)).thenReturn("pa");
    when(llm.complete(SummarizationService.MAP_SYSTEM_PROMPT, b)).thenReturn("pb");
    when(llm.complete(SummarizationService.MAP_SYSTEM_PROMPT, c)).thenReturn("pc");
    String sep = SummarizationService.CONTEXT_SEPARATOR;
    String reduceContext = "pa" + sep + "pb" + sep + "pc";
    when(llm.complete(SummarizationService.reducePrompt(null), reduceContext)).thenReturn("final");

    SummarizeResponse response = service.summarize(client, new SummarizeRequest(DOC_ID, null));

    assertThat(response.summary()).isEqualTo("final");
    assertThat(response.chunksUsed()).isEqualTo(3);
    InOrder order = inOrder(llm);
    order.verify(llm).complete(SummarizationService.MAP_SYSTEM_PROMPT, a);
    order.verify(llm).complete(SummarizationService.MAP_SYSTEM_PROMPT, b);
    order.verify(llm).complete(SummarizationService.MAP_SYSTEM_PROMPT, c);
    order.verify(llm).complete(SummarizationService.reducePrompt(null), reduceContext);
    verify(llm, times(4)).complete(anyString(), anyString());
  }

  @Test
  void summarize_partialsTooLongForOneCall_combinesThemInRounds() {
    int size = SummarizationService.MAX_CONTEXT_CHARS / 2 + 1;
    List<RankedChunk> chunks =
        List.of(
            chunk(0, "a".repeat(size)),
            chunk(1, "b".repeat(size)),
            chunk(2, "c".repeat(size)));
    when(index.chunksInOrder(DOC_ID)).thenReturn(chunks);
    // Three partials of a third of the budget are too long together, but two fit in one call:
    // the intermediate round combines [p, p] and [p], then the final reduce gets two summaries.
    String sep = SummarizationService.CONTEXT_SEPARATOR;
    String partial = "p".repeat(SummarizationService.MAX_CONTEXT_CHARS / 3 + 1);
    when(llm.complete(eq(SummarizationService.MAP_SYSTEM_PROMPT), anyString()))
        .thenReturn(partial);
    String detailedReduce = SummarizationService.reducePrompt(SummaryLength.DETAILED);
    String shortReduce = SummarizationService.reducePrompt(SummaryLength.SHORT);
    when(llm.complete(eq(detailedReduce), anyString())).thenReturn("combined");
    when(llm.complete(eq(shortReduce), anyString())).thenReturn("final");

    SummarizeResponse response =
        service.summarize(client, new SummarizeRequest(DOC_ID, SummaryLength.SHORT));

    assertThat(response.summary()).isEqualTo("final");
    verify(llm, times(3)).complete(eq(SummarizationService.MAP_SYSTEM_PROMPT), anyString());
    verify(llm).complete(detailedReduce, partial + sep + partial);
    verify(llm).complete(detailedReduce, partial);
    verify(llm).complete(shortReduce, "combined" + sep + "combined");
  }

  @Test
  void summarize_partialsThatCannotBeGroupedFurther_goStraightToFinalReduce() {
    int size = SummarizationService.MAX_CONTEXT_CHARS / 2 + 1;
    when(index.chunksInOrder(DOC_ID))
        .thenReturn(List.of(chunk(0, "a".repeat(size)), chunk(1, "b".repeat(size))));
    // Each partial is over half the budget, so grouping cannot shrink them; the loop must stop.
    String partial = "p".repeat(size);
    when(llm.complete(eq(SummarizationService.MAP_SYSTEM_PROMPT), anyString()))
        .thenReturn(partial);
    String finalReduce = SummarizationService.reducePrompt(null);
    when(llm.complete(eq(finalReduce), anyString())).thenReturn("final");

    SummarizeResponse response = service.summarize(client, new SummarizeRequest(DOC_ID, null));

    assertThat(response.summary()).isEqualTo("final");
    verify(llm, never())
        .complete(eq(SummarizationService.reducePrompt(SummaryLength.DETAILED)), anyString());
    verify(llm).complete(finalReduce, partial + SummarizationService.CONTEXT_SEPARATOR + partial);
  }

  @Test
  void summarize_documentOfAnotherClient_isNotFoundWithoutCallingLlm() {
    when(documents.findByIdAndOwnerId(DOC_ID, CLIENT_ID)).thenReturn(Optional.empty());

    assertStatus(new SummarizeRequest(DOC_ID, null), HttpStatus.NOT_FOUND);
    verify(llm, never()).complete(anyString(), anyString());
  }

  @Test
  void summarize_deletedDocument_isNotFound() {
    document.setDeletedAt(java.time.Instant.now());

    assertStatus(new SummarizeRequest(DOC_ID, null), HttpStatus.NOT_FOUND);
  }

  @Test
  void summarize_documentStillProcessing_isConflict() {
    document.markProcessing();

    assertStatus(new SummarizeRequest(DOC_ID, null), HttpStatus.CONFLICT);
    verify(index, never()).chunksInOrder(DOC_ID);
  }

  @Test
  void summarize_failedDocument_isConflictWithReason() {
    document.markFailed("bad pdf");

    assertThatThrownBy(() -> service.summarize(client, new SummarizeRequest(DOC_ID, null)))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT))
        .hasMessageContaining("bad pdf");
  }

  @Test
  void summarize_noIndexedChunks_isInternalError() {
    when(index.chunksInOrder(DOC_ID)).thenReturn(List.of());

    assertStatus(new SummarizeRequest(DOC_ID, null), HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  void summarize_onlyBlankChunks_isInternalError() {
    when(index.chunksInOrder(DOC_ID)).thenReturn(List.of(chunk(0, "  ")));

    assertStatus(new SummarizeRequest(DOC_ID, null), HttpStatus.INTERNAL_SERVER_ERROR);
    verify(llm, never()).complete(anyString(), anyString());
  }

  @Test
  void summarize_missingInputs_areRejected() {
    assertThatThrownBy(() -> service.summarize(null, new SummarizeRequest(DOC_ID, null)))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    assertStatus(null, HttpStatus.BAD_REQUEST);
    assertStatus(new SummarizeRequest(null, null), HttpStatus.BAD_REQUEST);
  }

  private void assertStatus(SummarizeRequest request, HttpStatus status) {
    assertThatThrownBy(() -> service.summarize(client, request))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(status));
  }

  private static RankedChunk chunk(int position, String text) {
    return new RankedChunk(DOC_ID + "-" + position, DOC_ID, 0, text, 0, 0);
  }
}
