package dev.coms4156.knowledgedrop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.model.DocumentStatus;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import dev.coms4156.knowledgedrop.retrieval.KeywordIndex;
import dev.coms4156.knowledgedrop.retrieval.VectorStore;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Runs {@link ChunkCleanupJob} against the real repository queries (H2), with Elasticsearch
 * mocked. Other tests share the database, so assertions only look at this test's documents.
 */
@SpringBootTest
@ActiveProfiles("test")
class ChunkCleanupJobIntegrationTest {

  @Autowired private DocumentRepository documents;
  private final UUID owner = UUID.randomUUID();
  private final VectorStore vectorStore = mock(VectorStore.class);
  private final KeywordIndex keywordIndex = mock(KeywordIndex.class);

  @AfterEach
  void tearDown() {
    documents.deleteAll(documents.findByOwnerId(owner, Pageable.unpaged()).getContent());
  }

  @Test
  void purgesDeletedAndFailedDocumentsOnceAndLeavesSearchableOnesAlone() {
    final DocumentRecord ready = save("ready", DocumentStatus.READY, Duration.ZERO);
    final DocumentRecord failed = save("failed", DocumentStatus.FAILED, Duration.ZERO);
    DocumentRecord deleted = save("deleted", DocumentStatus.READY, Duration.ZERO);
    deleted.setDeletedAt(Instant.now());
    documents.saveAndFlush(deleted);
    DocumentRecord recent = save("recent", DocumentStatus.PROCESSING, Duration.ZERO);

    job().run();

    for (DocumentRecord purged : new DocumentRecord[] {failed, deleted}) {
      verify(vectorStore).deleteByDocument(purged.getId());
      verify(keywordIndex).deleteByDocument(purged.getId());
      assertThat(reload(purged).getChunksPurgedAt()).isNotNull();
    }
    for (DocumentRecord untouched : new DocumentRecord[] {ready, recent}) {
      verify(vectorStore, never()).deleteByDocument(untouched.getId());
      assertThat(reload(untouched).getChunksPurgedAt()).isNull();
    }
    assertThat(reload(recent).getStatus()).isEqualTo(DocumentStatus.PROCESSING);

    // A second run must not purge the same documents again.
    VectorStore secondStore = mock(VectorStore.class);
    new ChunkCleanupJob(documents, secondStore, keywordIndex, Duration.ofHours(1), 10_000).run();
    verify(secondStore, never()).deleteByDocument(failed.getId());
    verify(secondStore, never()).deleteByDocument(deleted.getId());
  }

  @Test
  void stuckProcessingDocumentIsFailedAndPurgedInTheSameRun() {
    DocumentRecord stuck = save("stuck", DocumentStatus.PROCESSING, Duration.ofDays(2));

    job().run();

    DocumentRecord reloaded = reload(stuck);
    assertThat(reloaded.getStatus()).isEqualTo(DocumentStatus.FAILED);
    assertThat(reloaded.getFailureReason()).isEqualTo(ChunkCleanupJob.STUCK_REASON);
    assertThat(reloaded.getChunksPurgedAt()).isNotNull();
    verify(vectorStore).deleteByDocument(stuck.getId());
    verify(keywordIndex).deleteByDocument(stuck.getId());
  }

  @Test
  void deletedStuckDocumentKeepsItsStatusButIsPurged() {
    DocumentRecord stuck = save("deleted-stuck", DocumentStatus.PROCESSING, Duration.ofDays(2));
    stuck.setDeletedAt(Instant.now());
    documents.saveAndFlush(stuck);

    job().run();

    DocumentRecord reloaded = reload(stuck);
    assertThat(reloaded.getStatus()).isEqualTo(DocumentStatus.PROCESSING);
    assertThat(reloaded.getChunksPurgedAt()).isNotNull();
  }

  @Test
  void failedPurgeIsNotRecordedAndIsRetried() {
    DocumentRecord failed = save("retry", DocumentStatus.FAILED, Duration.ZERO);
    VectorStore down = mock(VectorStore.class);
    doThrow(new IllegalStateException("es down"))
        .when(down).deleteByDocument(any());

    new ChunkCleanupJob(documents, down, keywordIndex, Duration.ofHours(1), 10_000).run();
    assertThat(reload(failed).getChunksPurgedAt()).isNull();

    job().run();
    assertThat(reload(failed).getChunksPurgedAt()).isNotNull();
  }

  @Test
  void markChunksPurgedSkipsDocumentThatBecameSearchableAgain() {
    DocumentRecord document = save("revived", DocumentStatus.READY, Duration.ZERO);

    assertThat(documents.markChunksPurged(document.getId(), Instant.now())).isZero();
    assertThat(reload(document).getChunksPurgedAt()).isNull();
  }

  @Test
  void reprocessingClearsThePurgeMarker() {
    DocumentRecord document = save("reingest", DocumentStatus.FAILED, Duration.ZERO);
    assertThat(documents.markChunksPurged(document.getId(), Instant.now())).isOne();

    DocumentRecord reloaded = reload(document);
    reloaded.markProcessing();
    documents.saveAndFlush(reloaded);

    assertThat(reload(document).getChunksPurgedAt()).isNull();
  }

  private ChunkCleanupJob job() {
    // A large batch so documents from other tests sharing the database cannot crowd ours out.
    return new ChunkCleanupJob(documents, vectorStore, keywordIndex, Duration.ofHours(1), 10_000);
  }

  private DocumentRecord save(String name, DocumentStatus status, Duration age) {
    DocumentRecord document =
        new DocumentRecord(owner, name, "text/plain", 6, "0".repeat(40));
    ReflectionTestUtils.setField(document, "uploadedAt", Instant.now().minus(age));
    if (status == DocumentStatus.READY) {
      document.markReady();
    } else if (status == DocumentStatus.FAILED) {
      document.markFailed("ingestion failed");
    }
    return documents.saveAndFlush(document);
  }

  private DocumentRecord reload(DocumentRecord document) {
    return documents.findById(document.getId()).orElseThrow();
  }
}
