package com.agentshelf.catalog.internal.web;

import com.agentshelf.catalog.internal.importing.CsvReader;
import com.agentshelf.catalog.internal.importing.ImportService;
import java.util.List;

/** What the column-matching screen shows: headers, the first rows and a suggested mapping. */
record ImportPreviewResponse(
    List<String> headers, List<PreviewRow> rows, int rowCount, ColumnMappingDto suggestedMapping) {

  record PreviewRow(long rowNumber, List<String> values) {}

  static ImportPreviewResponse from(ImportService.Preview preview) {
    return new ImportPreviewResponse(
        preview.headers(),
        preview.rows().stream().map(ImportPreviewResponse::row).toList(),
        preview.rowCount(),
        ColumnMappingDto.from(preview.suggested()));
  }

  private static PreviewRow row(CsvReader.Row row) {
    return new PreviewRow(
        row.rowNumber(),
        row.values().stream().map(v -> v.length() > 100 ? v.substring(0, 100) + "…" : v).toList());
  }
}
