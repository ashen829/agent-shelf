package com.agentshelf.catalog.internal.domain;

import com.agentshelf.shared.DomainException;
import com.agentshelf.shared.Money;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The fields a supplier edits. Validated here, so a product can never hold invalid data, whatever
 * the source (form, CSV import, AI suggestion).
 */
public record ProductDetails(
    String sku,
    String name,
    @Nullable String description,
    @Nullable String category,
    @Nullable String unit,
    Money price,
    int stockQty) {

  /** Letters and digits, then also . _ / - ; no spaces. Compared ignoring case. */
  public static final Pattern SKU = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,63}");

  public ProductDetails {
    sku = sku.strip();
    name = name.strip();
    description = blankToNull(description);
    category = blankToNull(category);
    unit = blankToNull(unit);
    require(SKU.matcher(sku).matches(), "SKU must be 1 to 64 letters, digits or . _ / -");
    require(!name.isEmpty() && name.length() <= 200, "Name must be 1 to 200 characters");
    require(description == null || description.length() <= 2000, "Description is too long");
    require(category == null || category.length() <= 100, "Category is too long");
    require(unit == null || unit.length() <= 30, "Unit is too long");
    require(stockQty >= 0, "Stock can't be negative");
  }

  private static @Nullable String blankToNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new DomainException(message);
    }
  }
}
