package dev.coms4156.knowledgedrop.service;

import dev.coms4156.knowledgedrop.api.dto.AskRequest;
import dev.coms4156.knowledgedrop.api.dto.AskResponse;
import dev.coms4156.knowledgedrop.api.dto.SimilarityRequest;
import dev.coms4156.knowledgedrop.api.dto.SimilarityResponse;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import org.springframework.stereotype.Service;

/**
 * Question answering (ask) and document similarity.
 *
 * <p>DEFERRED: both need an LLM and/or embedding provider, which has not been chosen yet. Do not
 * build until that decision is made.
 */
@Service
public class QaService {

  /**
   * Answers a question using only the caller's documents, with citations.
   *
   * @param client the authenticated caller
   * @param request the question and optional K
   * @return the answer and its citations
   */
  public AskResponse ask(ClientRecord client, AskRequest request) {
    // TODO(ask): if the client has no ready documents, return an error with a reason.
    // TODO(ask): run hybrid retrieval (Redis vector search + Elasticsearch BM25, merged with RRF)
    //   over the client's ready documents; K defaults to knowledgedrop.retrieval.default-k (5).
    // TODO(ask): pass the top K chunks to an LLM with instructions to answer only from that
    //   context.
    // TODO(ask): attach a Citation (document ID, filename, page, snippet) per chunk used.
    // TODO(ask): if retrieval returns nothing relevant, return a "not found in your documents"
    //   statement with HTTP 200 and no citations.
    throw ApiException.notImplemented("POST /knowledgeDrop/ask");
  }

  /**
   * Ranks the caller's stored documents by similarity to the given text.
   *
   * @param client the authenticated caller
   * @param request the text to compare and optional K
   * @return the top K stored documents with scores and best-matching snippets
   */
  public SimilarityResponse similarity(ClientRecord client, SimilarityRequest request) {
    // TODO(similarity): if the client has no ready documents, return an error with a reason.
    // TODO(similarity): embed the input text and compare it with the client's stored chunk
    //   embeddings (vector search only, no LLM).
    // TODO(similarity): group chunk scores by document, rank documents, return the top K with the
    //   best-matching snippet each.
    throw ApiException.notImplemented("POST /knowledgeDrop/similarity");
  }
}
