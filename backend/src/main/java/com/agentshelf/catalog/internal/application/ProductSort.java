package com.agentshelf.catalog.internal.application;

import org.springframework.data.domain.Sort;

/** The sort orders the product list allows. A fixed list, so clients can't sort by any column. */
public enum ProductSort {
  NAME(Sort.by("name").ascending().and(Sort.by("id"))),
  NAME_DESC(Sort.by("name").descending().and(Sort.by("id"))),
  SKU(Sort.by("sku").ascending().and(Sort.by("id"))),
  UPDATED_DESC(Sort.by("updatedAt").descending().and(Sort.by("id")));

  private final Sort sort;

  ProductSort(Sort sort) {
    this.sort = sort;
  }

  public Sort sort() {
    return sort;
  }
}
