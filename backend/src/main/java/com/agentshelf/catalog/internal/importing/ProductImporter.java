package com.agentshelf.catalog.internal.importing;

import com.agentshelf.catalog.internal.domain.ColumnMapping;
import com.agentshelf.catalog.internal.domain.ImportError;
import com.agentshelf.catalog.internal.domain.ImportJob;
import com.agentshelf.catalog.internal.domain.Product;
import com.agentshelf.catalog.internal.domain.ProductDetails;
import com.agentshelf.catalog.internal.domain.ProductRuleViolation;
import com.agentshelf.catalog.internal.persistence.ImportErrorRepository;
import com.agentshelf.catalog.internal.persistence.ProductRepository;
import com.agentshelf.shared.CurrencyCode;
import com.agentshelf.shared.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Applies one batch of rows to one supplier's products: adds new SKUs, updates existing ones,
 * records rows that break a rule. Runs inside the caller's transaction.
 */
@Component
public class ProductImporter {

  private final ProductRepository products;
  private final ImportErrorRepository errors;

  ProductImporter(ProductRepository products, ImportErrorRepository errors) {
    this.products = products;
    this.errors = errors;
  }

  /** Counts for one batch. */
  public record BatchResult(int created, int updated, int unchanged, int failed) {}

  /**
   * @param firstRowBySku lower-cased SKU -> first row it appeared on, across the whole file, to
   *     report duplicates. Updated by this call.
   */
  public BatchResult importBatch(
      ImportJob job,
      CurrencyCode currency,
      List<CsvReader.Row> rows,
      Map<String, Long> firstRowBySku) {
    ColumnMapping mapping = job.mapping();
    Map<String, Product> existing = existingProducts(job, mapping, rows);
    int created = 0;
    int updated = 0;
    int unchanged = 0;
    List<ImportError> failures = new ArrayList<>();

    for (CsvReader.Row row : rows) {
      String sku = cell(row, mapping.sku());
      String key = sku.strip().toLowerCase(Locale.ROOT);
      try {
        Long firstRow = key.isEmpty() ? null : firstRowBySku.putIfAbsent(key, row.rowNumber());
        if (firstRow != null) {
          throw new ProductRuleViolation(
              "sku",
              "SKU "
                  + CellParser.shorten(sku)
                  + " is already on row "
                  + firstRow
                  + " of this file.");
        }
        Product product = existing.get(key);
        ProductDetails details = details(row, mapping, job.decimalSeparator(), currency, product);
        if (product == null) {
          Product added = products.save(new Product(job.supplierId(), details, Instant.now()));
          existing.put(key, added);
          created++;
        } else if (product.isActive() && product.hasDetails(details)) {
          unchanged++;
        } else {
          product.update(details);
          product.restore();
          updated++;
        }
      } catch (ProductRuleViolation e) {
        failures.add(
            new ImportError(
                job, row.rowNumber(), e.field(), String.valueOf(e.getMessage()), raw(job, row)));
      }
    }
    errors.saveAll(failures);
    return new BatchResult(created, updated, unchanged, failures.size());
  }

  private Map<String, Product> existingProducts(
      ImportJob job, ColumnMapping mapping, List<CsvReader.Row> rows) {
    List<String> skus =
        rows.stream()
            .map(row -> cell(row, mapping.sku()).strip().toLowerCase(Locale.ROOT))
            .filter(sku -> !sku.isEmpty())
            .distinct()
            .toList();
    Map<String, Product> bySku = new HashMap<>();
    if (!skus.isEmpty()) {
      for (Product p : products.findBySkus(job.supplierId(), skus)) {
        bySku.put(p.sku().toLowerCase(Locale.ROOT), p);
      }
    }
    return bySku;
  }

  /** Mapped columns replace values; unmapped fields keep the product's current value. */
  private static ProductDetails details(
      CsvReader.Row row,
      ColumnMapping mapping,
      char decimalSeparator,
      CurrencyCode currency,
      @Nullable Product current) {
    Money price =
        new Money(CellParser.price(cell(row, mapping.price()), decimalSeparator), currency);
    int stock =
        mapping.stockQty() != null
            ? CellParser.stock(cell(row, mapping.stockQty()), decimalSeparator)
            : current != null ? current.stockQty() : 0;
    return new ProductDetails(
        cell(row, mapping.sku()),
        cell(row, mapping.name()),
        optional(row, mapping.description(), current == null ? null : current.description()),
        optional(row, mapping.category(), current == null ? null : current.category()),
        optional(row, mapping.unit(), current == null ? null : current.unit()),
        price,
        stock);
  }

  private static @Nullable String optional(
      CsvReader.Row row, @Nullable Integer column, @Nullable String currentValue) {
    return column == null ? currentValue : cell(row, column);
  }

  /** A missing cell (short row) reads as empty. */
  private static String cell(CsvReader.Row row, @Nullable Integer column) {
    return column == null || column >= row.values().size() ? "" : row.values().get(column);
  }

  private static String raw(ImportJob job, CsvReader.Row row) {
    return ErrorReport.toCsvLine(row.values(), job.delimiter());
  }
}
