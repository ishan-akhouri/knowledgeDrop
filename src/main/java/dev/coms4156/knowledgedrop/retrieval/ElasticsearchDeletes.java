package dev.coms4156.knowledgedrop.retrieval;

import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;

/**
 * Checks the outcome of an Elasticsearch delete-by-query call.
 *
 * <p>Elasticsearch can answer a delete-by-query with a normal HTTP 200 whose body says the work was
 * not finished: {@code timed_out} is true, or {@code failures} lists chunks that could not be
 * deleted. The Java client does not throw for that, so the stores check the response here and fail
 * loudly. Otherwise a failed cleanup would silently leave chunks searchable, which breaks the rule
 * that only chunks of ready documents are ever searchable.
 *
 * <p>The caller decides what to do with the exception: ingestion cleanup should log it and mark the
 * document failed, so a document that still has stray chunks is never treated as ready.
 */
final class ElasticsearchDeletes {

  private ElasticsearchDeletes() {}

  /**
   * Throws if the delete did not run to completion.
   *
   * @param response the delete-by-query response
   * @throws IllegalStateException if the request timed out or reported any failure
   */
  static void requireComplete(DeleteByQueryResponse response) {
    if (Boolean.TRUE.equals(response.timedOut())) {
      throw new IllegalStateException(
          "Elasticsearch delete timed out, so some chunks may still be indexed");
    }
    var failures = response.failures();
    if (failures != null && !failures.isEmpty()) {
      String reason = failures.get(0).cause().reason();
      throw new IllegalStateException(
          "Elasticsearch delete failed for "
              + failures.size()
              + " chunks, first failure: "
              + (reason == null ? "unknown" : reason));
    }
  }
}
