package com.agentshelf.catalog.internal.domain;

/** Archived products are kept (quotes will point at them) but hidden from lists and agents. */
public enum ProductStatus {
  ACTIVE,
  ARCHIVED
}
