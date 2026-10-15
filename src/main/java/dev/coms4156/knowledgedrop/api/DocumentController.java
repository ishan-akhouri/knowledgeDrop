package dev.coms4156.knowledgedrop.api;

import dev.coms4156.knowledgedrop.api.dto.DocumentReadResponse;
import dev.coms4156.knowledgedrop.api.dto.DocumentStatusResponse;
import dev.coms4156.knowledgedrop.api.dto.DocumentSummary;
import dev.coms4156.knowledgedrop.api.dto.MessageResponse;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.security.AuthInterceptor;
import dev.coms4156.knowledgedrop.service.DocumentService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
 * <p>Create takes {@code multipart/form-data} with a single binary {@code file} part; the document
 * name is the file's original filename and its type is the file's content type. If the client
 * already has a non-deleted document with that name, create replaces its content and re-ingests
 * it. Read and delete identify the document by the {@code documentName} query parameter.
 *
 * <p>All paths are served under the {@code /knowledgeDrop/document} prefix, for example
 * {@code POST /knowledgeDrop/document/create}.
 */
@RestController
@RequestMapping("/knowledgeDrop/document")
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

  /**
   * Uploads a document (a single binary file part) and starts ingestion, replacing the content of
   * an existing document with the same name. TODO(create).
   */
  @PostMapping(path = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<DocumentStatusResponse> create(
      @RequestAttribute(AuthInterceptor.CLIENT_ATTRIBUTE) ClientRecord client,
      @RequestPart("file") MultipartFile file) {
    DocumentStatusResponse body = documentService.create(client, file);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
  }

  /** Returns the content and metadata of the client's non-deleted document. TODO(read). */
  @GetMapping("/read")
  public DocumentReadResponse read(
      @RequestAttribute(AuthInterceptor.CLIENT_ATTRIBUTE) ClientRecord client,
      @RequestParam(value = "documentName", required = false) String documentName) {
    return documentService.read(client, requireName(documentName));
  }

  /** Soft-deletes the client's document with the given name. TODO(delete). */
  @DeleteMapping("/delete")
  public MessageResponse delete(
      @RequestAttribute(AuthInterceptor.CLIENT_ATTRIBUTE) ClientRecord client,
      @RequestParam(value = "documentName", required = false) String documentName) {
    return documentService.delete(client, requireName(documentName));
  }

  private static String requireName(String documentName) {
    if (documentName == null || documentName.isBlank()) {
      throw ApiException.badRequest("documentName is required");
    }
    return documentName.trim();
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
