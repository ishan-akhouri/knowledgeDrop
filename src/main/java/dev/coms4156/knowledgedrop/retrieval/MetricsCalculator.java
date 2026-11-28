package dev.coms4156.knowledgedrop.retrieval;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Retrieval quality metrics: recall@k and mean reciprocal rank (MRR).
 *
 * <p>A pure class with no Spring and no datastore dependencies. It scores a ranked result list
 * against the set of items labeled relevant for a question. The item type is generic, so callers
 * can pass document IDs or chunk IDs.
 *
 * <p>Definitions:
 *
 * <p>recall@k = (distinct relevant items in the top k results) / (total relevant items).
 *
 * <p>reciprocal rank = 1 / (rank of the first relevant result), or 0 if none is found. MRR is the
 * mean of the reciprocal rank over all questions.
 *
 * <p>Ranks start at 1 and follow the list exactly as given, so a caller that wants document-level
 * ranks must pass a list of document IDs. A relevant item that appears more than once counts once
 * for recall.
 *
 * <p>Edge cases return 0.0 instead of failing: empty results, a question with no relevant items,
 * and no questions at all. A k larger than the list uses the whole list. A k that is not positive
 * is rejected.
 */
public final class MetricsCalculator {

  private MetricsCalculator() {
    // Static helpers only.
  }

  /**
   * Recall@k for one question.
   *
   * @param ranked the results, best first
   * @param relevant the items labeled relevant for the question
   * @param k how many top results to consider, must be positive
   * @param <T> the item type
   * @return relevant items found in the top k divided by the total relevant items, or 0.0 if
   *     there are no relevant items
   * @throws IllegalArgumentException if {@code k} is not positive
   */
  public static <T> double recallAtK(List<T> ranked, Set<T> relevant, int k) {
    requirePositive(k);
    if (relevant.isEmpty()) {
      return 0.0;
    }
    int limit = Math.min(k, ranked.size());
    Set<T> found = new HashSet<>();
    for (int i = 0; i < limit; i++) {
      T item = ranked.get(i);
      if (relevant.contains(item)) {
        found.add(item);
      }
    }
    return (double) found.size() / relevant.size();
  }

  /**
   * The 1-based rank of the first relevant result.
   *
   * @param ranked the results, best first
   * @param relevant the items labeled relevant for the question
   * @param <T> the item type
   * @return the rank, or 0 if no result is relevant
   */
  public static <T> int firstRelevantRank(List<T> ranked, Set<T> relevant) {
    for (int i = 0; i < ranked.size(); i++) {
      if (relevant.contains(ranked.get(i))) {
        return i + 1;
      }
    }
    return 0;
  }

  /**
   * Reciprocal rank for one question.
   *
   * @param ranked the results, best first
   * @param relevant the items labeled relevant for the question
   * @param <T> the item type
   * @return 1 / (rank of the first relevant result), or 0.0 if none is found
   */
  public static <T> double reciprocalRank(List<T> ranked, Set<T> relevant) {
    int rank = firstRelevantRank(ranked, relevant);
    return rank == 0 ? 0.0 : 1.0 / rank;
  }

  /**
   * Mean reciprocal rank over several questions.
   *
   * @param rankedLists the results of each question, best first
   * @param relevantSets the relevant items of each question, in the same order
   * @param <T> the item type
   * @return the mean reciprocal rank, or 0.0 if there are no questions
   * @throws IllegalArgumentException if the two lists differ in size
   */
  public static <T> double meanReciprocalRank(
      List<List<T>> rankedLists, List<Set<T>> relevantSets) {
    requireSameSize(rankedLists, relevantSets);
    double sum = 0.0;
    for (int i = 0; i < rankedLists.size(); i++) {
      sum += reciprocalRank(rankedLists.get(i), relevantSets.get(i));
    }
    return rankedLists.isEmpty() ? 0.0 : sum / rankedLists.size();
  }

  /**
   * Mean recall@k over several questions.
   *
   * @param rankedLists the results of each question, best first
   * @param relevantSets the relevant items of each question, in the same order
   * @param k how many top results to consider, must be positive
   * @param <T> the item type
   * @return the mean recall@k, or 0.0 if there are no questions
   * @throws IllegalArgumentException if {@code k} is not positive or the lists differ in size
   */
  public static <T> double meanRecallAtK(
      List<List<T>> rankedLists, List<Set<T>> relevantSets, int k) {
    requirePositive(k);
    requireSameSize(rankedLists, relevantSets);
    double sum = 0.0;
    for (int i = 0; i < rankedLists.size(); i++) {
      sum += recallAtK(rankedLists.get(i), relevantSets.get(i), k);
    }
    return rankedLists.isEmpty() ? 0.0 : sum / rankedLists.size();
  }

  private static void requirePositive(int k) {
    if (k <= 0) {
      throw new IllegalArgumentException("k must be positive, was " + k);
    }
  }

  private static void requireSameSize(List<?> rankedLists, List<?> relevantSets) {
    if (rankedLists.size() != relevantSets.size()) {
      throw new IllegalArgumentException(
          "Need one relevant set per question: " + rankedLists.size() + " result lists but "
              + relevantSets.size() + " relevant sets");
    }
  }
}
