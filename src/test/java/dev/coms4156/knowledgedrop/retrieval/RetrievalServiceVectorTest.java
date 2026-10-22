package dev.coms4156.knowledgedrop.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetrievalServiceVectorTest {

  private final VectorStore vectorStore = mock(VectorStore.class);
  private final FakeEmbeddingClient embeddings = new FakeEmbeddingClient();
  private final RetrievalService service =
      new RetrievalService(embeddings, vectorStore, null, new RrfMerger(60));

  @Test
  void vector_embedsTheQueryAndSearchesTheClientsChunks() {
    UUID clientId = UUID.randomUUID();
    List<RankedChunk> expected =
        List.of(
            new RankedChunk("c-1", UUID.randomUUID(), 1, "closest", 1, 0.9),
            new RankedChunk("c-2", UUID.randomUUID(), 1, "next", 2, 0.4));
    when(vectorStore.search(clientId, FakeEmbeddingClient.VECTOR, 5)).thenReturn(expected);

    List<RankedChunk> results = service.vector(clientId, "how do I cancel?", 5);

    assertThat(results).isEqualTo(expected);
    assertThat(embeddings.lastText).isEqualTo("how do I cancel?");
    verify(vectorStore).search(clientId, FakeEmbeddingClient.VECTOR, 5);
  }

  @Test
  void vector_blankQuery_returnsEmptyWithoutCallingTheEmbeddingProvider() {
    assertThat(service.vector(UUID.randomUUID(), "   ", 5)).isEmpty();
    assertThat(service.vector(UUID.randomUUID(), null, 5)).isEmpty();

    assertThat(embeddings.calls).isZero();
    verifyNoInteractions(vectorStore);
  }

  @Test
  void vector_nonPositiveK_returnsEmptyWithoutCallingTheEmbeddingProvider() {
    assertThat(service.vector(UUID.randomUUID(), "refund", 0)).isEmpty();

    assertThat(embeddings.calls).isZero();
    verifyNoInteractions(vectorStore);
  }

  /** A tiny fake so the test needs no embedding provider or network. */
  private static final class FakeEmbeddingClient implements EmbeddingClient {

    static final float[] VECTOR = {1f, 0f, 0f};

    private int calls;
    private String lastText;

    @Override
    public float[] embed(String text) {
      calls++;
      lastText = text;
      return VECTOR;
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
      return texts.stream().map(this::embed).toList();
    }

    @Override
    public int dimensions() {
      return VECTOR.length;
    }
  }
}
