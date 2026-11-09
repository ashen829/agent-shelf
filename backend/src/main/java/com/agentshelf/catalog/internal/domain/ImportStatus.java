package com.agentshelf.catalog.internal.domain;

/** UPLOADED → QUEUED → RUNNING → COMPLETED, COMPLETED_WITH_ERRORS or FAILED. */
public enum ImportStatus {
  /** File checked and stored; waiting for the supplier to match columns. */
  UPLOADED,
  QUEUED,
  RUNNING,
  COMPLETED,
  /** Valid rows were imported; some rows had errors. */
  COMPLETED_WITH_ERRORS,
  /** Nothing more could be done, for example the stored file was unreadable. */
  FAILED;

  public boolean isFinished() {
    return this == COMPLETED || this == COMPLETED_WITH_ERRORS || this == FAILED;
  }
}
