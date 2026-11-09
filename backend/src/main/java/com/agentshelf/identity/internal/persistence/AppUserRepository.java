package com.agentshelf.identity.internal.persistence;

import com.agentshelf.identity.internal.domain.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

  /**
   * Login lookup. The only query that is not scoped by supplier: an email address belongs to
   * exactly one user, and the user decides the supplier.
   */
  Optional<AppUser> findByEmail(String normalizedEmail);

  boolean existsByEmail(String normalizedEmail);

  /** Tenant-scoped lookup: returns nothing if the user belongs to another supplier. */
  Optional<AppUser> findByIdAndSupplierId(UUID id, UUID supplierId);
}
