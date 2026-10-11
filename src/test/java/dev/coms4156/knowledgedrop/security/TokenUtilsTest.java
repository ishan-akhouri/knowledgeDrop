package dev.coms4156.knowledgedrop.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenUtilsTest {

  @Test
  void generateToken_isUrlSafeAndUnique() {
    String first = TokenUtils.generateToken();
    String second = TokenUtils.generateToken();

    assertTrue(first.matches("[A-Za-z0-9_-]{43}"), "32 random bytes, base64url, no padding");
    assertNotEquals(first, second);
  }

  @Test
  void hash_matchesKnownSha256Vector() {
    assertEquals(
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        TokenUtils.hash("abc"));
  }

  @Test
  void hash_isDeterministicAndDiffersFromInput() {
    String token = TokenUtils.generateToken();

    assertEquals(TokenUtils.hash(token), TokenUtils.hash(token));
    assertNotEquals(token, TokenUtils.hash(token));
  }
}
