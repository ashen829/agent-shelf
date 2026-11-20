package com.agentshelf.catalog.internal.application;

import com.agentshelf.catalog.internal.domain.ProductStatus;
import org.jspecify.annotations.Nullable;

/**
 * What to find: free text (every word must appear in the SKU, name or category), and optional
 * filters. The text is untrusted input; it is only ever used as a bound parameter.
 */
public record ProductQuery(
    ProductStatus status,
    @Nullable String text,
    @Nullable String category,
    @Nullable StockFilter stock,
    ProductSort sort) {

  public static ProductQuery all(ProductStatus status, ProductSort sort) {
    return new ProductQuery(status, null, null, null, sort);
  }
}
