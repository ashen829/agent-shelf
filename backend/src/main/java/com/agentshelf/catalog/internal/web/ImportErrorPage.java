package com.agentshelf.catalog.internal.web;

import com.agentshelf.catalog.internal.domain.ImportError;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;

/** Rows that could not be imported, in file order. */
record ImportErrorPage(
    List<ImportErrorItem> items, int page, int size, long totalItems, int totalPages) {

  record ImportErrorItem(
      int rowNumber, @Schema(nullable = true) @Nullable String field, String message) {}

  static ImportErrorPage from(Page<ImportError> page) {
    return new ImportErrorPage(
        page.getContent().stream()
            .map(e -> new ImportErrorItem(e.rowNumber(), e.field(), e.message()))
            .toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
