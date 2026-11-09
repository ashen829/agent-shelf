package com.agentshelf.identity.internal.persistence;

import com.agentshelf.identity.internal.domain.Supplier;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

  boolean existsBySlug(String slug);
}
