package com.agentshelf.catalog.internal.web;

import com.agentshelf.catalog.internal.domain.ImportJob;
import com.agentshelf.catalog.internal.domain.ImportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One import: status, progress and results. */
record ImportResponse(
    UUID id,
    String fileName,
    ImportStatus status,
    int rowsTotal,
    int rowsProcessed,
    int rowsCreated,
    int rowsUpdated,
    int rowsUnchanged,
    int rowsFailed,
    int productsArchived,
    boolean completeCatalog,
    @Schema(nullable = true) @Nullable String failureMessage,
    Instant createdAt,
    @Schema(nullable = true) @Nullable Instant startedAt,
    @Schema(nullable = true) @Nullable Instant finishedAt) {

  static ImportResponse from(ImportJob job) {
    return new ImportResponse(
        job.id(),
        job.fileName(),
        job.status(),
        job.rowsTotal(),
        job.rowsProcessed(),
        job.rowsCreated(),
        job.rowsUpdated(),
        job.rowsUnchanged(),
        job.rowsFailed(),
        job.productsArchived(),
        job.completeCatalog(),
        job.failureMessage(),
        job.createdAt(),
        job.startedAt(),
        job.finishedAt());
  }
}
