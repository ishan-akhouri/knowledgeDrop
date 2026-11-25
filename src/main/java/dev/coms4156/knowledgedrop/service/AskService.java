package dev.coms4156.knowledgedrop.service;

import dev.coms4156.knowledgedrop.api.dto.AskRequest;
import dev.coms4156.knowledgedrop.api.dto.AskResponse;
import dev.coms4156.knowledgedrop.api.dto.SimilarityRequest;
import dev.coms4156.knowledgedrop.api.dto.SimilarityResponse;
import dev.coms4156.knowledgedrop.config.KnowledgeDropProperties;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.retrieval.RetrievalService;
import org.springframework.stereotype.Service;

/** Question answering (ask) and document similarity. */
@Service
public class AskService {

  private final RetrievalService retrieval;
  private final int defaultK;

  /**
   * Creates the service.
   *
   * @param retrieval shared retrieval orchestration
   * @param properties default result count
   */
  public AskService(RetrievalService retrieval, KnowledgeDropProperties properties) {
    this.retrieval = retrieval;
    this.defaultK = properties.retrieval().defaultK();
  }

  /**
   * Answers a question using only the caller's documents, with citations.
   *
   * @param client the authenticated caller
   * @param request the question and optional K
   * @return the answer and its citations
   */
  public AskResponse ask(ClientRecord client, AskRequest request) {
    // TODO: ask: if the client has no ready documents, return an error with a reason.
    // TODO: ask: run hybrid retrieval (Elasticsearch kNN vector search + BM25, merged with RRF)
    //   over the client's ready documents; K defaults to knowledgedrop.retrieval.default-k (5).
    // TODO: ask: pass the top K chunks to an LLM with instructions to answer only from that
    //   context.
    // TODO: ask: attach a Citation (document ID, filename, page, snippet) per chunk used.
    // TODO: ask: if retrieval returns nothing relevant, return a "not found in your documents"
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
    if (request.text() == null || request.text().isBlank()) {
      throw ApiException.badRequest("text must not be blank");
    }
    int k = request.k() == null ? defaultK : request.k();
    if (k < 1) {
      throw ApiException.badRequest("k must be greater than or equal to 1");
    }
    String mode = request.mode() == null ? "vector" : request.mode();
    if (!java.util.Set.of("vector", "keyword", "hybrid").contains(mode)) {
      throw ApiException.badRequest("mode must be one of: vector, keyword, hybrid");
    }
    return new SimilarityResponse(retrieval.similarity(client.getId(), request.text(), k, mode));
  }
}
