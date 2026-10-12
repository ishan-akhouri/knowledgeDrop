package dev.coms4156.knowledgedrop.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentRecordTest {

  @Test
  void recordContent_storesByteSizeAndSha1Hash() {
    DocumentRecord document =
        new DocumentRecord(UUID.randomUUID(), "notes.txt", "text/plain");

    document.recordContent("hello".getBytes(StandardCharsets.UTF_8));

    assertEquals(5L, document.getFileBytes());
    assertEquals(
        "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d",
        document.getContentHash());
  }
}
