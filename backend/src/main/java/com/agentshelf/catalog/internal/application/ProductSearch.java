package com.agentshelf.catalog.internal.application;

import com.agentshelf.catalog.internal.domain.Product;
import com.agentshelf.catalog.internal.persistence.CategoryCount;
import com.agentshelf.catalog.internal.persistence.ProductRepository;
import com.agentshelf.shared.DomainException;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds one supplier's products. Used by the dashboard today and by the AI agent gateway (M4)
 * through {@code CatalogApi}. Every query is scoped to the given supplier.
 */
@Service
public class ProductSearch {

  public static final int MAX_TEXT_LENGTH = 100;
  public static final int MAX_WORDS = 8;

  private final ProductRepository products;

  ProductSearch(ProductRepository products) {
    this.products = products;
  }

  @Transactional(readOnly = true)
  public Page<Product> search(UUID supplierId, ProductQuery query, int page, int size) {
    return products.findAll(
        specification(supplierId, query), PageRequest.of(page, size, query.sort().sort()));
  }

  @Transactional(readOnly = true)
  public Page<CategoryCount> categories(UUID supplierId, int page, int size) {
    return products.categories(supplierId, PageRequest.of(page, size));
  }

  /** Lower-cased words, each escaped for LIKE so % _ and \ only match themselves. */
  static List<String> words(String text) {
    String trimmed = text.strip();
    if (trimmed.length() > MAX_TEXT_LENGTH) {
      throw new DomainException("Search text can be at most 100 characters.");
    }
    List<String> words =
        Arrays.stream(trimmed.toLowerCase(Locale.ROOT).split("\\s+"))
            .filter(w -> !w.isEmpty())
            .distinct()
            .toList();
    if (words.size() > MAX_WORDS) {
      throw new DomainException("Search with at most 8 words.");
    }
    return words.stream()
        .map(w -> "%" + w.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%")
        .toList();
  }

  private static Specification<Product> specification(UUID supplierId, ProductQuery query) {
    return (root, criteria, cb) -> {
      List<Predicate> where = new ArrayList<>();
      where.add(cb.equal(root.get("supplierId"), supplierId));
      where.add(cb.equal(root.get("status"), query.status()));
      String text = query.text();
      if (text != null && !text.isBlank()) {
        for (String pattern : words(text)) {
          where.add(cb.like(root.get("searchText"), pattern, '\\'));
        }
      }
      String category = query.category();
      if (category != null && !category.isBlank()) {
        where.add(cb.equal(root.get("category"), category.strip()));
      }
      StockFilter stock = query.stock();
      if (stock == StockFilter.IN_STOCK) {
        where.add(cb.greaterThan(root.<Integer>get("stockQty"), 0));
      } else if (stock == StockFilter.OUT_OF_STOCK) {
        where.add(cb.equal(root.get("stockQty"), 0));
      }
      return cb.and(where.toArray(Predicate[]::new));
    };
  }
}
