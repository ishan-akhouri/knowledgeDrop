package dev.coms4156.knowledgedrop.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * Service-side record of an uploaded document: owner, name, type, upload time, and status. Chunk
 * embeddings live in Redis and keyword entries in Elasticsearch, keyed by this record's ID.
 *
 * <p>Document names are unique per client so that "document ID or document name" lookups are
 * unambiguous.
 */
@Entity
@Table(
    name = "documents",
    uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "name"}))
public class DocumentRecord {

  private static final int MAX_REASON_LENGTH = 1000;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "doc_type", nullable = false)
  private DocumentType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DocumentStatus status;

  @Column(name = "failure_reason", length = MAX_REASON_LENGTH)
  private String failureReason;

  /** Where the original uploaded file is stored. */
  @Column(name = "storage_path")
  private String storagePath;

  @Column(name = "uploaded_at", nullable = false)
  private Instant uploadedAt;

  /** Required by JPA. */
  protected DocumentRecord() {}

  /**
   * Creates a record in the {@code processing} state.
   *
   * @param ownerId ID of the owning client
   * @param name document name, unique per client
   * @param type document type
   * @param storagePath location of the stored original file
   */
  public DocumentRecord(UUID ownerId, String name, DocumentType type, String storagePath) {
    this.ownerId = ownerId;
    this.name = name;
    this.type = type;
    this.storagePath = storagePath;
    this.status = DocumentStatus.PROCESSING;
    this.uploadedAt = Instant.now();
  }

  /** Marks the document as being (re-)ingested and clears any earlier failure. */
  public void markProcessing() {
    this.status = DocumentStatus.PROCESSING;
    this.failureReason = null;
  }

  /** Marks the document as searchable. */
  public void markReady() {
    this.status = DocumentStatus.READY;
    this.failureReason = null;
  }

  /**
   * Marks the document as failed.
   *
   * @param reason why ingestion failed
   */
  public void markFailed(String reason) {
    this.status = DocumentStatus.FAILED;
    this.failureReason =
        reason != null && reason.length() > MAX_REASON_LENGTH
            ? reason.substring(0, MAX_REASON_LENGTH)
            : reason;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public String getName() {
    return name;
  }

  public DocumentType getType() {
    return type;
  }

  public DocumentStatus getStatus() {
    return status;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public String getStoragePath() {
    return storagePath;
  }

  public void setStoragePath(String storagePath) {
    this.storagePath = storagePath;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }
}
