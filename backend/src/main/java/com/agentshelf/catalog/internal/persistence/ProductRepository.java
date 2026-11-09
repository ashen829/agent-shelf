package com.agentshelf.catalog.internal.persistence;

import com.agentshelf.catalog.internal.domain.Product;
import com.agentshelf.catalog.internal.domain.ProductStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Every query is scoped by supplier: a product of another supplier is simply not found. */
public interface ProductRepository extends JpaRepository<Product, UUID> {

  Optional<Product> findByIdAndSupplierId(UUID id, UUID supplierId);

  Page<Product> findBySupplierIdAndStatus(UUID supplierId, ProductStatus status, Pageable page);

  @Query(
      """
      select count(p) > 0 from Product p
      where p.supplierId = :supplierId and lower(p.sku) = lower(:sku) and p.id <> :excludeId
      """)
  boolean skuTaken(UUID supplierId, String sku, UUID excludeId);
}
