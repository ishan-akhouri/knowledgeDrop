package dev.coms4156.knowledgedrop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.coms4156.knowledgedrop.api.dto.SimilarDocument;
import dev.coms4156.knowledgedrop.api.dto.SimilarityRequest;
import dev.coms4156.knowledgedrop.config.KnowledgeDropProperties;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.retrieval.RetrievalService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AskServiceSimilarityTest {
  private final RetrievalService retrieval = mock(RetrievalService.class);
  private final ClientRecord client = new ClientRecord("test", "hash");
  private final AskService service = new AskService(retrieval, new KnowledgeDropProperties(
      null, null, new KnowledgeDropProperties.Retrieval(5, 60, 50), null, null));

  @Test
  void passesOwnerTextAndDefaultOrExplicitLimitToRetrieval() {
    UUID owner = UUID.randomUUID();
    ReflectionTestUtils.setField(client, "id", owner);
    List<SimilarDocument> results = List.of(
        new SimilarDocument(UUID.randomUUID(), "test.txt", 0.8, "best match"));
    when(retrieval.similarity(owner, "text", 5, "vector")).thenReturn(results);
    assertThat(service.similarity(client, new SimilarityRequest("text", null)).results())
        .isEqualTo(results);
    service.similarity(client, new SimilarityRequest("text", 2));
    verify(retrieval).similarity(owner, "text", 2, "vector");
  }

  @Test
  void rejectsInvalidInputsWithoutRetrieval() {
    for (String text : new String[] {null, "", " \t"}) {
      assertThatThrownBy(() -> service.similarity(client, new SimilarityRequest(text, null)))
          .isInstanceOf(ApiException.class).hasMessage("text must not be blank");
    }
    assertThatThrownBy(() -> service.similarity(client, new SimilarityRequest("text", 0)))
        .isInstanceOf(ApiException.class).hasMessageContaining("k must be");
    assertThatThrownBy(() -> service.similarity(client,
        new SimilarityRequest("text", 5, "unknown")))
        .isInstanceOf(ApiException.class).hasMessageContaining("mode must be");
    assertThatThrownBy(() -> service.similarity(client, new SimilarityRequest("text", 10001)))
        .isInstanceOf(ApiException.class).hasMessageContaining("k must be less than");
    verifyNoInteractions(retrieval);
  }
}
