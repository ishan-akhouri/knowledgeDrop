package dev.coms4156.knowledgedrop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.coms4156.knowledgedrop.retrieval.RankedChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for splitting a document's chunks into groups that fit the context budget. */
class SummarizationServiceGroupingTest {

  private static final UUID DOC = UUID.randomUUID();

  @Test
  void groupChunks_everythingFits_returnsOneGroupInOrder() {
    List<RankedChunk> chunks = List.of(chunk(0, "aaaa"), chunk(1, "bbbb"), chunk(2, "cccc"));

    List<List<RankedChunk>> groups = SummarizationService.groupChunks(chunks, 100);

    assertThat(groups).containsExactly(chunks);
  }

  @Test
  void groupChunks_countsSeparatorTowardTheLimit() {
    // "aaaa" + "\n\n" + "bbbb" is 10 chars, so a 9-char limit must split them.
    List<RankedChunk> chunks = List.of(chunk(0, "aaaa"), chunk(1, "bbbb"));

    assertThat(SummarizationService.groupChunks(chunks, 9)).hasSize(2);
    assertThat(SummarizationService.groupChunks(chunks, 10)).hasSize(1);
  }

  @Test
  void groupChunks_eachGroupContextFitsAndOrderIsKept() {
    List<RankedChunk> chunks = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      chunks.add(chunk(i, "x".repeat(30)));
    }

    List<List<RankedChunk>> groups = SummarizationService.groupChunks(chunks, 100);

    assertThat(groups).allSatisfy(
        group ->
            assertThat(SummarizationService.toContext(group).length()).isLessThanOrEqualTo(100));
    assertThat(groups.stream().flatMap(List::stream).toList()).containsExactlyElementsOf(chunks);
  }

  @Test
  void groupChunks_oversizedChunk_getsItsOwnGroupUncut() {
    RankedChunk big = chunk(1, "y".repeat(50));
    List<RankedChunk> chunks = List.of(chunk(0, "aa"), big, chunk(2, "bb"));

    List<List<RankedChunk>> groups = SummarizationService.groupChunks(chunks, 10);

    assertThat(groups).hasSize(3);
    assertThat(groups.get(1)).containsExactly(big);
  }

  @Test
  void groupChunks_skipsBlankChunks() {
    List<RankedChunk> chunks = List.of(chunk(0, "aaaa"), chunk(1, "   "), chunk(2, null));

    List<List<RankedChunk>> groups = SummarizationService.groupChunks(chunks, 100);

    assertThat(groups).hasSize(1);
    assertThat(groups.get(0)).extracting(RankedChunk::text).containsExactly("aaaa");
  }

  @Test
  void groupChunks_noText_returnsNoGroups() {
    assertThat(SummarizationService.groupChunks(List.of(), 100)).isEmpty();
    assertThat(SummarizationService.groupChunks(List.of(chunk(0, " ")), 100)).isEmpty();
  }

  @Test
  void groupChunks_defaultBudgetIsMaxContextChars() {
    String text = "z".repeat(1000);
    List<RankedChunk> chunks = new ArrayList<>();
    for (int i = 0; i < 250; i++) {
      chunks.add(chunk(i, text));
    }

    List<List<RankedChunk>> groups = SummarizationService.groupChunks(chunks);

    assertThat(groups).hasSizeGreaterThan(1);
    assertThat(groups).allSatisfy(
        group ->
            assertThat(SummarizationService.toContext(group).length())
                .isLessThanOrEqualTo(SummarizationService.MAX_CONTEXT_CHARS));
  }

  @Test
  void groupChunks_nonPositiveLimit_isRejected() {
    assertThatThrownBy(() -> SummarizationService.groupChunks(List.of(), 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static RankedChunk chunk(int position, String text) {
    return new RankedChunk(DOC + "-" + position, DOC, 0, text, 0, 0);
  }
}
