package dev.coms4156.knowledgedrop.repository;

import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.model.DocumentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@link DocumentRecord}. Every lookup is scoped to the owning client so one client
 * can never see another client's documents.
 */
public interface DocumentRepository extends JpaRepository<DocumentRecord, UUID> {

  /** Finds a document by ID, only if it belongs to the given client. */
  Optional<DocumentRecord> findByIdAndOwnerId(UUID id, UUID ownerId);

  /** Finds a document by name within one client's documents. */
  Optional<DocumentRecord> findByOwnerIdAndName(UUID ownerId, String name);

  /** Finds active documents by case-insensitive title within one client. */
  List<DocumentRecord> findByOwnerIdAndNameIgnoreCaseAndDeletedFalse(UUID ownerId, String name);

  /** Returns true if the client owns at least one document with this status. */
  boolean existsByOwnerIdAndStatus(UUID ownerId, DocumentStatus status);

  /** Lists one client's documents, one page at a time. */
  Page<DocumentRecord> findByOwnerId(UUID ownerId, Pageable pageable);

  /** Lists one client's documents with the given status, one page at a time. */
  Page<DocumentRecord> findByOwnerIdAndStatus(
      UUID ownerId, DocumentStatus status, Pageable pageable);
}
