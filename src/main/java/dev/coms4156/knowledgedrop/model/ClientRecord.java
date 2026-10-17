package dev.coms4156.knowledgedrop.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A registered client program. Only a SHA-256 hash of the client token is stored; the plain token
 * is returned once, by registerClient.
 */
@Entity
@Table(name = "clients")
public class ClientRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "first_name", nullable = false)
  private String firstName;

  @Column(name = "last_name", nullable = false)
  private String lastName;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** Required by JPA. */
  protected ClientRecord() {}

  /**
   * Creates a client record.
   *
   * @param firstName client first name
   * @param lastName client last name
   * @param tokenHash SHA-256 hex digest of the client token
   */
  public ClientRecord(String firstName, String lastName, String tokenHash) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.tokenHash = tokenHash;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
