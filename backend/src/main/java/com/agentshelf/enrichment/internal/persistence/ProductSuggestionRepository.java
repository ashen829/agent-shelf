package com.agentshelf.enrichment.internal.persistence;

import com.agentshelf.enrichment.internal.domain.ProductSuggestion;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Every read is scoped by supplier. */
public interface ProductSuggestionRepository extends JpaRepository<ProductSuggestion, UUID> {

  /** One job's suggestions, grouped by product (index product_suggestion_job_product_ix). */
  Page<ProductSuggestion> findBySupplierIdAndEnrichmentJobIdOrderByProductIdAscFieldAsc(
      UUID supplierId, UUID enrichmentJobId, Pageable page);

  /** Removes a job's suggestions before saving them again (the job can run twice after a crash). */
  @Modifying
  @Query("delete from ProductSuggestion s where s.enrichmentJobId = :jobId")
  int deleteByEnrichmentJobId(UUID jobId);
}
