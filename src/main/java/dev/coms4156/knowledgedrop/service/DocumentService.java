package dev.coms4156.knowledgedrop.service;

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
 * Document management: create (which also replaces an existing document of the same name), read,
 * delete, and list.
 *
 * <p>TODO: documents: inject DocumentRepository, IngestionService, and a file-storage component
 * once they exist. Every lookup must be scoped to the calling client's documents.
 */
@Service
public class DocumentService {

  /**
   * Stores a document and starts ingesting it.
   *
   * <p>The document name is the file's original filename and the document type is the file's
   * content type. If the client already has a non-deleted document with that name, its content is
   * replaced and re-ingested under the same document ID.
   *
   * @param client the authenticated caller
   * @param file the uploaded binary file part
   * @return the document ID and its current status
   */
  public DocumentStatusResponse create(ClientRecord client, MultipartFile file) {
    // TODO: create: name = file.getOriginalFilename(); strip any directory path (for example
    //   Paths.get(name).getFileName()) and return 400 if it is null or blank or longer than 255.
    // TODO: create: type = file.getContentType(); if it is missing or application/octet-stream,
    //   infer it from the filename extension. Return 400 if the type is not an approved MIME
    //   type, or if the file is empty.
    // TODO: create: look up a non-deleted document (deletedAt is null) with this name for this
    //   client. If one exists, replace it: keep the same document ID, call
    //   IngestionService.deleteChunks, store the new content, and set status = processing.
    //   Otherwise create a new DocumentRecord (status = processing).
    // TODO: create: store the original bytes, then compute their size and SHA-1 hash with
    //   fingerprint(file) and record them with DocumentRecord.recordContent(bytes, sha1Hex).
    // TODO: create: kick off IngestionService.ingest(documentId) in the background and return the
    //   document ID with status processing right away.
    throw ApiException.notImplemented("POST /knowledgeDrop/document/create");
  }

  /**
   * Returns a document's stored content and metadata.
   *
   * @param client the authenticated caller
   * @param ref the document to read
   * @return content, document ID, filename, type, upload time, and status
   */
  public DocumentReadResponse read(ClientRecord client, DocumentRef ref) {
    // TODO: read: locate the document, verify ownership, return 404 with a reason if missing.
    // TODO: read: load the stored content from file storage and map to DocumentReadResponse.
    throw ApiException.notImplemented("GET /knowledgeDrop/document/read");
  }

  /**
   * Deletes a document and everything derived from it.
   *
   * @param client the authenticated caller
   * @param ref the document to delete
   * @return confirmation message
   */
  public MessageResponse delete(ClientRecord client, DocumentRef ref) {
    // TODO: delete: locate the document and verify ownership.
    // TODO: delete: delete the stored file, the Elasticsearch chunk entries and embeddings, and
    //   finally the document record.
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
}
