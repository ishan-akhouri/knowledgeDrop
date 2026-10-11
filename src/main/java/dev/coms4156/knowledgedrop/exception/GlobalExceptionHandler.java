package dev.coms4156.knowledgedrop.exception;

import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Converts exceptions into the consistent {@link ErrorResponse} JSON body. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /** Handles errors raised deliberately by the application. */
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
    return build(ex.getStatus(), ex.getMessage());
  }

  /** Handles request bodies that fail bean validation. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    String reason =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .collect(Collectors.joining("; "));
    return build(HttpStatus.BAD_REQUEST, reason.isEmpty() ? "Validation failed" : reason);
  }

  /** Handles missing or malformed JSON bodies. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
    return build(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
  }

  /** Handles a wrong Content-Type on the request or on a multipart part (HTTP 415). */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
      HttpMediaTypeNotSupportedException ex) {
    return build(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "Unsupported Content-Type; check the request and each multipart part");
  }

  /** Handles a missing query or form parameter. */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse> handleMissingParameter(
      MissingServletRequestParameterException ex) {
    String reason = "Missing required parameter '" + ex.getParameterName() + "'";
    return build(HttpStatus.BAD_REQUEST, reason);
  }

  /** Handles a missing multipart part. */
  @ExceptionHandler(MissingServletRequestPartException.class)
  public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex) {
    String reason = "Missing required part '" + ex.getRequestPartName() + "'";
    return build(HttpStatus.BAD_REQUEST, reason);
  }

  /** Handles a parameter with the wrong type, such as a malformed document ID. */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'");
  }

  /** Handles uploads above the configured size limit (HTTP 413). */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex) {
    // Numeric status: the HttpStatus constant for 413 was renamed in Spring Framework 7.
    return ResponseEntity.status(413)
        .body(new ErrorResponse("Content Too Large", "Uploaded content exceeds the size limit"));
  }

  private static ResponseEntity<ErrorResponse> build(HttpStatus status, String reason) {
    return ResponseEntity.status(status).body(new ErrorResponse(status.getReasonPhrase(), reason));
  }
}
