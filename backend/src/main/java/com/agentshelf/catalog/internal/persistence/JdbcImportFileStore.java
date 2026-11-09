package com.agentshelf.catalog.internal.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Keeps import files in {@code catalog.import_file}. Every lookup is scoped by supplier. */
@Component
class JdbcImportFileStore implements ImportFileStore {

  private final JdbcClient jdbc;

  JdbcImportFileStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void save(UUID importJobId, UUID supplierId, byte[] content) {
    jdbc.sql(
            "INSERT INTO catalog.import_file (import_job_id, supplier_id, content, created_at)"
                + " VALUES (?, ?, ?, ?)")
        .params(importJobId, supplierId, content, Timestamp.from(Instant.now()))
        .update();
  }

  @Override
  public byte[] load(UUID importJobId, UUID supplierId) {
    return jdbc.sql(
            "SELECT content FROM catalog.import_file WHERE import_job_id = ? AND supplier_id = ?")
        .params(importJobId, supplierId)
        .query(byte[].class)
        .optional()
        .orElseThrow(() -> new IllegalStateException("Import file not found"));
  }
}
