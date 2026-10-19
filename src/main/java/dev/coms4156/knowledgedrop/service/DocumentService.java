package dev.coms4156.knowledgedrop.service;

import dev.coms4156.knowledgedrop.api.dto.DocumentReadResponse;
import dev.coms4156.knowledgedrop.api.dto.DocumentStatusResponse;
import dev.coms4156.knowledgedrop.api.dto.DocumentSummary;
import dev.coms4156.knowledgedrop.api.dto.MessageResponse;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
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

  /**
   * Creates the service. For interacting with the database and the ingestion pipeline.
   *
   * @param documentRepository repository for storing and querying document records
   * @param ingestionService service that runs the ingestion pipeline for a document
   * @param fileStorage storage for the original uploaded files
   */
  public DocumentService(
      DocumentRepository documentRepository,
      IngestionService ingestionService,
      FileStorage fileStorage) {
    this.documentRepository = documentRepository;
    this.ingestionService = ingestionService;
    this.fileStorage = fileStorage;
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

    try {
      clientId = client.getId();
      fileName = file.getOriginalFilename();
      fileType = file.getContentType();
      fileSize = file.getSize();
    } catch (Exception e) {
      throw ApiException.badRequest(
          "Failed to extract information from request: " + e.getMessage());
    }

    if (fileSize > 10 * 1024 * 1024 * 1024) { 
      throw ApiException.CONTENT_TOO_LARGE("File size exceeds 10GB limit");
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
        deleteDocument(client.getId(), fileName);
      }
    } catch (Exception e) {
      throw ApiException.internalServerError(
          "Failed to check for existing documents: " + e.getMessage());
    }

    try {
      newDocument = new DocumentRecord(clientId, fileName, fileType, fileSize, sha1Hash);
      documentRepository.save(newDocument);
    } catch (Exception e) {
      throw ApiException.internalServerError(
          "Failed to create document record: " + e.getMessage());
    }


    ingestionService.ingest(newDocument.getId());
    return new DocumentStatusResponse(newDocument.getId(), newDocument.getStatus());
  }

  /**
   * Returns a document's stored content and metadata.
   *
   * <p>Only the client's non-deleted (deletedAt is null) document with this name is returned.
   *
   * @param client the authenticated caller
   * @param name the document name (the original filename it was created with)
   * @return content, document ID, filename, type, upload time, and status
   */
  public DocumentReadResponse read(ClientRecord client, String name) {
    // TODO: read: look up the non-deleted document for this client and name via
    //   DocumentRepository.findByOwnerIdAndNameAndDeletedAtIsNull(client.getId(), name); return 404
    //   with a reason if missing (soft-deleted documents count as missing).
    // TODO: read: load the stored content from file storage and map to DocumentReadResponse.
    throw ApiException.notImplemented("GET /knowledgeDrop/document/read");
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

  private void deleteDocument(UUID clientId, String name) {
    int updated = documentRepository.markDeletedByOwnerIdAndName(clientId, name, Instant.now());
    if (updated == 0) {
      throw ApiException.notFound("No non-deleted document found with name: " + clientId + "/" + name);
    }
  }
}
