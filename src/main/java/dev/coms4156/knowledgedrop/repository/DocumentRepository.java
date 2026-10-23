package dev.coms4156.knowledgedrop.repository;

import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.model.DocumentStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@link DocumentRecord}. Every lookup is scoped to the owning client so one client
 * can never see another client's documents.
 */
public interface DocumentRepository extends JpaRepository<DocumentRecord, UUID> {

  /** Finds a document by ID, only if it belongs to the given client. */
  Optional<DocumentRecord> findByIdAndOwnerId(UUID id, UUID ownerId);

  /** Finds a document by name within one client's documents. */
  Optional<DocumentRecord> findByOwnerIdAndName(UUID ownerId, String name);

  /** Finds a client's non-deleted (soft-delete marker unset) document with the given name. */
  Optional<DocumentRecord> findByOwnerIdAndNameAndDeletedAtIsNull(UUID ownerId, String name);

  /** Returns true if the client owns at least one document with this status. */
  boolean existsByOwnerIdAndStatus(UUID ownerId, DocumentStatus status);

  /** Lists one client's documents, one page at a time. */
  Page<DocumentRecord> findByOwnerId(UUID ownerId, Pageable pageable);

  /** Lists one client's documents with the given status, one page at a time. */
  Page<DocumentRecord> findByOwnerIdAndStatus(
      UUID ownerId, DocumentStatus status, Pageable pageable);

  /**
   * Soft-deletes a client's non-deleted document with the given name by setting its {@code
   * deletedAt} marker and clearing its {@code activeKey} so the name can be reused. Documents
   * already marked deleted are left untouched.
   *
   * @param ownerId ID of the owning client
   * @param name document name
   * @param deletedAt deletion timestamp to record
   * @return the number of records updated ({@code 0} if no matching non-deleted document exists)
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE DocumentRecord d SET d.deletedAt = :deletedAt, d.activeKey = NULL "
          + "WHERE d.ownerId = :ownerId AND d.name = :name AND d.deletedAt IS NULL")
  int markDeletedByOwnerIdAndName(
      @Param("ownerId") UUID ownerId,
      @Param("name") String name,
      @Param("deletedAt") Instant deletedAt);

  /** Lists one client's non-deleted documents, one page at a time. */
  Page<DocumentRecord> findByOwnerIdAndDeletedAtIsNull(UUID ownerId, Pageable pageable);

  /** Lists one client's non-deleted documents with the given status, one page at a time. */
  Page<DocumentRecord> findByOwnerIdAndStatusAndDeletedAtIsNull(
      UUID ownerId, DocumentStatus status, Pageable pageable);

  /** Marks all owned documents deleted, including documents that were deleted earlier. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update DocumentRecord d set d.deletedAt = :deletedAt where d.ownerId = :ownerId")
  int softDeleteByOwnerId(@Param("ownerId") UUID ownerId, @Param("deletedAt") Instant deletedAt);
}
