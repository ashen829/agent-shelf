package com.agentshelf.catalog.internal.persistence;

import com.agentshelf.catalog.internal.domain.ImportJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped, except {@link #findById} which only the background job uses. */
public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

  Optional<ImportJob> findByIdAndSupplierId(UUID id, UUID supplierId);

  Page<ImportJob> findBySupplierIdOrderByCreatedAtDesc(UUID supplierId, Pageable page);
}
