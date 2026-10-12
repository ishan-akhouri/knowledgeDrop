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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Service-side record of an uploaded document: owner, name, type, upload time, and status. Chunk
 * text and embeddings live in Elasticsearch (keyword index and vector store), keyed by this
 * record's ID.
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

  @Column(name = "doc_type", nullable = false, length = 255)
  private String type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DocumentStatus status;

  @Column(name = "failure_reason", length = MAX_REASON_LENGTH)
  private String failureReason;

  /** Size of the stored content in bytes. */
  @Column(name = "file_bytes")
  private int fileBytes;

  /** SHA-1 hash of the stored content, encoded as lowercase hexadecimal. */
  @Column(name = "content_hash", length = 40)
  private String contentHash;

  @Column(name = "uploaded_at", nullable = false)
  private Instant uploadedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  /** Required by JPA. */
  protected DocumentRecord() {}

  /**
   * Creates a record in the {@code processing} state.
   *
   * @param ownerId ID of the owning client
   * @param name document name, unique per client
   * @param type document MIME type
   */
  public DocumentRecord(UUID ownerId, String name, String type) {
    this.ownerId = ownerId;
    this.name = name;
    this.type = type;
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

  public String getType() {
    return type;
  }

  public DocumentStatus getStatus() {
    return status;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public int getFileBytes() {
    return fileBytes;
  }

  public String getContentHash() {
    return contentHash;
  }

  /**
   * Records the size and SHA-1 hash of the document content.
   * SHA-1 produces a 160 bit digest. 8 bytes
   *
   * @param content document bytes
   */
  public void recordContent(byte[] content) {
    this.fileBytes = content.length;
    try {
      this.contentHash =
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 is not available", e);
    }
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }
}
