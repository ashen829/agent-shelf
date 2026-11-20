package com.agentshelf.catalog.internal.web;

import com.agentshelf.catalog.internal.persistence.CategoryCount;
import java.util.List;
import org.springframework.data.domain.Page;

/** Categories with their product counts, for the category filter. */
record CategoryPage(List<CategoryItem> items, int page, int size, long totalItems, int totalPages) {

  record CategoryItem(String name, long count) {}

  static CategoryPage from(Page<CategoryCount> page) {
    return new CategoryPage(
        page.getContent().stream().map(c -> new CategoryItem(c.name(), c.count())).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
