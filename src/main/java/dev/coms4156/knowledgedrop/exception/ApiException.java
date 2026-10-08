package dev.coms4156.knowledgedrop.exception;

import org.springframework.http.HttpStatus;

/** An error that maps to an HTTP status and a human-readable reason in the response body. */
public class ApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final HttpStatus status;

  /**
   * Creates an exception.
   *
   * @param status HTTP status to return
   * @param reason explanation returned to the client
   */
  public ApiException(HttpStatus status, String reason) {
    super(reason);
    this.status = status;
  }

  public HttpStatus getStatus() {
    return status;
  }

  /** Returns a 400 error. */
  public static ApiException badRequest(String reason) {
    return new ApiException(HttpStatus.BAD_REQUEST, reason);
  }

  /** Returns a 401 error. */
  public static ApiException unauthorized(String reason) {
    return new ApiException(HttpStatus.UNAUTHORIZED, reason);
  }

  /** Returns a 404 error. */
  public static ApiException notFound(String reason) {
    return new ApiException(HttpStatus.NOT_FOUND, reason);
  }

  /** Returns a 409 error. */
  public static ApiException conflict(String reason) {
    return new ApiException(HttpStatus.CONFLICT, reason);
  }

  /**
   * Returns a 501 error. Every endpoint that is still a TODO fails with this until it is built.
   *
   * @param what the endpoint or feature that is not built yet
   */
  public static ApiException notImplemented(String what) {
    return new ApiException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet: " + what);
  }
}
