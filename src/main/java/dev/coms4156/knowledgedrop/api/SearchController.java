package dev.coms4156.knowledgedrop.api;

import dev.coms4156.knowledgedrop.api.dto.CompareRequest;
import dev.coms4156.knowledgedrop.api.dto.CompareResponse;
import dev.coms4156.knowledgedrop.api.dto.EvalRequest;
import dev.coms4156.knowledgedrop.api.dto.EvalResponse;
import dev.coms4156.knowledgedrop.api.dto.SummarizeRequest;
import dev.coms4156.knowledgedrop.api.dto.SummarizeResponse;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.security.AuthInterceptor;
import dev.coms4156.knowledgedrop.service.SearchService;
import dev.coms4156.knowledgedrop.service.SummarizationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Searching, evaluation, and summarization endpoints. All are TODO. */
@RestController
@RequestMapping("/knowledgeDrop")
public class SearchController {

  private final SearchService searchService;
  private final SummarizationService summarizationService;

  /**
   * Creates the controller.
   *
   * @param searchService compare and eval logic
   * @param summarizationService summarize logic
   */
  public SearchController(
      SearchService searchService, SummarizationService summarizationService) {
    this.searchService = searchService;
    this.summarizationService = summarizationService;
  }

  /** Runs a query through vector, keyword, and hybrid retrieval. TODO(compare). */
  @PostMapping("/compare")
  public CompareResponse compare(
      @RequestAttribute(AuthInterceptor.CLIENT_ATTRIBUTE) ClientRecord client,
      @Valid @RequestBody CompareRequest request) {
    return searchService.compare(client, request);
  }

  /** Computes recall@K and MRR per retrieval mode for a labeled question set. TODO(eval). */
  @PostMapping("/eval")
  public EvalResponse eval(
      @RequestAttribute(AuthInterceptor.CLIENT_ATTRIBUTE) ClientRecord client,
      @Valid @RequestBody EvalRequest request) {
    return searchService.eval(client, request);
  }

  /** Summarizes a document. TODO(summarize). */
  @PostMapping("/summarize")
  public SummarizeResponse summarize(
      @RequestAttribute(AuthInterceptor.CLIENT_ATTRIBUTE) ClientRecord client,
      @Valid @RequestBody SummarizeRequest request) {
    return summarizationService.summarize(client, request);
  }
}
