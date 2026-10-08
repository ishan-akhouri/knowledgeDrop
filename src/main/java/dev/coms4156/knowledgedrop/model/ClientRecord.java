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

  @Column(nullable = false, unique = true)
  private String name;

  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** Required by JPA. */
  protected ClientRecord() {}

  /**
   * Creates a client record.
   *
   * @param name unique client name
   * @param tokenHash SHA-256 hex digest of the client token
   */
  public ClientRecord(String name, String tokenHash) {
    this.name = name;
    this.tokenHash = tokenHash;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
