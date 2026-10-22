package dev.coms4156.knowledgedrop.service;

import dev.coms4156.knowledgedrop.api.dto.DocumentStatusResponse;
import dev.coms4156.knowledgedrop.api.dto.DocumentSummary;
import dev.coms4156.knowledgedrop.api.dto.MessageResponse;
import dev.coms4156.knowledgedrop.api.dto.ReadDocument;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.model.DocumentStatus;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import dev.coms4156.knowledgedrop.storage.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.AbstractPageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Document management: create, read, delete, and list.
 *
 * <p>A document is identified by its name (the uploaded file's original filename), which is unique
 * per client among non-deleted documents. Deletion is soft: the record is kept with
 * {@code deletedAt} set, and every lookup here ignores soft-deleted records. Creating a document
 * whose name is already in use soft-deletes the old document and stores the upload as a new one.
 *
 * <p>All methods are scoped to the authenticated client, so one client can never see or change
 * another client's documents.
 */
@Service
public class DocumentService {

  private final DocumentRepository documentRepository;
  private final IngestionService ingestionService;
  private final FileStorage fileStorage;
  private final TransactionTemplate transactionTemplate;

  /**
   * Creates the service. For interacting with the database and the ingestion pipeline.
   *
   * @param documentRepository repository for storing and querying document records
   * @param ingestionService service that runs the ingestion pipeline for a document
   * @param fileStorage storage for the original uploaded files
   * @param transactionManager transaction manager used to run multi-step database updates
   *     atomically
   */
  public DocumentService(
      DocumentRepository documentRepository,
      IngestionService ingestionService,
      FileStorage fileStorage,
      PlatformTransactionManager transactionManager) {
    this.documentRepository = documentRepository;
    this.ingestionService = ingestionService;
    this.fileStorage = fileStorage;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  /**
   * Stores a document and starts ingesting it.
   *
   * <p>The document name is the file's original filename and the document type is the file's
   * content type. The file is written to {@link FileStorage} (replacing any earlier file stored
   * under the same client and name) and its SHA-1 hash is recorded. If the client already has a
   * non-deleted document with that name, that document is soft-deleted and the upload is stored
   * as a new document with a new document ID, in one transaction, so the name always refers to
   * the latest upload. Once that transaction has committed, the replaced document's chunks are
   * removed from the search indexes. Ingestion of the new document is then started in the
   * background, so the returned status is normally {@code processing}.
   *
   * @param client the authenticated caller
   * @param file the uploaded binary file part
   * @return the new document ID and its current status
   * @throws ApiException 400 if the file is empty or has no filename, 413 if it is larger than
   *     10 GB, or 500 if the file or its record cannot be saved
   */
  public DocumentStatusResponse create(ClientRecord client, MultipartFile file) {
    UUID clientId;
    String fileName;
    String fileType;
    long fileSize;
    String sha1Hash;
    DocumentRecord newDocument;
    DocumentRecord oldDocument = null;

    try {
      clientId = client.getId();
      fileName = file.getOriginalFilename();
      fileType = file.getContentType();
      fileSize = file.getSize();
    } catch (Exception e) {
      throw ApiException.badRequest(
          "Failed to extract information from request: " + e.getMessage());
    }

    if (fileSize > 10L * 1024 * 1024 * 1024) { 
      throw ApiException.contentTooLarge("File size exceeds 10GB limit");
    }
    if (fileSize == 0) {
      throw ApiException.badRequest("File is empty");
    }
    if (fileName == null || fileName.isBlank()) {
      throw ApiException.badRequest("File name is missing");
    }

    try (InputStream in = file.getInputStream()) {
      fileStorage.save(client.getId(), fileName, in);
    } catch (Exception e) {
      throw ApiException.internalServerError(
          "Failed to save file content: " + e.getMessage());
    }

    try (InputStream in = file.getInputStream()) {
      sha1Hash = computeSha1Hash(in);
    } catch (Exception e) {
      throw ApiException.internalServerError(
          "Failed to process file content: " + e.getMessage());
    }

    try {
      Optional<DocumentRecord> existing =
          documentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(clientId, fileName);
      if (existing.isPresent()) {
        oldDocument = existing.get();
      }
    } catch (Exception e) {
      throw ApiException.internalServerError(
          "Failed to check for existing documents: " + e.getMessage());
    }

    try {
      newDocument = new DocumentRecord(clientId, fileName, fileType, fileSize, sha1Hash);
    } catch (Exception e) {
      throw ApiException.internalServerError(
          "Failed to create document record: " + e.getMessage());
    }
    if (oldDocument != null) {
      replaceDocument(oldDocument, newDocument);
    } else {
      try {
        documentRepository.save(newDocument);
      } catch (Exception e) {
        throw ApiException.internalServerError(
            "Failed to save new document record: " + e.getMessage());
      }
    }


    UUID documentId = newDocument.getId();
    DocumentStatus status = newDocument.getStatus();

    ingestionService.ingest(documentId);

    return new DocumentStatusResponse(documentId, status);
  }

  /**
   * Returns a document's stored content and metadata.
   *
   * <p>Only the client's non-deleted (deletedAt is null) document with this name is returned.
   * The stored file's SHA-1 hash is recomputed and compared with the hash recorded at upload
   * before any content is returned.
   *
   * @param client the authenticated caller
   * @param fileName the document name (the original filename it was created with)
   * @return the document's file name, file type, file size, and an input stream over its content;
   *     the caller must close the stream
   * @throws ApiException 404 if there is no non-deleted document with this name, or 500 if the
   *     stored file cannot be read or its hash does not match
   */
  public ReadDocument read(ClientRecord client, String fileName) {
    UUID clientId;
    DocumentRecord document;
    try {
      clientId = client.getId();
    } catch (Exception e) {
      throw ApiException.badRequest(
          "Failed to extract information from request: " + e.getMessage());
    }

    document = documentRepository
        .findByOwnerIdAndNameAndDeletedAtIsNull(clientId, fileName)
        .orElseThrow(() -> ApiException.notFound("Document not found or has been deleted"));

    // Stream #1: used only to verify the content hash; closed automatically.
    try (InputStream hashStream = fileStorage.open(clientId, fileName)) {
      String computedHash = computeSha1Hash(hashStream);
      if (!computedHash.equals(document.getContentHash())) {
        throw ApiException.internalServerError("File content hash mismatch");
      }
    } catch (ApiException e) {
      throw e;
    } catch (IOException e) {
      throw ApiException.internalServerError("Failed to compute file hash: " + e.getMessage());
    } catch (Exception e) {
      throw ApiException.internalServerError("Unexpected error: " + e.getMessage());
    }

    // Stream #2: returned to the caller, who is responsible for closing it.
    InputStream fileStream;
    try {
      fileStream = fileStorage.open(clientId, fileName);
    } catch (Exception e) {
      throw ApiException.internalServerError("Failed to reopen file content: " + e.getMessage());
    }

    return new ReadDocument(
        document.getName(), document.getType(), document.getFileBytes(), fileStream);
  }

  /**
   * Soft-deletes a document: the record is kept but marked as deleted, and its chunks are
   * removed from the search indexes via {@link IngestionService#deleteChunks}. The stored file is
   * not removed. The name becomes free for a later {@link #create}.
   *
   * @param client the authenticated caller
   * @param fileName the document name (the original filename it was created with)
   * @return confirmation message
   * @throws ApiException 404 if there is no non-deleted document with this name, or 500 if the
   *     lookup or the record update fails
   */
  public MessageResponse delete(ClientRecord client, String fileName) {
    UUID clientId;
    DocumentRecord document;

    try {
      clientId = client.getId();
    } catch (Exception e) {
      throw ApiException.badRequest(
          "Failed to extract information from request: " + e.getMessage());
    }

    try {
      Optional<DocumentRecord> documentOptional = documentRepository
          .findByOwnerIdAndNameAndDeletedAtIsNull(clientId, fileName);

      if (documentOptional.isEmpty()) {
        throw ApiException.notFound("Document not found or has been deleted");
      }
      document = documentOptional.get();

    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw ApiException.internalServerError(
          "Failed to check for existing documents: " + e.getMessage());
    }

    try {
      document.setDeletedAt(Instant.now());
      documentRepository.save(document);
    } catch (Exception e) {
      throw ApiException.internalServerError(
          "Failed to mark document as deleted: " + e.getMessage());
    }

    ingestionService.deleteChunks(document.getId());

    return new MessageResponse("Document '" + fileName + "' has been deleted");
  }

  /**
   * Lists the caller's non-deleted documents, newest upload first.
   *
   * @param client the authenticated caller
   * @param status optional status filter: "processing", "ready", "failed" or "all"; defaults to
   *     "all"
   * @param limit optional page size; missing, zero or negative returns all remaining documents
   * @param offset optional number of documents to skip; missing or negative is treated as 0
   * @return matching documents, or an empty list if the client has none
   * @throws ApiException 400 if {@code status} is not a recognized value
   */
  public List<DocumentSummary> list(
      ClientRecord client, String status, Integer limit, Integer offset) {
    UUID clientId;
    try {
      clientId = client.getId();
    } catch (Exception e) {
      throw ApiException.badRequest(
          "Failed to extract information from request: " + e.getMessage());
    }
    int pageSize = (limit == null || limit <= 0) ? Integer.MAX_VALUE : limit;
    long skip = (offset == null || offset < 0) ? 0 : offset;
    Pageable pageable = new OffsetPageRequest(skip, pageSize);

    Page<DocumentRecord> page;
    if (status == null || status.isBlank() || status.trim().equalsIgnoreCase("all")) {
      page = documentRepository.findByOwnerIdAndDeletedAtIsNull(clientId, pageable);
    } else {
      DocumentStatus parsedStatus = DocumentStatus.fromValue(status);
      page = documentRepository.findByOwnerIdAndStatusAndDeletedAtIsNull(
          clientId, parsedStatus, pageable);
    }
    return page.getContent().stream()
        .map(d -> new DocumentSummary(
            d.getId(), d.getName(), d.getType(), d.getUploadedAt(), d.getStatus()))
        .toList();
  }

  /**
   * A {@link Pageable} addressed by an arbitrary row offset rather than a page number, since the
   * API's {@code offset} need not be a multiple of {@code limit}. Newest uploads come first.
   */
  private static final class OffsetPageRequest extends AbstractPageRequest {
    private static final long serialVersionUID = 1L;
    private static final Sort SORT = Sort.by(Sort.Direction.DESC, "uploadedAt");

    private final long offset;

    OffsetPageRequest(long offset, int size) {
      super(0, size);
      this.offset = offset;
    }

    @Override
    public long getOffset() {
      return offset;
    }

    @Override
    public Sort getSort() {
      return SORT;
    }

    @Override
    public Pageable next() {
      return new OffsetPageRequest(offset + getPageSize(), getPageSize());
    }

    @Override
    public Pageable previous() {
      return new OffsetPageRequest(Math.max(0, offset - getPageSize()), getPageSize());
    }

    @Override
    public Pageable first() {
      return new OffsetPageRequest(0, getPageSize());
    }

    @Override
    public Pageable withPage(int pageNumber) {
      return new OffsetPageRequest((long) pageNumber * getPageSize(), getPageSize());
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof OffsetPageRequest that
          && offset == that.offset
          && getPageSize() == that.getPageSize();
    }

    @Override
    public int hashCode() {
      return Objects.hash(offset, getPageSize());
    }
  }

  /**
   * Computes the SHA-1 digest of a stream by reading it in chunks, so large files are never held
   * in memory. The stream is read to the end but not closed.
   *
   * @param in the content to hash
   * @return the digest as 40 lowercase hexadecimal characters
   * @throws IOException if the stream cannot be read
   */
  private static String computeSha1Hash(InputStream in) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 is not available", e);
    }
  }

  /**
   * Soft-deletes the old document and saves the new one in a single transaction. If either save
   * fails, both are rolled back, so the old document is never left deleted without a replacement.
   * The transaction is committed before this method returns, and only after the commit are the
   * old document's chunks removed via {@link IngestionService#deleteChunks}.
   *
   * @param oldDocument the existing document to soft-delete
   * @param newDocument the new document that replaces it
   * @throws ApiException if either save fails (both changes are rolled back; the old document's
   *     chunks are left untouched)
   */
  private void replaceDocument(DocumentRecord oldDocument, DocumentRecord newDocument) {
    try {
      transactionTemplate.executeWithoutResult(status -> {
        oldDocument.setDeletedAt(Instant.now());
        documentRepository.save(oldDocument);
        documentRepository.save(newDocument);
      });
    } catch (Exception e) {
      throw ApiException.internalServerError(
          "Failed to update document records: " + e.getMessage());
    }
    ingestionService.deleteChunks(oldDocument.getId());
  }

}
