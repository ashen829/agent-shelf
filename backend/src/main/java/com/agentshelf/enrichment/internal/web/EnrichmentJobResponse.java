package com.agentshelf.enrichment.internal.web;

import com.agentshelf.enrichment.internal.domain.EnrichmentJob;
import com.agentshelf.enrichment.internal.domain.EnrichmentStatus;
import com.agentshelf.enrichment.internal.domain.TokenUsage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One AI cleanup job: status, results and the tokens it used. */
record EnrichmentJobResponse(
    UUID id,
    EnrichmentStatus status,
    String model,
    int productsTotal,
    int productsFailed,
    int suggestionsCreated,
    int suggestionsDropped,
    long inputTokens,
    long outputTokens,
    long cacheReadTokens,
    long cacheWriteTokens,
    @Schema(nullable = true) @Nullable String failureMessage,
    Instant createdAt,
    @Schema(nullable = true) @Nullable Instant submittedAt,
    @Schema(nullable = true) @Nullable Instant finishedAt) {

  static EnrichmentJobResponse from(EnrichmentJob job) {
    TokenUsage usage = job.tokenUsage();
    return new EnrichmentJobResponse(
        job.id(),
        job.status(),
        job.model(),
        job.productsTotal(),
        job.productsFailed(),
        job.suggestionsCreated(),
        job.suggestionsDropped(),
        usage.inputTokens(),
        usage.outputTokens(),
        usage.cacheReadTokens(),
        usage.cacheWriteTokens(),
        job.failureMessage(),
        job.createdAt(),
        job.submittedAt(),
        job.finishedAt());
  }
}
