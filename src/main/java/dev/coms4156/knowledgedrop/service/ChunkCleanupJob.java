package dev.coms4156.knowledgedrop.service;

import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import dev.coms4156.knowledgedrop.retrieval.KeywordIndex;
import dev.coms4156.knowledgedrop.retrieval.VectorStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically removes chunks from Elasticsearch that belong to documents which must not be
 * searchable, so stale chunks cannot pile up and crowd ready ones out of retrieval's candidate
 * pool.
 *
 * <p>Ingestion and delete already remove chunks themselves, but that cleanup is lost when the
 * server stops mid-ingestion, when a queued {@code deleteChunks} task is dropped on restart, or
 * when Elasticsearch is down at delete time (those failures are only logged). Each run:
 *
 * <ol>
 *   <li>marks documents processing for longer than {@code knowledgedrop.cleanup.stuck-after} as
 *       failed, because their ingestion is gone and they would otherwise stay processing forever;
 *   <li>purges the chunks of up to {@code knowledgedrop.cleanup.batch-size} deleted or failed
 *       documents not purged yet, oldest first, and records each purge so it is not repeated.
 * </ol>
 *
 * <p>Purging stops at the first Elasticsearch failure, which usually means it is unavailable; the
 * remaining documents are retried on the next run. Deletes are idempotent, so purging a document
 * that already has no chunks is harmless.
 *
 * <p>Set {@code stuck-after} well above the longest real ingestion, including time spent queued.
 * If an ingestion that is still running is marked failed, it may finish afterwards and mark the
 * document ready again, possibly after its chunks were purged.
 *
 * <p>Runs on the cron {@code knowledgedrop.cleanup.cron} (every 10 minutes by default; {@code -}
 * disables it, as the test profile does).
 */
@Component
public class ChunkCleanupJob {

  /** Failure reason recorded on documents whose ingestion never finished. */
  static final String STUCK_REASON =
      "Ingestion did not finish (the server may have restarted); upload the document again";

  private static final Logger log = LoggerFactory.getLogger(ChunkCleanupJob.class);

  private final DocumentRepository documents;
  private final VectorStore vectorStore;
  private final KeywordIndex keywordIndex;
  private final Duration stuckAfter;
  private final int batchSize;

  /**
   * Creates the job.
   *
   * @param documents finds stale documents and records purges
   * @param vectorStore Elasticsearch vector store to purge
   * @param keywordIndex Elasticsearch keyword index to purge
   * @param stuckAfter how long a document may stay processing before it is marked failed
   * @param batchSize maximum documents purged per run
   * @throws IllegalArgumentException if {@code stuckAfter} or {@code batchSize} is not positive
   */
  public ChunkCleanupJob(
      DocumentRepository documents,
      VectorStore vectorStore,
      KeywordIndex keywordIndex,
      @Value("${knowledgedrop.cleanup.stuck-after:PT2H}") Duration stuckAfter,
      @Value("${knowledgedrop.cleanup.batch-size:100}") int batchSize) {
    if (stuckAfter == null || stuckAfter.isNegative() || stuckAfter.isZero()) {
      throw new IllegalArgumentException("knowledgedrop.cleanup.stuck-after must be positive");
    }
    if (batchSize <= 0) {
      throw new IllegalArgumentException("knowledgedrop.cleanup.batch-size must be positive");
    }
    this.documents = documents;
    this.vectorStore = vectorStore;
    this.keywordIndex = keywordIndex;
    this.stuckAfter = stuckAfter;
    this.batchSize = batchSize;
  }

  /** One scheduled run: fail stuck ingestions, then purge stale chunks. Never throws. */
  @Scheduled(cron = "${knowledgedrop.cleanup.cron:0 */10 * * * *}")
  public void run() {
    try {
      failStuckIngestions();
      purgeStaleChunks();
    } catch (RuntimeException e) {
      log.error("Chunk cleanup run failed; retrying on the next run", e);
    }
  }

  /**
   * Marks documents that have been processing for longer than {@code stuckAfter} as failed.
   *
   * @return the number of documents marked failed
   */
  int failStuckIngestions() {
    int failed = documents.failStuckProcessing(Instant.now().minus(stuckAfter), STUCK_REASON);
    if (failed > 0) {
      log.warn("Marked {} document(s) failed after processing for over {}", failed, stuckAfter);
    }
    return failed;
  }

  /**
   * Purges the chunks of one batch of deleted or failed documents and records each purge.
   *
   * @return the number of documents purged
   */
  int purgeStaleChunks() {
    List<DocumentRecord> stale = documents.findWithUnpurgedChunks(PageRequest.of(0, batchSize));
    int purged = 0;
    for (DocumentRecord document : stale) {
      try {
        vectorStore.deleteByDocument(document.getId());
        keywordIndex.deleteByDocument(document.getId());
      } catch (RuntimeException e) {
        log.warn("Could not purge chunks of document {}; stopping this run", document.getId(), e);
        break;
      }
      documents.markChunksPurged(document.getId(), Instant.now());
      purged++;
    }
    if (purged > 0) {
      log.info("Purged stale chunks of {} document(s)", purged);
    }
    return purged;
  }
}
