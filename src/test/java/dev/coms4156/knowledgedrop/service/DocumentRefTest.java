package dev.coms4156.knowledgedrop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.coms4156.knowledgedrop.exception.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DocumentRefTest {

  @Test
  void of_idOnly_usesId() {
    UUID id = UUID.randomUUID();

    DocumentRef ref = DocumentRef.of(id, null);

    assertEquals(id, ref.id());
    assertNull(ref.name());
  }

  @Test
  void of_nameOnly_usesTrimmedName() {
    DocumentRef ref = DocumentRef.of(null, "  policy.pdf ");

    assertNull(ref.id());
    assertEquals("policy.pdf", ref.name());
  }

  @Test
  void of_blankNameWithId_usesId() {
    UUID id = UUID.randomUUID();

    assertEquals(id, DocumentRef.of(id, "   ").id());
  }

  @Test
  void of_neitherProvided_isBadRequest() {
    ApiException ex = assertThrows(ApiException.class, () -> DocumentRef.of(null, " "));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
  }

  @Test
  void of_bothProvided_isBadRequest() {
    ApiException ex =
        assertThrows(ApiException.class, () -> DocumentRef.of(UUID.randomUUID(), "policy.pdf"));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
  }
}
