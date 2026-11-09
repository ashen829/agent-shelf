package com.agentshelf.catalog.internal.domain;

import com.agentshelf.shared.DomainException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Which column of the file (0-based) holds which product field. {@code null}: not in the file. On
 * update, an unmapped field keeps the product's current value.
 */
public record ColumnMapping(
    @Nullable Integer sku,
    @Nullable Integer name,
    @Nullable Integer description,
    @Nullable Integer category,
    @Nullable Integer unit,
    @Nullable Integer price,
    @Nullable Integer stockQty) {

  /** SKU, name and price are needed; columns must exist and each be used once. */
  public void validate(int columnCount) {
    if (sku == null || name == null || price == null) {
      throw new DomainException("Choose the columns for SKU, name and price.");
    }
    Set<Integer> seen = new HashSet<>();
    for (Integer column : columns()) {
      if (column < 0 || column >= columnCount) {
        throw new DomainException("A chosen column doesn't exist in the file.");
      }
      if (!seen.add(column)) {
        throw new DomainException("Each column can only be used for one field.");
      }
    }
  }

  private List<Integer> columns() {
    List<Integer> columns = new ArrayList<>();
    for (Integer c : new Integer[] {sku, name, description, category, unit, price, stockQty}) {
      if (c != null) {
        columns.add(c);
      }
    }
    return columns;
  }
}
