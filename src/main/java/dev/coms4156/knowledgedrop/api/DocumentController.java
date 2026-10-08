package dev.coms4156.knowledgedrop.api;

import dev.coms4156.knowledgedrop.api.dto.CreateDocumentRequest;
import dev.coms4156.knowledgedrop.api.dto.DocumentReadResponse;
import dev.coms4156.knowledgedrop.api.dto.DocumentStatusResponse;
import dev.coms4156.knowledgedrop.api.dto.DocumentSummary;
import dev.coms4156.knowledgedrop.api.dto.MessageResponse;
import dev.coms4156.knowledgedrop.api.dto.UpdateDocumentRequest;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.security.AuthInterceptor;
import dev.coms4156.knowledgedrop.service.DocumentRef;
import dev.coms4156.knowledgedrop.service.DocumentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Document management endpoints. All are TODO: the controller wiring, parameters, and status codes
 * are in place; the logic lives in {@link DocumentService}.
 *
 * <p>Create and update take {@code multipart/form-data} with two parts: a JSON {@code metadata}
 * part (content type {@code application/json}) and the document itself as a binary {@code file}
 * part. Read and delete identify the document by the {@code documentId} or {@code documentName}
 * query parameter; update takes the same two fields in its metadata part.
 */
@RestController
@RequestMapping("/knowledgeDrop")
public class DocumentController {

  private final DocumentService documentService;

  /**
   * Creates the controller.
   *
   * @param documentService document management logic
   */
  public DocumentController(DocumentService documentService) {
    this.documentService = documentService;
  }

  /** Uploads a document (JSON metadata + binary file) and starts ingestion. TODO(create). */
  @PostMapping(path = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<DocumentStatusResponse> create(
      @RequestAttribute(AuthInterceptor.CLIENT_ATTRIBUTE) ClientRecord client,
      @Valid @RequestPart("metadata") CreateDocumentRequest metadata,
      @RequestPart(value = "file", required = false) MultipartFile file) {
    DocumentStatusResponse body = documentService.create(client, metadata, file);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
  }

  /** Replaces a document's content (JSON metadata + binary file); re-ingests it. TODO(update). */
  @PutMapping(path = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public DocumentStatusResponse update(
      @RequestAttribute(AuthInterceptor.CLIENT_ATTRIBUTE) ClientRecord client,
      @RequestPart("metadata") UpdateDocumentRequest metadata,
      @RequestPart(value = "file", required = false) MultipartFile file) {
    DocumentRef ref = DocumentRef.of(metadata.documentId(), metadata.documentName());
    return documentService.update(client, ref, metadata.text(), file);
  }

  /** Returns a document's content and metadata. TODO(read). */
  @GetMapping("/read")
  public DocumentReadResponse read(
      @RequestAttribute(AuthInterceptor.CLIENT_ATTRIBUTE) ClientRecord client,
      @RequestParam(value = "documentId", required = false) UUID documentId,
      @RequestParam(value = "documentName", required = false) String documentName) {
    return documentService.read(client, DocumentRef.of(documentId, documentName));
  }

  /** Deletes a document and all of its chunks. TODO(delete). */
  @DeleteMapping("/delete")
  public MessageResponse delete(
      @RequestAttribute(AuthInterceptor.CLIENT_ATTRIBUTE) ClientRecord client,
      @RequestParam(value = "documentId", required = false) UUID documentId,
      @RequestParam(value = "documentName", required = false) String documentName) {
    return documentService.delete(client, DocumentRef.of(documentId, documentName));
  }

  /** Lists the caller's documents, optionally filtered and paginated. TODO(list). */
  @GetMapping("/list")
  public List<DocumentSummary> list(
      @RequestAttribute(AuthInterceptor.CLIENT_ATTRIBUTE) ClientRecord client,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "limit", required = false) Integer limit,
      @RequestParam(value = "offset", required = false) Integer offset) {
    return documentService.list(client, status, limit, offset);
  }
}
