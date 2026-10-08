package dev.coms4156.knowledgedrop.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Helpers for generating client tokens and hashing them for storage. */
public final class TokenUtils {

  private static final int TOKEN_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  private TokenUtils() {}

  /**
   * Generates a random URL-safe token (256 bits of entropy).
   *
   * @return a new client token
   */
  public static String generateToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Hashes a token with SHA-256 so the plain token never needs to be stored.
   *
   * @param token the plain token
   * @return lower-case hex digest
   */
  public static String hash(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
