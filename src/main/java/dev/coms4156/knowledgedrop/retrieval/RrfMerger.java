package dev.coms4156.knowledgedrop.retrieval;

import java.util.List;

/**
 * Reciprocal Rank Fusion: merges several ranked lists into one.
 *
 * <p>A pure class on purpose: no Spring, no datastores, no network. ask, compare, and eval all
 * depend on it, and it is the easiest piece to unit test thoroughly, so build it first.
 *
 * <p>Formula, for each chunk: {@code score = sum over lists of 1 / (rrfK + rank)}, where rank is
 * the 1-based position of the chunk in that list. A chunk missing from a list adds nothing for
 * that list. {@code rrfK} defaults to 60 (knowledgedrop.retrieval.rrf-k).
 */
public final class RrfMerger {

  private final int rrfK;

  /**
   * Creates a merger.
   *
   * @param rrfK the RRF constant, must be positive
   */
  public RrfMerger(int rrfK) {
    this.rrfK = rrfK;
  }

  /**
   * Merges ranked lists.
   *
   * @param rankedLists each list ordered best first (for example the vector list and the keyword
   *     list)
   * @param limit maximum chunks to return
   * @return merged chunks ordered by RRF score descending, with ranks renumbered from 1 and the
   *     score field set to the RRF score
   */
  public List<RankedChunk> merge(List<List<RankedChunk>> rankedLists, int limit) {
    // TODO(retrieval): identify chunks across lists by chunkId.
    // TODO(retrieval): sum 1 / (rrfK + rank) per chunk across every list it appears in.
    // TODO(retrieval): sort by score descending; break ties deterministically (for example by
    //   chunkId) so tests and eval are repeatable.
    // TODO(retrieval): cut to limit, renumber ranks from 1.
    throw new UnsupportedOperationException("RRF merge");
  }

  // Tests to write first (move from EndpointTodoTests.rrf_...):
  //   - one list in, same order out
  //   - a chunk in both lists outranks a chunk in only one
  //   - hand-computed example: ranks 1 and 3 with rrfK 60 => 1/61 + 1/63
  //   - empty lists, limit smaller than the result, tie-breaking is stable
}

