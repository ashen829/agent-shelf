package com.agentshelf.enrichment.internal.domain;

import java.util.Optional;

/** The product fields AI cleanup may suggest. SKU, price and stock are never suggested. */
public enum SuggestionField {
  NAME(200),
  CATEGORY(100),
  UNIT(30),
  DESCRIPTION(500);

  private final int maxLength;

  SuggestionField(int maxLength) {
    this.maxLength = maxLength;
  }

  /** Longest value accepted from the model (descriptions are kept short on purpose). */
  public int maxLength() {
    return maxLength;
  }

  /** The field for a name sent by the model ("name", "unit"), or empty if it isn't one of ours. */
  public static Optional<SuggestionField> fromModel(String name) {
    for (SuggestionField field : values()) {
      if (field.name().equalsIgnoreCase(name)) {
        return Optional.of(field);
      }
    }
    return Optional.empty();
  }
}
