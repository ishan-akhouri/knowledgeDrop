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
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service-side record of an uploaded document: owner, name, type, upload time, and status. Chunk
 * text and embeddings live in Elasticsearch (keyword index and vector store), keyed by this
 * record's ID.
 *
 * <p>Document names are unique per client among non-deleted documents so that "document ID or
 * document name" lookups are unambiguous. Because deletes are soft ({@code deletedAt}), a plain
 * unique constraint on (owner, name) would block re-uploading a deleted name. Instead the database
 * enforces uniqueness on {@code activeKey}, which holds {@code ownerId/name} while {@code
 * deletedAt} is null and is null once the document is deleted. Unique constraints allow multiple
 * nulls, so any number of deleted documents may share a name. Queries must still filter out
 * deleted records.
 */
@Entity
@Table(
    name = "documents",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_documents_active_owner_name", columnNames = "active_key"))
public class DocumentRecord {

  private static final int MAX_REASON_LENGTH = 1000;

  /** Room for a 36-character owner UUID, a separator, and the default 255-character name. */
  private static final int MAX_ACTIVE_KEY_LENGTH = 320;

  /** A SHA-1 digest as 40 lowercase hexadecimal characters. */
  private static final Pattern SHA1_HEX = Pattern.compile("[0-9a-f]{40}");

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private String name;

  @Column(name = "doc_type", nullable = false, length = 127)
  private String type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DocumentStatus status;

  @Column(name = "failure_reason", length = MAX_REASON_LENGTH)
  private String failureReason;

  /** Size of the stored content in bytes. */
  @Column(name = "file_bytes")
  private long fileBytes;

  /** SHA-1 hash of the stored content, encoded as lowercase hexadecimal. */
  @Column(name = "content_hash", length = 40)
  private String contentHash;

  @Column(name = "uploaded_at", nullable = false)
  private Instant uploadedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  /**
   * {@code ownerId/name} while the document is not deleted, null once it is. Unique, which makes
   * (owner, name) unique among non-deleted documents. Derived; never set directly.
   */
  @Column(name = "active_key", length = MAX_ACTIVE_KEY_LENGTH)
  private String activeKey;

  /** Required by JPA. */
  protected DocumentRecord() {}

  /**
   * Creates a record in the {@code processing} state.
   *
   * <p>The content size and SHA-1 hash are computed by the caller (the service layer, which has
   * access to the uploaded file) and validated here, so a record never exists without them.
   *
   * @param ownerId ID of the owning client
   * @param name document name, unique per client
   * @param type document MIME type
   * @param fileBytes size of the content in bytes; must not be negative
   * @param contentHash SHA-1 of the content as 40 lowercase hex characters
   * @throws NullPointerException if {@code contentHash} is null
   * @throws IllegalArgumentException if {@code fileBytes} is negative or {@code contentHash} is
   *     not 40 lowercase hex characters
   */
  public DocumentRecord(
      UUID ownerId, String name, String type, long fileBytes, String contentHash) {
    validateContent(fileBytes, contentHash);
    this.ownerId = ownerId;
    this.name = name;
    this.type = type;
    this.status = DocumentStatus.PROCESSING;
    this.fileBytes = fileBytes;
    this.contentHash = contentHash;
    this.uploadedAt = Instant.now();
    refreshActiveKey();
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

  public long getFileBytes() {
    return fileBytes;
  }

  public String getContentHash() {
    return contentHash;
  }

  private static void validateContent(long fileBytes, String contentHash) {
    Objects.requireNonNull(contentHash, "contentHash must not be null");
    if (fileBytes < 0) {
      throw new IllegalArgumentException("fileBytes must not be negative");
    }
    if (!SHA1_HEX.matcher(contentHash).matches()) {
      throw new IllegalArgumentException(
          "contentHash must be 40 lowercase hexadecimal characters (SHA-1)");
    }
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  /**
   * Sets or clears the soft-delete marker. Setting it frees this document's name for reuse by the
   * same client; clearing it claims the name again, which fails at save time if another
   * non-deleted document now holds it.
   *
   * @param deletedAt deletion time, or null to mark the document as not deleted
   */
  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
    refreshActiveKey();
  }

  private void refreshActiveKey() {
    this.activeKey = deletedAt == null ? ownerId + "/" + name : null;
  }
}
