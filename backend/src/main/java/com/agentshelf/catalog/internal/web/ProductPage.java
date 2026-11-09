package com.agentshelf.catalog.internal.web;

import com.agentshelf.catalog.internal.domain.Product;
import java.util.List;
import org.springframework.data.domain.Page;

/** One page of products. Every list endpoint returns this shape: items plus paging numbers. */
record ProductPage(
    List<ProductResponse> items, int page, int size, long totalItems, int totalPages) {

  static ProductPage from(Page<Product> page) {
    return new ProductPage(
        page.getContent().stream().map(ProductResponse::from).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
