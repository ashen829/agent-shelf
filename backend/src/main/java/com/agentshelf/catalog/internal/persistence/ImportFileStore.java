package com.agentshelf.catalog.internal.persistence;

import java.util.UUID;

/**
 * Where uploaded import files are kept. A port: today PostgreSQL ({@link JdbcImportFileStore}),
 * later object storage, without changing the import code.
 */
public interface ImportFileStore {

  void save(UUID importJobId, UUID supplierId, byte[] content);

  /** The file of this supplier's import. */
  byte[] load(UUID importJobId, UUID supplierId);
}
