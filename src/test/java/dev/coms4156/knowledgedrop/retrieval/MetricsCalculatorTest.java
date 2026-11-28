package dev.coms4156.knowledgedrop.retrieval;

import static dev.coms4156.knowledgedrop.retrieval.MetricsCalculator.firstRelevantRank;
import static dev.coms4156.knowledgedrop.retrieval.MetricsCalculator.meanRecallAtK;
import static dev.coms4156.knowledgedrop.retrieval.MetricsCalculator.meanReciprocalRank;
import static dev.coms4156.knowledgedrop.retrieval.MetricsCalculator.recallAtK;
import static dev.coms4156.knowledgedrop.retrieval.MetricsCalculator.reciprocalRank;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MetricsCalculatorTest {

  private static final double EPSILON = 1e-9;

  // recall@k

  @Test
  void recallAtK_hitAtRankOne_isOne() {
    assertThat(recallAtK(List.of("a", "x", "y"), Set.of("a"), 3)).isEqualTo(1.0);
  }

  @Test
  void recallAtK_hitAtRankThree_countsOnlyWhenTheCutoffReachesIt() {
    List<String> ranked = List.of("x", "y", "a");

    assertThat(recallAtK(ranked, Set.of("a"), 2)).isEqualTo(0.0);
    assertThat(recallAtK(ranked, Set.of("a"), 3)).isEqualTo(1.0);
  }

  @Test
  void recallAtK_miss_isZero() {
    assertThat(recallAtK(List.of("x", "y", "z"), Set.of("a"), 3)).isEqualTo(0.0);
  }

  @Test
  void recallAtK_someRelevantFound_isTheFractionFound() {
    List<String> ranked = List.of("a", "x", "y", "d");
    Set<String> relevant = Set.of("a", "d");

    assertThat(recallAtK(ranked, relevant, 3)).isEqualTo(0.5);
    assertThat(recallAtK(ranked, relevant, 4)).isEqualTo(1.0);
  }

  @Test
  void recallAtK_repeatedResults_doNotCountTwice() {
    assertThat(recallAtK(List.of("a", "a", "a"), Set.of("a", "b"), 3)).isEqualTo(0.5);
  }

  @Test
  void recallAtK_emptyResults_isZero() {
    assertThat(recallAtK(List.of(), Set.of("a"), 5)).isEqualTo(0.0);
  }

  @Test
  void recallAtK_noRelevantDocuments_isZero() {
    assertThat(recallAtK(List.of("a", "b"), Set.of(), 5)).isEqualTo(0.0);
  }

  @Test
  void recallAtK_cutoffLargerThanTheList_usesTheWholeList() {
    assertThat(recallAtK(List.of("a", "b"), Set.of("a", "b"), 10)).isEqualTo(1.0);
  }

  @Test
  void recallAtK_nonPositiveK_isRejected() {
    assertThatThrownBy(() -> recallAtK(List.of("a"), Set.of("a"), 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("k must be positive");
    assertThatThrownBy(() -> recallAtK(List.of("a"), Set.of("a"), -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // reciprocal rank

  @Test
  void firstRelevantRank_isOneBasedAndZeroOnMiss() {
    assertThat(firstRelevantRank(List.of("x", "y", "a"), Set.of("a"))).isEqualTo(3);
    assertThat(firstRelevantRank(List.of("x", "y"), Set.of("a"))).isEqualTo(0);
  }

  @Test
  void reciprocalRank_hitAtRankOne_isOne() {
    assertThat(reciprocalRank(List.of("a", "x"), Set.of("a"))).isEqualTo(1.0);
  }

  @Test
  void reciprocalRank_hitAtRankThree_isOneThird() {
    assertThat(reciprocalRank(List.of("x", "y", "a"), Set.of("a")))
        .isCloseTo(1.0 / 3, within(EPSILON));
  }

  @Test
  void reciprocalRank_miss_isZero() {
    assertThat(reciprocalRank(List.of("x", "y"), Set.of("a"))).isEqualTo(0.0);
  }

  @Test
  void reciprocalRank_severalRelevant_usesTheFirstOne() {
    assertThat(reciprocalRank(List.of("x", "a", "b"), Set.of("a", "b"))).isEqualTo(0.5);
  }

  @Test
  void reciprocalRank_emptyResultsOrNoRelevantDocuments_isZero() {
    assertThat(reciprocalRank(List.of(), Set.of("a"))).isEqualTo(0.0);
    assertThat(reciprocalRank(List.of("a"), Set.of())).isEqualTo(0.0);
  }

  // means over questions

  @Test
  void meanReciprocalRank_averagesRankOneRankThreeAndMiss() {
    List<List<String>> ranked =
        List.of(List.of("a", "x"), List.of("x", "y", "b"), List.of("x", "y"));
    List<Set<String>> relevant = List.of(Set.of("a"), Set.of("b"), Set.of("c"));

    assertThat(meanReciprocalRank(ranked, relevant)).isCloseTo(4.0 / 9, within(EPSILON));
  }

  @Test
  void meanReciprocalRank_noQuestions_isZero() {
    assertThat(meanReciprocalRank(List.of(), List.of())).isEqualTo(0.0);
  }

  @Test
  void meanReciprocalRank_differentNumberOfListsAndSets_isRejected() {
    assertThatThrownBy(() -> meanReciprocalRank(List.of(List.of("a")), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void meanRecallAtK_averagesTheQuestions() {
    List<List<String>> ranked = List.of(List.of("a", "b"), List.of("x", "a"));
    List<Set<String>> relevant = List.of(Set.of("a", "b"), Set.of("a", "d"));

    assertThat(meanRecallAtK(ranked, relevant, 2)).isEqualTo(0.75);
  }

  @Test
  void meanRecallAtK_nonPositiveCutoffOrMismatchedLists_isRejected() {
    assertThatThrownBy(() -> meanRecallAtK(List.of(), List.of(), 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> meanRecallAtK(List.of(List.of("a")), List.of(), 1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
