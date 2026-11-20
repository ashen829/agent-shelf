package com.agentshelf.enrichment.internal.model;

import com.agentshelf.catalog.ProductSnapshot;
import com.agentshelf.enrichment.internal.domain.ProposedChange;
import com.agentshelf.enrichment.internal.domain.TokenUsage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Port to the AI model that suggests product fixes (ADR 0006). Work is asynchronous: submit a
 * batch, then poll until it has ended. The Claude adapter uses the Message Batches API; tests use a
 * fake.
 */
public interface ProductEnrichmentModel {

  /** The model's name, stored on every job. */
  String modelName();

  /**
   * Sends the products as one batch.
   *
   * @param categories the supplier's categories in use, so suggestions reuse them
   * @return the batch ID to poll
   */
  String submit(List<ProductSnapshot> products, List<String> categories);

  /** Empty while the batch is still running; the outcome once it has ended. */
  Optional<BatchOutcome> poll(String batchId);

  /**
   * What a finished batch produced.
   *
   * @param proposals per product ID: the changes the model proposed (unchecked)
   * @param failedProducts products the model gave no usable answer for (errors, refusals, bad JSON)
   */
  record BatchOutcome(
      Map<UUID, List<ProposedChange>> proposals, int failedProducts, TokenUsage usage) {}
}
