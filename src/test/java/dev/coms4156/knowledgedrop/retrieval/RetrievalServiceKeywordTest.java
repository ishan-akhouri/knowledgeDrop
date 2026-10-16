package dev.coms4156.knowledgedrop.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetrievalServiceKeywordTest {

  private final KeywordIndex keywordIndex = mock(KeywordIndex.class);
  private final RetrievalService service =
      new RetrievalService(null, null, keywordIndex, new RrfMerger(60));

  @Test
  void keyword_scopesTheSearchToTheClientAndReturnsTheIndexResultsInOrder() {
    UUID clientId = UUID.randomUUID();
    List<RankedChunk> expected =
        List.of(
            new RankedChunk("c-1", UUID.randomUUID(), 1, "best", 1, 4.2),
            new RankedChunk("c-2", UUID.randomUUID(), 1, "next", 2, 1.1));
    when(keywordIndex.search(clientId, "refund", 5)).thenReturn(expected);

    List<RankedChunk> results = service.keyword(clientId, "refund", 5);

    assertThat(results).isEqualTo(expected);
    verify(keywordIndex).search(clientId, "refund", 5);
  }
}
