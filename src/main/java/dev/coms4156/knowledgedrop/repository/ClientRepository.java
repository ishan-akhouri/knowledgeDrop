package dev.coms4156.knowledgedrop.repository;

import dev.coms4156.knowledgedrop.model.ClientRecord;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link ClientRecord}. */
public interface ClientRepository extends JpaRepository<ClientRecord, UUID> {

  /** Finds the client whose token hash matches (used by the auth interceptor). */
  Optional<ClientRecord> findByTokenHash(String tokenHash);

  /** Locks the client row so concurrent deletion requests cannot change its deletion time. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from ClientRecord c where c.id = :id")
  Optional<ClientRecord> findByIdForUpdate(@Param("id") UUID id);
}
