package dev.coms4156.knowledgedrop.service;

import dev.coms4156.knowledgedrop.api.dto.SummarizeRequest;
import dev.coms4156.knowledgedrop.api.dto.SummarizeResponse;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import org.springframework.stereotype.Service;

/**
 * Map-reduce summarization of a single document.
 *
 * <p>DEFERRED: needs an LLM provider, which has not been chosen yet. Do not build until that
 * decision is made.
 */
@Service
public class SummarizationService {

  /**
   * Summarizes a document.
   *
   * @param client the authenticated caller
   * @param request the document ID and optional length
   * @return the summary and the number of chunks used
   */
  public SummarizeResponse summarize(ClientRecord client, SummarizeRequest request) {
    // TODO(summarize): verify the document belongs to the client and its status is ready;
    //   otherwise return an error with a reason (missing, still processing, or failed).
    // TODO(summarize): retrieve the document's chunks in order.
    // TODO(summarize): for long documents, summarize groups of chunks with an LLM first, then
    //   combine the partial summaries into a final summary (map-reduce). Length defaults to short.
    throw ApiException.notImplemented("POST /knowledgeDrop/summarize");
  }
}
