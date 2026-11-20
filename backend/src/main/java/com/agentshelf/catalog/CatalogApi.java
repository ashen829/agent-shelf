package com.agentshelf.catalog;

import com.agentshelf.catalog.internal.application.ProductQuery;
import com.agentshelf.catalog.internal.application.ProductSearch;
import com.agentshelf.catalog.internal.application.ProductSort;
import com.agentshelf.catalog.internal.domain.Product;
import com.agentshelf.catalog.internal.domain.ProductStatus;
import com.agentshelf.catalog.internal.persistence.CategoryCount;
import com.agentshelf.catalog.internal.persistence.ProductRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API of the catalog module: the only class other modules call. AI cleanup (M2) reads
 * products here and the agent gateway (M4) searches them; the supplier always comes from the
 * caller's own tenant context or tenant-scoped data.
 */
@Component
public class CatalogApi {

  private final ProductSearch search;
  private final ProductRepository products;

  CatalogApi(ProductSearch search, ProductRepository products) {
    this.search = search;
    this.products = products;
  }

  /** Active products of one supplier whose SKU, name or category contain every word. */
  public Page<ProductSummary> searchProducts(UUID supplierId, String text, int page, int size) {
    return search
        .search(
            supplierId,
            new ProductQuery(ProductStatus.ACTIVE, text, null, null, ProductSort.NAME),
            page,
            size)
        .map(CatalogApi::summary);
  }

  /** Up to {@code limit} active products of one supplier, by SKU: the input for AI cleanup. */
  @Transactional(readOnly = true)
  public List<ProductSnapshot> activeProducts(UUID supplierId, int limit) {
    return products
        .findBySupplierIdAndStatusOrderBySkuAsc(supplierId, ProductStatus.ACTIVE, Limit.of(limit))
        .stream()
        .map(CatalogApi::snapshot)
        .toList();
  }

  /**
   * Names of the supplier's categories in use: the first {@code limit} by name, most used first.
   */
  @Transactional(readOnly = true)
  public List<String> categoryNames(UUID supplierId, int limit) {
    return search.categories(supplierId, 0, Math.max(1, limit)).getContent().stream()
        .sorted((a, b) -> Long.compare(b.count(), a.count()))
        .map(CategoryCount::name)
        .toList();
  }

  /** These products of one supplier as they are now. Other suppliers' IDs are simply missing. */
  @Transactional(readOnly = true)
  public List<ProductSnapshot> products(UUID supplierId, Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return products.findBySupplierIdAndIdIn(supplierId, ids).stream()
        .map(CatalogApi::snapshot)
        .toList();
  }

  private static ProductSnapshot snapshot(Product p) {
    return new ProductSnapshot(
        p.id(), p.version(), p.sku(), p.name(), p.description(), p.category(), p.unit());
  }

  private static ProductSummary summary(Product p) {
    return new ProductSummary(p.id(), p.sku(), p.name(), p.unit(), p.price(), p.stockQty() > 0);
  }
}
