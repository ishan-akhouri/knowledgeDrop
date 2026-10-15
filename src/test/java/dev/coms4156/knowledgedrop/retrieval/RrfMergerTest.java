package dev.coms4156.knowledgedrop.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link RrfMerger}. */
class RrfMergerTest {

  private static final double TOLERANCE = 1e-9;
  private static final UUID DOC = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private final RrfMerger merger = new RrfMerger(60);

  /** Builds a ranked list from chunk IDs, best first, with ranks 1 to n. */
  private static List<RankedChunk> list(String... ids) {
    List<RankedChunk> chunks = new ArrayList<>();
    for (int i = 0; i < ids.length; i++) {
      chunks.add(new RankedChunk(ids[i], DOC, 1, "text of " + ids[i], i + 1, 0.0));
    }
    return chunks;
  }

  private static List<String> ids(List<RankedChunk> chunks) {
    return chunks.stream().map(RankedChunk::chunkId).toList();
  }

  @Test
  void merge_singleList_keepsOrderAndScoresByRank() {
    List<RankedChunk> result = merger.merge(List.of(list("a", "b", "c")), 10);

    assertThat(ids(result)).containsExactly("a", "b", "c");
    assertThat(result.get(0).score()).isCloseTo(1.0 / 61, within(TOLERANCE));
    assertThat(result.get(1).score()).isCloseTo(1.0 / 62, within(TOLERANCE));
    assertThat(result.get(2).score()).isCloseTo(1.0 / 63, within(TOLERANCE));
  }

  @Test
  void merge_chunkInBothLists_outranksChunksInOnlyOne() {
    List<RankedChunk> result = merger.merge(List.of(list("a", "b"), list("c", "b")), 10);

    assertThat(ids(result)).containsExactly("b", "a", "c");
  }

  @Test
  void merge_handComputedExample_sumsReciprocalsAcrossLists() {
    List<RankedChunk> result =
        merger.merge(List.of(list("x", "y", "z"), list("p", "q", "x")), 10);

    assertThat(result.get(0).chunkId()).isEqualTo("x");
    assertThat(result.get(0).score()).isCloseTo(1.0 / 61 + 1.0 / 63, within(TOLERANCE));
  }

  @Test
  void merge_usesConfiguredConstant() {
    List<RankedChunk> result = new RrfMerger(10).merge(List.of(list("a")), 5);

    assertThat(result.get(0).score()).isCloseTo(1.0 / 11, within(TOLERANCE));
  }

  @Test
  void merge_emptyInput_returnsEmptyList() {
    assertThat(merger.merge(List.of(), 5)).isEmpty();
    assertThat(merger.merge(List.of(List.of(), List.of()), 5)).isEmpty();
  }

  @Test
  void merge_limitSmallerThanResult_cutsAndRenumbersRanks() {
    List<RankedChunk> result = merger.merge(List.of(list("a", "b", "c", "d")), 2);

    assertThat(ids(result)).containsExactly("a", "b");
    assertThat(result).extracting(RankedChunk::rank).containsExactly(1, 2);
  }

  @Test
  void merge_zeroLimit_returnsEmptyList() {
    assertThat(merger.merge(List.of(list("a", "b")), 0)).isEmpty();
  }

  @Test
  void merge_equalScores_breaksTiesByChunkIdAscending() {
    List<RankedChunk> result = merger.merge(List.of(list("b"), list("a")), 10);

    assertThat(ids(result)).containsExactly("a", "b");
    assertThat(result.get(0).score()).isEqualTo(result.get(1).score());
  }

  @Test
  void merge_duplicateWithinOneList_countsOnlyBestPosition() {
    List<RankedChunk> result = merger.merge(List.of(list("a", "a")), 10);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).score()).isCloseTo(1.0 / 61, within(TOLERANCE));
  }

  @Test
  void merge_chunkDetailsComeFromFirstListContainingIt() {
    RankedChunk laterCopy = new RankedChunk("b", DOC, 7, "other text", 1, 0.0);

    List<RankedChunk> result = merger.merge(List.of(list("a", "b"), List.of(laterCopy)), 10);

    RankedChunk merged = result.get(0);
    assertThat(merged.chunkId()).isEqualTo("b");
    assertThat(merged.page()).isEqualTo(1);
    assertThat(merged.text()).isEqualTo("text of b");
  }

  @Test
  void merge_ignoresRankFieldOnInputChunks() {
    RankedChunk first = new RankedChunk("a", DOC, 1, "text of a", 99, 0.0);
    RankedChunk second = new RankedChunk("b", DOC, 1, "text of b", 1, 0.0);

    List<RankedChunk> result = merger.merge(List.of(List.of(first, second)), 10);

    assertThat(ids(result)).containsExactly("a", "b");
    assertThat(result.get(0).score()).isCloseTo(1.0 / 61, within(TOLERANCE));
  }

  @Test
  void constructor_nonPositiveConstant_isRejected() {
    assertThatThrownBy(() -> new RrfMerger(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RrfMerger(-5)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void merge_negativeLimit_isRejected() {
    assertThatThrownBy(() -> merger.merge(List.of(list("a")), -1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}