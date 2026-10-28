package dev.coms4156.knowledgedrop.service;

import dev.coms4156.knowledgedrop.config.KnowledgeDropProperties;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import dev.coms4156.knowledgedrop.retrieval.EmbeddingClient;
import dev.coms4156.knowledgedrop.retrieval.KeywordIndex;
import dev.coms4156.knowledgedrop.retrieval.RankedChunk;
import dev.coms4156.knowledgedrop.retrieval.VectorStore;
import dev.coms4156.knowledgedrop.storage.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * The ingestion pipeline used by create, including when create replaces an existing document.
 *
 * <p>Runs on the {@code ingestionExecutor} thread pool so create can return immediately with
 * status {@code processing}. Nothing receives an exception thrown here, so every failure is logged
 * and recorded on the document as status {@code failed} with a reason.
 *
 * <p>Text is extracted with Apache Tika (no OCR) and split into fixed-size character chunks that
 * overlap, sized by {@code knowledgedrop.ingestion.chunk-size} and {@code chunk-overlap}.
 *
 * <p>Chunks are written to Elasticsearch only after every embedding has been computed, and removed
 * again if any write fails, so a document that ends up {@code failed} leaves no searchable chunks
 * (see {@link KeywordIndex}).
 */
@Service
public class IngestionService {

  private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

  private final DocumentRepository documentRepository;
  private final FileStorage fileStorage;
  private final KeywordIndex keywordIndex;
  private final VectorStore vectorStore;
  private final EmbeddingClient embeddings;
  private final Tika tika = new Tika();
  private final int chunkSize;
  private final int chunkOverlap;

  /**
   * Creates the service.
   *
   * @param documentRepository loads documents and records their final ingestion status
   * @param fileStorage reads the original uploaded file for text extraction
   * @param keywordIndex Elasticsearch BM25 index for chunk text
   * @param vectorStore Elasticsearch kNN vector store for chunk embeddings
   * @param embeddings turns chunk text into embedding vectors
   * @param properties supplies chunk size and overlap from {@code knowledgedrop.ingestion.*}
   * @throws IllegalStateException if the chunk settings would not make progress through the text
   */
  public IngestionService(
      DocumentRepository documentRepository,
      FileStorage fileStorage,
      KeywordIndex keywordIndex,
      VectorStore vectorStore,
      EmbeddingClient embeddings,
      KnowledgeDropProperties properties) {
    this.documentRepository = documentRepository;
    this.fileStorage = fileStorage;
    this.keywordIndex = keywordIndex;
    this.vectorStore = vectorStore;
    this.embeddings = embeddings;
    this.chunkSize = properties.ingestion().chunkSize();
    this.chunkOverlap = properties.ingestion().chunkOverlap();
    if (chunkSize <= 0 || chunkOverlap < 0 || chunkOverlap >= chunkSize) {
      throw new IllegalStateException(
          "knowledgedrop.ingestion requires chunk-size > 0 and 0 <= chunk-overlap < chunk-size,"
              + " got chunk-size=" + chunkSize + ", chunk-overlap=" + chunkOverlap);
    }
  }

  /**
   * Extracts, chunks, embeds, and indexes a document, then sets its final status.
   *
   * <p>Does nothing if the document is missing or soft-deleted. Otherwise marks it {@code ready},
   * or {@code failed} with a reason if the file cannot be read or parsed, contains no text,
   * cannot be embedded, or cannot be indexed.
   *
   * @param documentId the document to ingest
   */
  @Async("ingestionExecutor")
  public void ingest(UUID documentId) {
    DocumentRecord document;
    try {
      Optional<DocumentRecord> documentOpt =
          documentRepository.findByIdAndDeletedAtIsNull(documentId);
      if (documentOpt.isEmpty()) {
        log.warn("Ingestion skipped: document {} not found or deleted", documentId);
        return;
      }
      document = documentOpt.get();
    } catch (RuntimeException e) {
      log.error("Ingestion could not load document {}", documentId, e);
      return;
    }

    UUID ownerId = document.getOwnerId();
    List<RankedChunk> chunks;
    try (InputStream content = fileStorage.open(ownerId, document.getName())) {
      chunks = extractChunks(document, content);
    } catch (IOException e) {
      log.error("Ingestion could not read the file for document {}", documentId, e);
      markFailed(document, "Could not read file: " + e.getMessage());
      return;
    } catch (RuntimeException e) {
      log.error("Ingestion could not extract text from document {}", documentId, e);
      markFailed(document, "Could not extract text: " + e.getMessage());
      return;
    }
    // a ready document with no chunks could never be found, e.g. a scanned PDF without OCR
    if (chunks.stream().allMatch(chunk -> chunk.text().isBlank())) {
      log.warn("Ingestion found no text in document {}", documentId);
      markFailed(document, "No text could be extracted from the file");
      return;
    }

    List<float[]> vectors;
    try {
      vectors = embeddings.embedAll(chunks.stream().map(RankedChunk::text).toList());
    } catch (RuntimeException e) {
      log.error("Ingestion could not embed chunks of document {}", documentId, e);
      markFailed(document, "Could not create embeddings: " + e.getMessage());
      return;
    }

    try {
      vectorStore.index(ownerId, documentId, chunks, vectors);
      keywordIndex.index(ownerId, documentId, chunks);
      document.markReady();
      documentRepository.save(document);
      log.info("Ingested document {} ({} chunks)", documentId, chunks.size());
    } catch (RuntimeException e) {
      log.error("Ingestion could not index document {}", documentId, e);
      deleteChunks(documentId);
      markFailed(document, "Could not index document: " + e.getMessage());
    }
  }

