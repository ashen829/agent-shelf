package com.agentshelf.catalog.internal.web;

import com.agentshelf.catalog.internal.domain.ColumnMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** 0-based column index in the file for each product field; null = not in the file. */
record ColumnMappingDto(
    @Schema(nullable = true) @Nullable Integer sku,
    @Schema(nullable = true) @Nullable Integer name,
    @Schema(nullable = true) @Nullable Integer description,
    @Schema(nullable = true) @Nullable Integer category,
    @Schema(nullable = true) @Nullable Integer unit,
    @Schema(nullable = true) @Nullable Integer price,
    @Schema(nullable = true) @Nullable Integer stockQty) {

  static ColumnMappingDto from(ColumnMapping m) {
    return new ColumnMappingDto(
        m.sku(), m.name(), m.description(), m.category(), m.unit(), m.price(), m.stockQty());
  }

  ColumnMapping toDomain() {
    return new ColumnMapping(sku, name, description, category, unit, price, stockQty);
  }
}
