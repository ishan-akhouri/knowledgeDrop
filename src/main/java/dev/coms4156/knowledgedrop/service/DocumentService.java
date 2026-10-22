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
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Document management: create (a same-name upload soft-deletes the old document and creates a new
 * one), read, delete, and list.
 * 
 * <p>All methods are scoped to the authenticated client, so one client can never see another
 *
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
   * content type. If the client already has a non-deleted document with that name, that document
   * is soft-deleted and the upload is stored as a new document with a new document ID, so the
   * name always refers to the latest upload.
   *
   * @param client the authenticated caller
   * @param file the uploaded binary file part
   * @return the document ID and its current status
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
   *
   * @param client the authenticated caller
   * @param fileName the document name (the original filename it was created with)
   * @return the document's file name, file type, file size, and an input stream over its content
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
   * Soft-deletes a document: the record is kept but marked as deleted.
   *
   * @param client the authenticated caller
   * @param name the document name (the original filename it was created with)
   * @return confirmation message
   */
  public MessageResponse delete(ClientRecord client, String name) {
    // TODO: delete: look up the non-deleted document for this client and name via
    //   DocumentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(client.getId(), name); return 404
    //   with a reason if missing.
    // TODO: delete: mark it deleted with setDeletedAt(Instant.now()) and save the record. The
    //   name then becomes free for create to reuse with a new document.
    // TODO: delete: remove the document's Elasticsearch chunk entries and embeddings so a deleted
    //   document never appears in search results.
    throw ApiException.notImplemented("DELETE /knowledgeDrop/document/delete");
  }

  /**
   * Lists the caller's documents.
   *
   * @param client the authenticated caller
   * @param status optional status filter: "processing", "ready", or "failed"
   * @param limit optional page size
   * @param offset optional number of documents to skip
   * @return matching documents, or an empty list if the client has none
   */
  public List<DocumentSummary> list(
      ClientRecord client, String status, Integer limit, Integer offset) {
    // TODO: list: parse status via DocumentStatus.fromValue; apply defaults for limit/offset.
    // TODO: list: query DocumentRepository (findByOwnerId / findByOwnerIdAndStatus). The
    //   repository is page-based, so convert limit/offset into a Pageable.
    throw ApiException.notImplemented("GET /knowledgeDrop/document/list");
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
   * The transaction is committed before this method returns.
   *
   * @param oldDocument the existing document to soft-delete
   * @param newDocument the new document that replaces it
   * @throws ApiException if either save fails (both changes are rolled back)
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
  }

}
