package dev.coms4156.knowledgedrop.service;

import dev.coms4156.knowledgedrop.api.dto.CreateDocumentRequest;
import dev.coms4156.knowledgedrop.api.dto.DocumentReadResponse;
import dev.coms4156.knowledgedrop.api.dto.DocumentStatusResponse;
import dev.coms4156.knowledgedrop.api.dto.DocumentSummary;
import dev.coms4156.knowledgedrop.api.dto.MessageResponse;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Document management: create, update, read, delete, and list.
 *
 * <p>TODO(documents): inject DocumentRepository, IngestionService, and a file-storage component
 * once they exist. Every lookup must be scoped to the calling client's documents.
 */
@Service
public class DocumentService {

  /**
   * Stores a document and starts ingesting it.
   *
   * @param client the authenticated caller
   * @param metadata name, type, and optional raw text (the JSON part of the request)
   * @param file the uploaded binary file part, or null if {@code metadata.text()} is used
   * @return the document ID and its current status
   */
  public DocumentStatusResponse create(
      ClientRecord client, CreateDocumentRequest metadata, MultipartFile file) {
    // TODO(create): validate: type via DocumentType.fromValue (400 if unsupported), exactly one of
    //   file / metadata.text(), and content not empty (400). Reject a duplicate name for this
    //   client (409).
    // TODO(create): store the original bytes and create a DocumentRecord (status = processing).
    // TODO(create): kick off IngestionService.ingest(documentId) in the background and return the
    //   document ID with status processing right away.
    throw ApiException.notImplemented("POST /knowledgeDrop/create");
  }

  /**
   * Replaces a document's content and re-runs ingestion, keeping the same document ID.
   *
   * @param client the authenticated caller
   * @param ref the document to update (by ID or name)
   * @param text new raw text, or null if {@code file} is used
   * @param file new uploaded binary file part, or null if {@code text} is used
   * @return the document ID and its current status
   */
  public DocumentStatusResponse update(
      ClientRecord client, DocumentRef ref, String text, MultipartFile file) {
    // TODO(update): locate the document and verify it belongs to the client (404 / not owned).
    // TODO(update): require exactly one of file / text, not empty (400).
    // TODO(update): delete the existing chunks from Redis and Elasticsearch
    //   (IngestionService.deleteChunks).
    // TODO(update): store the new content, set status = processing, re-run ingestion.
    throw ApiException.notImplemented("PUT /knowledgeDrop/update");
  }

  /**
   * Returns a document's stored content and metadata.
   *
   * @param client the authenticated caller
   * @param ref the document to read
   * @return content, document ID, filename, type, upload time, and status
   */
  public DocumentReadResponse read(ClientRecord client, DocumentRef ref) {
    // TODO(read): locate the document, verify ownership, return 404 with a reason if missing.
    // TODO(read): load the stored content from file storage and map to DocumentReadResponse.
    throw ApiException.notImplemented("GET /knowledgeDrop/read");
  }

  /**
   * Deletes a document and everything derived from it.
   *
   * @param client the authenticated caller
   * @param ref the document to delete
   * @return confirmation message
   */
  public MessageResponse delete(ClientRecord client, DocumentRef ref) {
    // TODO(delete): locate the document and verify ownership.
    // TODO(delete): delete the stored file, the Redis chunk embeddings, the Elasticsearch
    //   entries, and finally the document record.
    throw ApiException.notImplemented("DELETE /knowledgeDrop/delete");
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
    // TODO(list): parse status via DocumentStatus.fromValue; apply defaults for limit/offset.
    // TODO(list): query DocumentRepository (findByOwnerId / findByOwnerIdAndStatus). The
    //   repository is page-based, so convert limit/offset into a Pageable.
    throw ApiException.notImplemented("GET /knowledgeDrop/list");
  }
}
