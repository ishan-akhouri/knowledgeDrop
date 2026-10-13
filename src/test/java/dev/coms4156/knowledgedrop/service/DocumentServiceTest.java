package dev.coms4156.knowledgedrop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.model.DocumentType;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit tests for document soft deletion. */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

  @Mock private DocumentRepository repository;
  @InjectMocks private DocumentService service;

  private final UUID ownerId = UUID.randomUUID();
  private final UUID documentId = UUID.randomUUID();
  private final ClientRecord client = new ClientRecord("owner", "hash");
  private DocumentRecord document;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(client, "id", ownerId);
    document = new DocumentRecord(ownerId, "Notes", DocumentType.TEXT, "/files/notes");
  }

  @Test
  void deleteById_setsFlagAndPreservesRecord() {
    when(repository.findByIdAndOwnerId(documentId, ownerId)).thenReturn(Optional.of(document));

    assertFalse(document.isDeleted());
    assertEquals("Document deleted successfully",
        service.delete(client, DocumentRef.of(documentId, null)).message());

    assertTrue(document.isDeleted());
    assertEquals("/files/notes", document.getStoragePath());
    verify(repository).save(document);
    verify(repository, never()).delete(any(DocumentRecord.class));
  }

  @Test
  void deleteByTitle_usesCaseInsensitiveOwnerScopedLookup() {
    when(repository.findByOwnerIdAndNameIgnoreCaseAndDeletedFalse(ownerId, "nOtEs"))
        .thenReturn(List.of(document));

    service.delete(client, DocumentRef.of(null, " nOtEs "));

    assertTrue(document.isDeleted());
    verify(repository).save(document);
  }

  @Test
  void deleteUnknownOrUnownedId_returns404WithoutSaving() {
    when(repository.findByIdAndOwnerId(documentId, ownerId)).thenReturn(Optional.empty());

    assertDeleteError(DocumentRef.of(documentId, null), HttpStatus.NOT_FOUND);
  }

  @Test
  void deleteAlreadyDeletedId_returns404WithoutSaving() {
    document.markDeleted();
    when(repository.findByIdAndOwnerId(documentId, ownerId)).thenReturn(Optional.of(document));

    assertDeleteError(DocumentRef.of(documentId, null), HttpStatus.NOT_FOUND);
  }

  @Test
  void deleteUnknownTitle_returns404() {
    when(repository.findByOwnerIdAndNameIgnoreCaseAndDeletedFalse(ownerId, "unknown"))
        .thenReturn(List.of());

    assertDeleteError(DocumentRef.of(null, "unknown"), HttpStatus.NOT_FOUND);
  }

  @Test
  void deleteAmbiguousTitle_returns409WithoutDeletingEither() {
    DocumentRecord other = new DocumentRecord(ownerId, "NOTES", DocumentType.TEXT, "/other");
    when(repository.findByOwnerIdAndNameIgnoreCaseAndDeletedFalse(ownerId, "notes"))
        .thenReturn(List.of(document, other));

    assertDeleteError(DocumentRef.of(null, "notes"), HttpStatus.CONFLICT);
    assertFalse(document.isDeleted());
    assertFalse(other.isDeleted());
  }

  private void assertDeleteError(DocumentRef ref, HttpStatus status) {
    ApiException exception = assertThrows(ApiException.class, () -> service.delete(client, ref));
    assertEquals(status, exception.getStatus());
    verify(repository, never()).save(any());
  }
}
