package dev.coms4156.knowledgedrop.repository;

import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.model.DocumentStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence for {@link DocumentRecord}. Every lookup is scoped to the owning client so one client
 * can never see another client's documents, except the chunk cleanup queries, which are internal
 * maintenance and never reach a client.
 */
public interface DocumentRepository extends JpaRepository<DocumentRecord, UUID> {

  /** Fetches candidate documents in one owner-scoped query for retrieval eligibility checks. */
  List<DocumentRecord> findAllByOwnerIdAndIdIn(UUID ownerId, Collection<UUID> ids);

  /** Finds a document by ID, only if it belongs to the given client. */
  Optional<DocumentRecord> findByIdAndOwnerId(UUID id, UUID ownerId);

  /** Finds a document by ID, only if it is not deleted (soft-delete marker unset). */
  Optional<DocumentRecord> findByIdAndDeletedAtIsNull(UUID id);

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

  /**
   * Lists documents whose chunks may still be in Elasticsearch although they must not be
   * searchable: deleted or failed documents the cleanup job has not purged yet, oldest first.
   * Spans all clients; used only by the cleanup job.
   *
   * @param pageable how many to return (the cleanup batch)
   * @return documents whose chunks should be purged
   */
  @Query(
      "select d from DocumentRecord d where d.chunksPurgedAt is null and (d.deletedAt is not null"
          + " or d.status = dev.coms4156.knowledgedrop.model.DocumentStatus.FAILED)"
          + " order by d.uploadedAt, d.id")
  List<DocumentRecord> findWithUnpurgedChunks(Pageable pageable);

  /**
   * Records that a document's chunks were purged, unless the document became searchable again
   * since it was selected (it is no longer deleted or failed). Updates only this column, so it
   * cannot overwrite a concurrent status change or delete.
   *
   * @param id the document
   * @param purgedAt when the chunks were removed
   * @return 1 if recorded, 0 if the document no longer qualifies or was already purged
   */
  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update DocumentRecord d set d.chunksPurgedAt = :purgedAt where d.id = :id"
          + " and d.chunksPurgedAt is null and (d.deletedAt is not null"
          + " or d.status = dev.coms4156.knowledgedrop.model.DocumentStatus.FAILED)")
  int markChunksPurged(@Param("id") UUID id, @Param("purgedAt") Instant purgedAt);

  /**
   * Marks non-deleted documents that have been processing since before {@code cutoff} as failed.
   * Their ingestion was lost (for example the server restarted while it was queued or running),
   * so they would otherwise stay processing forever. Spans all clients.
   *
   * @param cutoff documents uploaded before this instant are considered stuck
   * @param reason failure reason to record
   * @return the number of documents marked failed
   */
  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update DocumentRecord d"
          + " set d.status = dev.coms4156.knowledgedrop.model.DocumentStatus.FAILED,"
          + " d.failureReason = :reason"
          + " where d.status = dev.coms4156.knowledgedrop.model.DocumentStatus.PROCESSING"
          + " and d.deletedAt is null and d.uploadedAt < :cutoff")
  int failStuckProcessing(@Param("cutoff") Instant cutoff, @Param("reason") String reason);

  /** Marks all owned documents deleted, including documents that were deleted earlier. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update DocumentRecord d set d.deletedAt = :deletedAt where d.ownerId = :ownerId")
  int softDeleteByOwnerId(@Param("ownerId") UUID ownerId, @Param("deletedAt") Instant deletedAt);
}
