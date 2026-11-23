package dev.coms4156.knowledgedrop.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetrievalServiceKeywordTest {

  private final KeywordIndex keywordIndex = mock(KeywordIndex.class);
  private final DocumentRepository documents = mock(DocumentRepository.class);
  private final RetrievalService service =
      new RetrievalService(null, null, keywordIndex, documents,
          RetrievalServiceReadinessTest.properties());

  @Test
  void keyword_scopesTheSearchToTheClientAndReturnsTheIndexResultsInOrder() {
    UUID clientId = UUID.randomUUID();
    List<RankedChunk> expected =
        List.of(
            new RankedChunk("c-1", UUID.randomUUID(), 1, "best", 1, 4.2),
            new RankedChunk("c-2", UUID.randomUUID(), 1, "next", 2, 1.1));
    when(keywordIndex.search(clientId, "refund", 50)).thenReturn(expected);

    when(documents.findAllByOwnerIdAndIdIn(clientId,
        Set.of(expected.get(0).documentId(), expected.get(1).documentId())))
        .thenReturn(expected.stream().map(c -> RetrievalServiceReadinessTest.document(
            c.documentId(), dev.coms4156.knowledgedrop.model.DocumentStatus.READY)).toList());

    List<RankedChunk> results = service.keyword(clientId, "refund", 5);

    assertThat(results).isEqualTo(expected);
    verify(keywordIndex).search(clientId, "refund", 50);
  }
}
