package com.agentshelf.enrichment.internal.domain;

import com.agentshelf.shared.Ids;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One AI cleanup run over a supplier's catalog: its batch, progress, result and token usage. */
@Entity
@Table(schema = "enrichment", name = "enrichment_job")
public class EnrichmentJob {

  @Id private UUID id;
  private UUID supplierId;

  @Enumerated(EnumType.STRING)
  private EnrichmentStatus status;

  private String model;
  private @Nullable String batchId;
  private int productsTotal;
  private int productsFailed;
  private int suggestionsCreated;
  private int suggestionsDropped;
  private long inputTokens;
  private long outputTokens;
  private long cacheReadTokens;
  private long cacheWriteTokens;
  private @Nullable String failureMessage;
  private @Nullable Instant submittedAt;
  private @Nullable Instant finishedAt;
  private Instant createdAt;
  private Instant updatedAt;
  @Version private @Nullable Long version;

  protected EnrichmentJob() {}

  public EnrichmentJob(UUID supplierId, String model, Instant now) {
    this.id = Ids.newId();
    this.supplierId = supplierId;
    this.model = model;
    this.status = EnrichmentStatus.QUEUED;
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** The products were sent to the model as one batch. */
  public void submitted(String batchId, int productsTotal, Instant now) {
    requireStatus(EnrichmentStatus.QUEUED);
    this.batchId = batchId;
    this.productsTotal = productsTotal;
    this.status = EnrichmentStatus.SUBMITTED;
    this.submittedAt = now;
  }

  /** The supplier has no active products: nothing to send. */
  public void completeEmpty(Instant now) {
    requireStatus(EnrichmentStatus.QUEUED);
    this.status = EnrichmentStatus.COMPLETED;
    this.finishedAt = now;
  }

  /** The batch ended and its suggestions were checked and saved. */
  public void complete(
      int productsFailed,
      int suggestionsCreated,
      int suggestionsDropped,
      TokenUsage usage,
      Instant now) {
    requireStatus(EnrichmentStatus.SUBMITTED);
    this.productsFailed = productsFailed;
    this.suggestionsCreated = suggestionsCreated;
    this.suggestionsDropped = suggestionsDropped;
    this.inputTokens = usage.inputTokens();
    this.outputTokens = usage.outputTokens();
    this.cacheReadTokens = usage.cacheReadTokens();
    this.cacheWriteTokens = usage.cacheWriteTokens();
    this.status = EnrichmentStatus.COMPLETED;
    this.finishedAt = now;
  }

  /** Stops the job for good. The message is shown to the supplier: never include internals. */
  public void fail(String message, Instant now) {
    if (status.isFinished()) {
      return;
    }
    this.failureMessage = message.length() > 500 ? message.substring(0, 500) : message;
    this.status = EnrichmentStatus.FAILED;
    this.finishedAt = now;
  }

  private void requireStatus(EnrichmentStatus expected) {
    if (status != expected) {
      throw new IllegalStateException("Enrichment job " + id + " is " + status);
    }
  }

  @PreUpdate
  void touch() {
    updatedAt = Instant.now();
  }

  public UUID id() {
    return id;
  }

  public UUID supplierId() {
    return supplierId;
  }

  public EnrichmentStatus status() {
    return status;
  }

  public String model() {
    return model;
  }

  public @Nullable String batchId() {
    return batchId;
  }

  public int productsTotal() {
    return productsTotal;
  }

  public int productsFailed() {
    return productsFailed;
  }

  public int suggestionsCreated() {
    return suggestionsCreated;
  }

  public int suggestionsDropped() {
    return suggestionsDropped;
  }

  public TokenUsage tokenUsage() {
    return new TokenUsage(inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens);
  }

  public @Nullable String failureMessage() {
    return failureMessage;
  }

  public @Nullable Instant submittedAt() {
    return submittedAt;
  }

  public @Nullable Instant finishedAt() {
    return finishedAt;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
