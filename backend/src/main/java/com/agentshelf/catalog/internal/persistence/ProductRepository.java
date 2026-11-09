package com.agentshelf.catalog.internal.persistence;

import com.agentshelf.catalog.internal.domain.Product;
import com.agentshelf.catalog.internal.domain.ProductStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

  /** Products of one supplier whose SKUs (lower-cased) are in the list. */
  @Query("select p from Product p where p.supplierId = :supplierId and lower(p.sku) in :lowerSkus")
  List<Product> findBySkus(UUID supplierId, Collection<String> lowerSkus);

  /** ID and lower-cased SKU of every active product: for the "complete catalog" option. */
  @Query(
      "select p.id, lower(p.sku) from Product p where p.supplierId = :supplierId"
          + " and p.status = com.agentshelf.catalog.internal.domain.ProductStatus.ACTIVE")
  List<Object[]> findActiveSkus(UUID supplierId);

  /** Archives products in bulk; bumps the version so open edit forms see the change. */
  @Modifying(clearAutomatically = true)
  @Query(
      "update Product p set p.status = com.agentshelf.catalog.internal.domain.ProductStatus.ARCHIVED,"
          + " p.updatedAt = :now, p.version = p.version + 1"
          + " where p.supplierId = :supplierId and p.id in :ids")
  int archiveAll(UUID supplierId, Collection<UUID> ids, Instant now);
}
