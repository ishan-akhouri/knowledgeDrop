package dev.coms4156.knowledgedrop.repository;

import dev.coms4156.knowledgedrop.model.ClientRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link ClientRecord}. */
public interface ClientRepository extends JpaRepository<ClientRecord, UUID> {

  boolean existsByName(String name);

  Optional<ClientRecord> findByTokenHash(String tokenHash);
}
