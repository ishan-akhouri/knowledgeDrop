package dev.coms4156.knowledgedrop.api;

import dev.coms4156.knowledgedrop.api.dto.AskRequest;
import dev.coms4156.knowledgedrop.api.dto.AskResponse;
import dev.coms4156.knowledgedrop.api.dto.SimilarityRequest;
import dev.coms4156.knowledgedrop.api.dto.SimilarityResponse;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.security.AuthInterceptor;
import dev.coms4156.knowledgedrop.service.AskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Question answering and similarity endpoints. Both are TODO. */
@RestController
@RequestMapping("/knowledgeDrop")
public class AskController {

  private final AskService qaService;

  /**
   * Creates the controller.
   *
   * @param qaService question answering and similarity logic
   */
  public AskController(AskService qaService) {
    this.qaService = qaService;
  }

  /** Answers a question from the caller's documents, with citations. TODO(ask). */
  @PostMapping("/ask")
  public AskResponse ask(
      @RequestAttribute(AuthInterceptor.CLIENT_ATTRIBUTE) ClientRecord client,
      @Valid @RequestBody AskRequest request) {
    return qaService.ask(client, request);
  }

  /** Ranks the caller's documents by similarity to the given text. TODO(similarity). */
  @PostMapping("/similarity")
  public SimilarityResponse similarity(
      @RequestAttribute(AuthInterceptor.CLIENT_ATTRIBUTE) ClientRecord client,
      @Valid @RequestBody SimilarityRequest request) {
    return qaService.similarity(client, request);
  }
}
