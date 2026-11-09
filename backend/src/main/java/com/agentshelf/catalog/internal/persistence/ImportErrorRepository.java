package com.agentshelf.catalog.internal.persistence;

import com.agentshelf.catalog.internal.domain.ImportError;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ImportErrorRepository extends JpaRepository<ImportError, UUID> {

  Page<ImportError> findByImportJobIdAndSupplierIdOrderByRowNumber(
      UUID importJobId, UUID supplierId, Pageable page);

  List<ImportError> findAllByImportJobIdAndSupplierIdOrderByRowNumber(
      UUID importJobId, UUID supplierId);

  @Modifying
  @Query("delete from ImportError e where e.importJobId = :importJobId")
  void deleteByImportJobId(UUID importJobId);
}
