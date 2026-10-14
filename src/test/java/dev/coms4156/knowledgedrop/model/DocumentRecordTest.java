package dev.coms4156.knowledgedrop.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentRecordTest {

  private static final String HELLO_SHA1 = "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d";

  private static DocumentRecord newDocument() {
    return new DocumentRecord(UUID.randomUUID(), "notes.txt", "text/plain");
  }

  @Test
  void recordContent_storesByteSizeAndHash() {
    DocumentRecord document = newDocument();

    document.recordContent(5L, HELLO_SHA1);

    assertEquals(5L, document.getFileBytes());
    assertEquals(HELLO_SHA1, document.getContentHash());
  }

  @Test
  void recordContent_acceptsZeroBytes() {
    DocumentRecord document = newDocument();

    document.recordContent(0L, "da39a3ee5e6b4b0d3255bfef95601890afd80709");

    assertEquals(0L, document.getFileBytes());
  }

  @Test
  void recordContent_rejectsNegativeSize() {
    DocumentRecord document = newDocument();

    assertThrows(IllegalArgumentException.class, () -> document.recordContent(-1L, HELLO_SHA1));
  }

  @Test
  void recordContent_rejectsNullHash() {
    DocumentRecord document = newDocument();

    assertThrows(NullPointerException.class, () -> document.recordContent(5L, null));
  }

  @Test
  void recordContent_rejectsMalformedHash() {
    DocumentRecord document = newDocument();

    assertThrows(IllegalArgumentException.class, () -> document.recordContent(5L, ""));
    assertThrows(IllegalArgumentException.class, () -> document.recordContent(5L, "abc123"));
    assertThrows(
        IllegalArgumentException.class, () -> document.recordContent(5L, HELLO_SHA1.toUpperCase()));
    assertThrows(
        IllegalArgumentException.class,
        () -> document.recordContent(5L, "zzf4c61ddcc5e8a2dabede0f3b482cd9aea9434d"));
  }

  @Test
  void recordContent_failedValidationLeavesFieldsUnchanged() {
    DocumentRecord document = newDocument();
    document.recordContent(5L, HELLO_SHA1);

    assertThrows(IllegalArgumentException.class, () -> document.recordContent(99L, "bad"));

    assertEquals(5L, document.getFileBytes());
    assertEquals(HELLO_SHA1, document.getContentHash());
  }

  @Test
  void newDocument_hasNoContentRecordedYet() {
    DocumentRecord document = newDocument();

    assertEquals(0L, document.getFileBytes());
    assertNull(document.getContentHash());
  }
}