  /**
   * Turns a document's file content into ordered chunks of at most {@code chunkSize} characters,
   * consecutive chunks sharing {@code chunkOverlap} characters.
   *
   * @param document the document being ingested
   * @param content the original uploaded file; closed before this method returns
   * @return the chunks in document order, with rank 0 and score 0
   * @throws IOException if the file cannot be read or parsed
   */
  private List<RankedChunk> extractChunks(DocumentRecord document, InputStream content)
      throws IOException {
    List<RankedChunk> chunks = new ArrayList<>();
    char[] buffer = new char[chunkSize];
    int filled = 0;
    try (Reader reader = openText(document, content)) {
      int read;
      while ((read = reader.read(buffer, filled, chunkSize - filled)) != -1) {
        filled += read;
        if (filled == chunkSize) {
          chunks.add(toChunk(document, chunks.size(), new String(buffer, 0, filled)));
          // carry the last chunkOverlap characters into the next chunk
          System.arraycopy(buffer, chunkSize - chunkOverlap, buffer, 0, chunkOverlap);
          filled = chunkOverlap;
        }
      }
    } catch (IOException | RuntimeException e) {
      // close the file before rethrowing, keeping the original failure as the one reported
      try {
        content.close();
      } catch (IOException closeFailure) {
        e.addSuppressed(closeFailure);
      }
      throw e;
    }
    content.close();
    // the final partial chunk, unless it holds only overlap already in the previous chunk
    if (filled > (chunks.isEmpty() ? 0 : chunkOverlap)) {
      chunks.add(toChunk(document, chunks.size(), new String(buffer, 0, filled)));
    }
    return chunks;
  }

  /**
   * Opens the text of an uploaded file as a stream. Tika detects the format (PDF, Word, Excel,
   * PowerPoint, plain text, and so on) from the content, using the file name as a hint, and parses
   * in a background thread as the reader is consumed.
   *
   * @param document the document being ingested, whose name helps detect the format
   * @param content the original uploaded file; closed by the caller
   * @return a reader over the document's text; the caller must close it. Parse failures are thrown
   *     as {@link IOException} from its read methods.
   * @throws IOException if the file cannot be read
   */
  private Reader openText(DocumentRecord document, InputStream content) throws IOException {
    Metadata metadata = new Metadata();
    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, document.getName());
    return tika.parse(content, metadata);
  }

  /**
   * Builds the chunk at {@code position} in the document, with ID documentId-position. Page is 0
   * (no page) because chunking runs over the whole extracted text, not page by page.
   */
  private RankedChunk toChunk(DocumentRecord document, int position, String text) {
    return new RankedChunk(document.getId() + "-" + position, document.getId(), 0, text, 0, 0);
  }

  /** Records an ingestion failure on the document; if that write fails, logs and rethrows. */
  private void markFailed(DocumentRecord document, String reason) {
    try {
      document.markFailed(reason);
      documentRepository.save(document);
    } catch (RuntimeException e) {
      log.error("Could not mark document {} as failed", document.getId(), e);
      throw e;
    }
  }

  /**
   * Removes a document's chunks and embeddings from Elasticsearch. Both deletes are attempted even
   * if the first fails; failures are logged, not thrown.
   *
   * <p>Runs on the {@code ingestionExecutor} thread pool when called from another bean, so delete
   * and replace requests return without waiting for Elasticsearch. Calls from inside this class
   * (cleanup in {@link #ingest}) bypass the Spring proxy and run synchronously, so a failed
   * document's chunks are gone before it is marked failed.
   *
   * @param documentId the document whose chunks should be deleted
   */
  @Async("ingestionExecutor")
  public void deleteChunks(UUID documentId) {
    try {
      vectorStore.deleteByDocument(documentId);
    } catch (RuntimeException e) {
      log.error("Could not delete vector chunks of document {}", documentId, e);
    }
    try {
      keywordIndex.deleteByDocument(documentId);
    } catch (RuntimeException e) {
      log.error("Could not delete keyword chunks of document {}", documentId, e);
    }
  }
}
