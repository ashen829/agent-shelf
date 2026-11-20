package com.agentshelf.enrichment.internal.domain;

/** QUEUED: waiting to be sent. SUBMITTED: Claude is working on the batch. Then a final status. */
public enum EnrichmentStatus {
  QUEUED,
  SUBMITTED,
  COMPLETED,
  FAILED;

  public boolean isFinished() {
    return this == COMPLETED || this == FAILED;
  }
}
