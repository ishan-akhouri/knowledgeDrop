package dev.coms4156.knowledgedrop.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reciprocal Rank Fusion: merges several ranked lists into one.
 *
 * <p>A pure class on purpose: no Spring, no datastores, no network. ask, compare, and eval all
 * depend on it, and it is the easiest piece to unit test thoroughly.
 *
 * <p>Formula, for each chunk: {@code score = sum over lists of 1 / (rrfK + rank)}, where rank is
 * the 1-based position of the chunk in that list. A chunk missing from a list adds nothing for
 * that list. {@code rrfK} defaults to 60 (knowledgedrop.retrieval.rrf-k).
 *
 * <p>Position in the list is the rank; the {@code rank} field of the input chunks is ignored, so
 * lists from different retrieval modes can be merged without trusting their own numbering.
 */
public final class RrfMerger {

  private final int rrfK;

  /**
   * Creates a merger.
   *
   * @param rrfK the RRF constant, must be positive
   * @throws IllegalArgumentException if {@code rrfK} is not positive
   */
  public RrfMerger(int rrfK) {
    if (rrfK <= 0) {
      throw new IllegalArgumentException("rrfK must be positive, was " + rrfK);
    }
    this.rrfK = rrfK;
  }

  /**
   * Merges ranked lists.
   *
   * <p>Chunks are identified across lists by {@code chunkId}. If a chunk appears more than once in
   * one list, only its first (best) position counts. Chunk details (document, page, text) come
   * from the first list that contains the chunk. Ties are broken by {@code chunkId} ascending so
   * results are repeatable.
   *
   * @param rankedLists each list ordered best first (for example the vector list and the keyword
   *     list)
   * @param limit maximum chunks to return
   * @return merged chunks ordered by RRF score descending, with ranks renumbered from 1 and the
   *     score field set to the RRF score
   * @throws IllegalArgumentException if {@code limit} is negative
   */
  public List<RankedChunk> merge(List<List<RankedChunk>> rankedLists, int limit) {
    if (limit < 0) {
      throw new IllegalArgumentException("limit must not be negative, was " + limit);
    }
    Map<String, Double> scores = new LinkedHashMap<>();
    Map<String, RankedChunk> firstSeen = new LinkedHashMap<>();
    for (List<RankedChunk> list : rankedLists) {
      Set<String> seenInList = new HashSet<>();
      for (int i = 0; i < list.size(); i++) {
        RankedChunk chunk = list.get(i);
        if (seenInList.add(chunk.chunkId())) {
          scores.merge(chunk.chunkId(), 1.0 / (rrfK + i + 1), Double::sum);
          firstSeen.putIfAbsent(chunk.chunkId(), chunk);
        }
      }
    }

    List<String> ids = new ArrayList<>(scores.keySet());
    ids.sort(
        Comparator.comparingDouble((String id) -> scores.get(id))
            .reversed()
            .thenComparing(Comparator.naturalOrder()));

    int count = Math.min(limit, ids.size());
    List<RankedChunk> merged = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      String id = ids.get(i);
      RankedChunk source = firstSeen.get(id);
      merged.add(
          new RankedChunk(
              id, source.documentId(), source.page(), source.text(), i + 1, scores.get(id)));
    }
    return List.copyOf(merged);
  }
}