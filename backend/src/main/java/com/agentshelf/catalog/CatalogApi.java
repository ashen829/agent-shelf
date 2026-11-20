package com.agentshelf.catalog;

import com.agentshelf.catalog.internal.application.ProductQuery;
import com.agentshelf.catalog.internal.application.ProductSearch;
import com.agentshelf.catalog.internal.application.ProductSort;
import com.agentshelf.catalog.internal.domain.Product;
import com.agentshelf.catalog.internal.domain.ProductStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Public API of the catalog module: the only class other modules call. The AI agent gateway (M4)
 * searches products here; the supplier always comes from the caller's own tenant context.
 */
@Component
public class CatalogApi {

  private final ProductSearch search;

  CatalogApi(ProductSearch search) {
    this.search = search;
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

  private static ProductSummary summary(Product p) {
    return new ProductSummary(p.id(), p.sku(), p.name(), p.unit(), p.price(), p.stockQty() > 0);
  }
}
