package com.agentshelf.catalog.internal.domain;

import com.agentshelf.shared.DomainException;

/** A product rule was broken. Names the field, so forms and import reports can point at it. */
public class ProductRuleViolation extends DomainException {

  private static final long serialVersionUID = 1L;

  private final String field;

  public ProductRuleViolation(String field, String message) {
    super(message);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
