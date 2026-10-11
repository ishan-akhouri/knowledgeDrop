package dev.coms4156.knowledgedrop.service;

import dev.coms4156.knowledgedrop.exception.ApiException;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * The ingestion pipeline shared by create and update.
 *
 * <p>Runs on the {@code ingestionExecutor} thread pool so create can return immediately with
 * status {@code processing}.
 */
@Service
public class IngestionService {

  /**
   * Extracts, chunks, embeds, and indexes a document, then sets its final status.
   *
   * @param documentId the document to ingest
   */
  @Async("ingestionExecutor")
  public void ingest(UUID documentId) {
    // TODO(ingestion): extract text: direct read for text files, PDF text extraction for pdf (for
    //   example Apache PDFBox), OCR for images (for example Tess4J). Keep page numbers.
    // TODO(ingestion): split the text into chunks using knowledgedrop.ingestion.* settings.
    // TODO(ingestion) [DEFERRED: embedding provider undecided]: create an embedding per chunk and
    //   insert it into Redis.
    // TODO(ingestion): index each chunk in Elasticsearch for BM25 keyword search.
    // TODO(ingestion): set status = ready, or failed with a reason if any step throws.
    throw ApiException.notImplemented("document ingestion pipeline");
  }

  /**
   * Removes a document's chunks from Redis and Elasticsearch.
   *
   * @param documentId the document whose chunks should be deleted
   */
  public void deleteChunks(UUID documentId) {
    // TODO(update, delete, clientDrop): delete all chunk embeddings (Redis) and chunk entries
    //   (Elasticsearch) belonging to documentId.
    throw ApiException.notImplemented("chunk deletion");
  }
}
