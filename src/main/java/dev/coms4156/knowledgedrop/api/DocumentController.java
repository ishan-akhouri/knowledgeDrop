package dev.coms4156.knowledgedrop.api;

import dev.coms4156.knowledgedrop.api.dto.DocumentStatusResponse;
import dev.coms4156.knowledgedrop.api.dto.DocumentSummary;
import dev.coms4156.knowledgedrop.api.dto.MessageResponse;
import dev.coms4156.knowledgedrop.api.dto.ReadDocument;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.security.AuthInterceptor;
import dev.coms4156.knowledgedrop.service.DocumentService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
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
 * Document management endpoints: create, read, delete, and list. The controller handles routing,
 * parameters, and status codes; the logic lives in {@link DocumentService}.
 *
 * <p>Create takes {@code multipart/form-data} with a single binary {@code file} part; the document
 * name is the file's original filename and its type is the file's content type. If the client
 * already has a non-deleted document with that name, create soft-deletes it and stores the
 * upload as a new document with a new ID. Read and delete identify the document by the
 * {@code documentName} query parameter.
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
   * Uploads a document (a single binary file part) and starts ingestion. If a document with that
   * name already exists, it is soft-deleted and the upload gets a new ID. Returns 202 Accepted
   * with the document ID and status, since ingestion continues in the background.
   */
  @PostMapping(path = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<DocumentStatusResponse> create(
      @RequestAttribute(AuthInterceptor.CLIENT_ATTRIBUTE) ClientRecord client,
      @RequestPart("file") MultipartFile file) {
    DocumentStatusResponse body = documentService.create(client, file);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
  }

  /**
   * Streams the stored file of the client's non-deleted document, with the document's content
   * type, length, and an attachment filename. Returns 400 if {@code documentName} is missing or
   * blank, and 404 if there is no such document.
   */
  @GetMapping("/read")
  public ResponseEntity<InputStreamResource> read(
      @RequestAttribute(AuthInterceptor.CLIENT_ATTRIBUTE) ClientRecord client,
      @RequestParam(value = "documentName", required = false) String documentName) {
    ReadDocument readDocument = documentService.read(client, requireName(documentName));

    InputStream stream = readDocument.inputStream();

    try {
      ContentDisposition disposition = ContentDisposition.attachment()
          .filename(readDocument.fileName(), StandardCharsets.UTF_8)
          .build();

      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
          .contentType(parseMediaTypeOrDefault(readDocument.fileType()))
          .contentLength(readDocument.fileSize())
          .body(new InputStreamResource(stream));
    } catch (RuntimeException e) {
      // Spring closes the stream after writing the body; close it ourselves if we never get there.
      try {
        stream.close();
      } catch (IOException closeEx) {
        e.addSuppressed(closeEx);
      }
      throw e;
    }
  }

  private static MediaType parseMediaTypeOrDefault(String type) {
    if (type == null || type.isBlank()) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
    try {
      return MediaType.parseMediaType(type);
    } catch (InvalidMediaTypeException e) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
  }

  /**
   * Soft-deletes the client's document with the given name. Returns 400 if {@code documentName}
   * is missing or blank, and 404 if there is no such document.
   */
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

  /**
   * Lists the caller's non-deleted documents, newest first, optionally filtered by {@code status}
   * and paginated with {@code limit} and {@code offset}.
   */
  @GetMapping("/list")
  public List<DocumentSummary> list(
      @RequestAttribute(AuthInterceptor.CLIENT_ATTRIBUTE) ClientRecord client,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "limit", required = false) Integer limit,
      @RequestParam(value = "offset", required = false) Integer offset) {
    return documentService.list(client, status, limit, offset);
  }
}
