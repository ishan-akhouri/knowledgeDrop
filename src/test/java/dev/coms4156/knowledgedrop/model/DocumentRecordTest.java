package dev.coms4156.knowledgedrop.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentRecordTest {

  private static final String HELLO_SHA1 = "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d";

  // ------------------------------------------------------------ constructor

  @Test
  void constructor_storesContentAndStartsProcessing() {
    UUID ownerId = UUID.randomUUID();

    DocumentRecord document =
        new DocumentRecord(ownerId, "notes.txt", "text/plain", 5L, HELLO_SHA1);

    assertEquals(ownerId, document.getOwnerId());
    assertEquals("notes.txt", document.getName());
    assertEquals("text/plain", document.getType());
    assertEquals(DocumentStatus.PROCESSING, document.getStatus());
    assertEquals(5L, document.getFileBytes());
    assertEquals(HELLO_SHA1, document.getContentHash());
  }

  @Test
  void constructor_acceptsZeroBytes() {
    DocumentRecord document =
        new DocumentRecord(
            UUID.randomUUID(),
            "empty.txt",
            "text/plain",
            0L,
            "da39a3ee5e6b4b0d3255bfef95601890afd80709");

    assertEquals(0L, document.getFileBytes());
  }

  @Test
  void constructor_rejectsNegativeSize() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new DocumentRecord(UUID.randomUUID(), "n.txt", "text/plain", -1L, HELLO_SHA1));
  }

  @Test
  void constructor_rejectsNullHash() {
    assertThrows(
        NullPointerException.class,
        () -> new DocumentRecord(UUID.randomUUID(), "n.txt", "text/plain", 5L, null));
  }

  @Test
  void constructor_rejectsMalformedHash() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new DocumentRecord(UUID.randomUUID(), "n.txt", "text/plain", 5L, "abc123"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DocumentRecord(
                UUID.randomUUID(), "n.txt", "text/plain", 5L, HELLO_SHA1.toUpperCase()));
  }

}
