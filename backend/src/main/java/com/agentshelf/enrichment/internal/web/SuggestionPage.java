package com.agentshelf.enrichment.internal.web;

import com.agentshelf.enrichment.internal.domain.ProductSuggestion;
import java.util.List;
import org.springframework.data.domain.Page;

/** One job's suggestions, grouped by product. */
record SuggestionPage(
    List<SuggestionResponse> items, int page, int size, long totalItems, int totalPages) {

  static SuggestionPage from(Page<ProductSuggestion> page) {
    return new SuggestionPage(
        page.getContent().stream().map(SuggestionResponse::from).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
