package dev.coms4156.knowledgedrop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import dev.coms4156.knowledgedrop.retrieval.KeywordIndex;
import dev.coms4156.knowledgedrop.retrieval.VectorStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit tests for {@link ChunkCleanupJob}. */
class ChunkCleanupJobTest {

  private final DocumentRepository documents = mock(DocumentRepository.class);
  private final VectorStore vectorStore = mock(VectorStore.class);
  private final KeywordIndex keywordIndex = mock(KeywordIndex.class);
  private final ChunkCleanupJob job =
      new ChunkCleanupJob(documents, vectorStore, keywordIndex, Duration.ofHours(2), 3);

  @Test
  void failStuckIngestionsUsesCutoffStuckAfterBeforeNow() {
    when(documents.failStuckProcessing(any(), any())).thenReturn(2);
    Instant before = Instant.now();

    assertThat(job.failStuckIngestions()).isEqualTo(2);

    ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
    verify(documents).failStuckProcessing(cutoff.capture(), eq(ChunkCleanupJob.STUCK_REASON));
    assertThat(cutoff.getValue())
        .isBetween(before.minus(Duration.ofHours(2)), Instant.now().minus(Duration.ofHours(2)));
  }

  @Test
  void purgeDeletesBothStoresAndRecordsEachPurge() {
    DocumentRecord first = document();
    DocumentRecord second = document();
    when(documents.findWithUnpurgedChunks(any())).thenReturn(List.of(first, second));

    assertThat(job.purgeStaleChunks()).isEqualTo(2);

    ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
    verify(documents).findWithUnpurgedChunks(page.capture());
    assertThat(page.getValue()).isEqualTo(PageRequest.of(0, 3));
    for (DocumentRecord document : List.of(first, second)) {
      InOrder order = inOrder(vectorStore, keywordIndex, documents);
      order.verify(vectorStore).deleteByDocument(document.getId());
      order.verify(keywordIndex).deleteByDocument(document.getId());
      order.verify(documents).markChunksPurged(eq(document.getId()), any());
    }
  }

  @Test
  void purgeStopsAtFirstFailureWithoutRecordingIt() {
    DocumentRecord first = document();
    DocumentRecord broken = document();
    DocumentRecord last = document();
    when(documents.findWithUnpurgedChunks(any())).thenReturn(List.of(first, broken, last));
    doThrow(new IllegalStateException("es down"))
        .when(keywordIndex).deleteByDocument(broken.getId());

    assertThat(job.purgeStaleChunks()).isEqualTo(1);

    verify(documents).markChunksPurged(eq(first.getId()), any());
    verify(documents, never()).markChunksPurged(eq(broken.getId()), any());
    verify(vectorStore, never()).deleteByDocument(last.getId());
    verify(documents, never()).markChunksPurged(eq(last.getId()), any());
  }

  @Test
  void purgeWithNothingStaleTouchesNoStore() {
    when(documents.findWithUnpurgedChunks(any())).thenReturn(List.of());

    assertThat(job.purgeStaleChunks()).isZero();

    verify(vectorStore, never()).deleteByDocument(any());
    verify(keywordIndex, never()).deleteByDocument(any());
  }

  @Test
  void runFailsStuckIngestionsBeforePurgingSoTheyArePurgedTheSameRun() {
    when(documents.findWithUnpurgedChunks(any())).thenReturn(List.of());

    job.run();

    InOrder order = inOrder(documents);
    order.verify(documents).failStuckProcessing(any(), any());
    order.verify(documents).findWithUnpurgedChunks(any());
  }

  @Test
  void runSwallowsDatabaseFailures() {
    when(documents.failStuckProcessing(any(), any())).thenThrow(new IllegalStateException("db"));

    job.run();

    verify(documents, never()).findWithUnpurgedChunks(any());
  }

  @Test
  void rejectsNonPositiveSettings() {
    assertThatThrownBy(() -> new ChunkCleanupJob(documents, vectorStore, keywordIndex,
        Duration.ZERO, 1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ChunkCleanupJob(documents, vectorStore, keywordIndex,
        Duration.ofMinutes(-1), 1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ChunkCleanupJob(documents, vectorStore, keywordIndex,
        Duration.ofHours(1), 0)).isInstanceOf(IllegalArgumentException.class);
  }

  private static DocumentRecord document() {
    DocumentRecord document =
        new DocumentRecord(UUID.randomUUID(), "doc", "text/plain", 0, "0".repeat(40));
    ReflectionTestUtils.setField(document, "id", UUID.randomUUID());
    return document;
  }
}
