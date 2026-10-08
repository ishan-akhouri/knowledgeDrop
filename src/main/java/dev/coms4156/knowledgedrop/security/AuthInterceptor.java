package dev.coms4156.knowledgedrop.security;

import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.service.ClientService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Authenticates every protected request from the {@code Authorization: Bearer <token>} header.
 * Missing or invalid tokens are rejected with HTTP 401. On success the calling client is exposed to
 * controllers as the request attribute {@link #CLIENT_ATTRIBUTE}.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

  /** Request attribute holding the authenticated {@link ClientRecord}. */
  public static final String CLIENT_ATTRIBUTE = "knowledgeDrop.client";

  private static final String BEARER_PREFIX = "Bearer ";

  private final ClientService clientService;

  /**
   * Creates the interceptor.
   *
   * @param clientService used to look up the client that owns a token
   */
  public AuthInterceptor(ClientService clientService) {
    this.clientService = clientService;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    boolean hasBearer =
        header != null
            && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length());
    if (!hasBearer) {
      throw ApiException.unauthorized(
          "Missing or malformed Authorization header; expected 'Bearer <client token>'");
    }
    String token = header.substring(BEARER_PREFIX.length()).trim();
    ClientRecord client =
        clientService
            .authenticate(token)
            .orElseThrow(() -> ApiException.unauthorized("Invalid client token"));
    request.setAttribute(CLIENT_ATTRIBUTE, client);
    return true;
  }
}
