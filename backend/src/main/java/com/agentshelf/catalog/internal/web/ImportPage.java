package com.agentshelf.catalog.internal.web;

import com.agentshelf.catalog.internal.domain.ImportJob;
import java.util.List;
import org.springframework.data.domain.Page;

/** Import history, newest first. */
record ImportPage(List<ImportResponse> items, int page, int size, long totalItems, int totalPages) {

  static ImportPage from(Page<ImportJob> page) {
    return new ImportPage(
        page.getContent().stream().map(ImportResponse::from).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
