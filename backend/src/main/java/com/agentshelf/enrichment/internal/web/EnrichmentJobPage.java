package com.agentshelf.enrichment.internal.web;

import com.agentshelf.enrichment.internal.domain.EnrichmentJob;
import java.util.List;
import org.springframework.data.domain.Page;

/** AI cleanup jobs, newest first. */
record EnrichmentJobPage(
    List<EnrichmentJobResponse> items, int page, int size, long totalItems, int totalPages) {

  static EnrichmentJobPage from(Page<EnrichmentJob> page) {
    return new EnrichmentJobPage(
        page.getContent().stream().map(EnrichmentJobResponse::from).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
