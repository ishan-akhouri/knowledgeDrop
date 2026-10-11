package dev.coms4156.knowledgedrop.service;

import dev.coms4156.knowledgedrop.api.dto.CompareRequest;
import dev.coms4156.knowledgedrop.api.dto.CompareResponse;
import dev.coms4156.knowledgedrop.api.dto.EvalRequest;
import dev.coms4156.knowledgedrop.api.dto.EvalResponse;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import org.springframework.stereotype.Service;

/**
 * Retrieval comparison and evaluation across vector, keyword, and hybrid search.
 *
 * <p>DEFERRED: vector search needs an embedding provider, which has not been chosen yet. Do not
 * build until that decision is made.
 *
 * <p>TODO(retrieval): the three retrieval modes and reciprocal rank fusion should live in one
 * shared component that ask, compare, and eval all use. RRF score = sum over lists of 1 / (rrfK +
 * rank), with rrfK from knowledgedrop.retrieval.rrf-k (default 60).
 */
@Service
public class SearchService {

  /**
   * Runs one query through vector, keyword, and hybrid retrieval.
   *
   * @param client the authenticated caller
   * @param request the query and optional K
   * @return the top K chunks per mode and an overlap summary
   */
  public CompareResponse compare(ClientRecord client, CompareRequest request) {
    // TODO(compare): if the client has no ready documents, return an error with a reason.
    // TODO(compare): scope every search to the client's documents.
    // TODO(compare): run vector search (Redis), keyword search (Elasticsearch BM25), and hybrid
    //   (both lists merged with RRF); record rank and score per chunk per mode.
    // TODO(compare): build the overlap summary (which chunks appear in more than one mode).
    throw ApiException.notImplemented("POST /knowledgeDrop/compare");
  }

  /**
   * Measures retrieval quality for each mode against a labeled question set.
   *
   * @param client the authenticated caller
   * @param request the labeled questions and optional K
   * @return recall@K and MRR per mode, plus a per-question breakdown
   */
  public EvalResponse eval(ClientRecord client, EvalRequest request) {
    // TODO(eval): verify every labeled document/chunk ID belongs to the client; otherwise return
    //   an error with a reason. Require at least one label per question (400).
    // TODO(eval): run each question through vector, keyword, and hybrid retrieval.
    // TODO(eval): recall@K = fraction of questions with a correct source in the top K;
    //   MRR = mean of 1 / rank of the first correct source (0 if not found).
    // TODO(eval): return per-mode metrics and the per-question rank breakdown.
    throw ApiException.notImplemented("POST /knowledgeDrop/eval");
  }
}
