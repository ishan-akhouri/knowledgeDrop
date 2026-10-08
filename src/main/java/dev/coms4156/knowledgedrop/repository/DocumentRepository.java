package dev.coms4156.knowledgedrop.repository;

import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.model.DocumentStatus;
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

  Optional<DocumentRecord> findByIdAndOwnerId(UUID id, UUID ownerId);

  Optional<DocumentRecord> findByOwnerIdAndName(UUID ownerId, String name);

  boolean existsByOwnerIdAndStatus(UUID ownerId, DocumentStatus status);

  Page<DocumentRecord> findByOwnerId(UUID ownerId, Pageable pageable);

  Page<DocumentRecord> findByOwnerIdAndStatus(
      UUID ownerId, DocumentStatus status, Pageable pageable);
}
