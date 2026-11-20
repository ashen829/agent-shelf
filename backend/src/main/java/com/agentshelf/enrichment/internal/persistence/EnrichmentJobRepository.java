package com.agentshelf.enrichment.internal.persistence;

import com.agentshelf.enrichment.internal.domain.EnrichmentJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped, except {@link #findById} which only the background job uses. */
public interface EnrichmentJobRepository extends JpaRepository<EnrichmentJob, UUID> {

  Optional<EnrichmentJob> findByIdAndSupplierId(UUID id, UUID supplierId);

  Page<EnrichmentJob> findBySupplierIdOrderByCreatedAtDesc(UUID supplierId, Pageable page);

  Optional<EnrichmentJob> findFirstBySupplierIdOrderByCreatedAtDesc(UUID supplierId);
}
