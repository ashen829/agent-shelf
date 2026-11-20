package com.agentshelf.enrichment.internal.domain;

/** Every suggestion starts as PENDING. Only the supplier moves it on (next slice). */
public enum SuggestionStatus {
  PENDING,
  APPROVED,
  REJECTED
}
