package com.agentshelf.catalog.internal.web;

import com.agentshelf.catalog.internal.domain.Product;
import com.agentshelf.catalog.internal.domain.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record ProductResponse(
    UUID id,
    String sku,
    String name,
    @Schema(nullable = true) @Nullable String description,
    @Schema(nullable = true) @Nullable String category,
    @Schema(nullable = true) @Nullable String unit,
    MoneyResponse price,
    int stockQty,
    ProductStatus status,
    long version,
    Instant updatedAt) {

  static ProductResponse from(Product p) {
    return new ProductResponse(
        p.id(),
        p.sku(),
        p.name(),
        p.description(),
        p.category(),
        p.unit(),
        MoneyResponse.from(p.price()),
        p.stockQty(),
        p.status(),
        p.version(),
        p.updatedAt());
  }
}
