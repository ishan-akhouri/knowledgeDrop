package dev.coms4156.knowledgedrop.retrieval;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import org.junit.jupiter.api.Test;

/** Unit tests for how delete-by-query responses are judged, no Elasticsearch needed. */
class ElasticsearchDeletesTest {

  @Test
  void requireComplete_cleanResponse_passes() {
    DeleteByQueryResponse response = DeleteByQueryResponse.of(r -> r.deleted(2L).timedOut(false));

    assertThatCode(() -> ElasticsearchDeletes.requireComplete(response))
        .doesNotThrowAnyException();
  }

  @Test
  void requireComplete_nothingToDelete_passes() {
    DeleteByQueryResponse response = DeleteByQueryResponse.of(r -> r.deleted(0L));

    assertThatCode(() -> ElasticsearchDeletes.requireComplete(response))
        .doesNotThrowAnyException();
  }

  @Test
  void requireComplete_timedOut_throws() {
    DeleteByQueryResponse response = DeleteByQueryResponse.of(r -> r.deleted(1L).timedOut(true));

    assertThatThrownBy(() -> ElasticsearchDeletes.requireComplete(response))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("timed out");
  }

  @Test
  void requireComplete_reportedFailures_throwsWithTheFirstReason() {
    DeleteByQueryResponse response =
        DeleteByQueryResponse.of(
            r ->
                r.timedOut(false)
                    .failures(
                        f ->
                            f.id("chunk-1")
                                .index("kd-vectors")
                                .status(409)
                                .cause(
                                    c ->
                                        c.type("version_conflict")
                                            .reason("conflict on chunk-1"))));

    assertThatThrownBy(() -> ElasticsearchDeletes.requireComplete(response))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("1 chunks")
        .hasMessageContaining("conflict on chunk-1");
  }
}
