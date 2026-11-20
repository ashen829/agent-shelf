package com.agentshelf.enrichment.internal.application;

import com.agentshelf.catalog.CatalogApi;
import com.agentshelf.catalog.ProductSnapshot;
import com.agentshelf.enrichment.internal.domain.EnrichmentJob;
import com.agentshelf.enrichment.internal.domain.EnrichmentStatus;
import com.agentshelf.enrichment.internal.domain.ProductSuggestion;
import com.agentshelf.enrichment.internal.domain.ProposedChange;
import com.agentshelf.enrichment.internal.domain.SuggestionRules;
import com.agentshelf.enrichment.internal.model.EnrichmentProperties;
import com.agentshelf.enrichment.internal.model.ProductEnrichmentModel;
import com.agentshelf.enrichment.internal.model.ProductEnrichmentModel.BatchOutcome;
import com.agentshelf.enrichment.internal.persistence.EnrichmentJobRepository;
import com.agentshelf.enrichment.internal.persistence.ProductSuggestionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs one AI cleanup job in the background (a db-scheduler task, ADR 0003), step by step: send the
 * products as one batch, then check the batch until it has ended, then check and save the
 * suggestions. There is no signed-in user here: the supplier ID always comes from the job itself.
 *
 * <p>Safe to run again after a crash: saving deletes the job's earlier suggestions first.
 */
@Component
public class EnrichmentRunner {

  public static final int MAX_ATTEMPTS = 5;
  static final Duration FIRST_CHECK = Duration.ofSeconds(30);
  static final Duration CHECK_EVERY = Duration.ofMinutes(1);

  /** Batches end within 24 hours; after that something is wrong. */
  static final Duration GIVE_UP_AFTER = Duration.ofHours(25);

  static final int MAX_CATEGORIES = 100;
  static final String FAILED_MESSAGE =
      "AI suggestions stopped because of an unexpected error. Try again later, and contact"
          + " support if it keeps happening.";

  private static final Logger log = LoggerFactory.getLogger(EnrichmentRunner.class);

  private final EnrichmentJobRepository jobs;
  private final ProductSuggestionRepository suggestions;
  private final CatalogApi catalog;
  private final ObjectProvider<ProductEnrichmentModel> model;
  private final EnrichmentProperties properties;
  private final TransactionTemplate tx;

  EnrichmentRunner(
      EnrichmentJobRepository jobs,
      ProductSuggestionRepository suggestions,
      CatalogApi catalog,
      ObjectProvider<ProductEnrichmentModel> model,
      EnrichmentProperties properties,
      TransactionTemplate tx) {
    this.jobs = jobs;
    this.suggestions = suggestions;
    this.catalog = catalog;
    this.model = model;
    this.properties = properties;
    this.tx = tx;
  }

  /**
   * Does the next step of a job.
   *
   * @param previousFailures how often this step has already failed; on the last attempt the job is
   *     marked failed instead of retried
   * @return when to run the next step, or null when the job is finished
   */
  public @Nullable Instant run(UUID jobId, int previousFailures) {
    try {
      return step(jobId);
    } catch (RuntimeException e) {
      if (previousFailures + 1 < MAX_ATTEMPTS) {
        log.warn(
            "Enrichment job {} failed (attempt {}), will retry", jobId, previousFailures + 1, e);
        throw e;
      }
      log.error("Enrichment job {} failed for good", jobId, e);
      fail(jobId, FAILED_MESSAGE);
      return null;
    }
  }

  private @Nullable Instant step(UUID jobId) {
    EnrichmentJob job = jobs.findById(jobId).orElse(null);
    if (job == null || job.status().isFinished()) {
      return null;
    }
    ProductEnrichmentModel ai = model.getIfAvailable();
    if (ai == null) {
      fail(jobId, "AI suggestions are switched off.");
      return null;
    }
    return job.status() == EnrichmentStatus.QUEUED ? submit(job, ai) : check(job, ai);
  }

  private @Nullable Instant submit(EnrichmentJob job, ProductEnrichmentModel ai) {
    List<ProductSnapshot> products =
        catalog.activeProducts(job.supplierId(), properties.maxProducts());
    if (products.isEmpty()) {
      update(job.id(), j -> j.completeEmpty(Instant.now()));
      return null;
    }
    List<String> categories = catalog.categoryNames(job.supplierId(), MAX_CATEGORIES);
    // Outside a transaction: the call can take a few seconds. If saving the batch ID fails, the
    // next attempt submits again (and that batch is billed twice). Rare, and cheap at Haiku prices.
    String batchId = ai.submit(products, categories);
    update(job.id(), j -> j.submitted(batchId, products.size(), Instant.now()));
    return Instant.now().plus(FIRST_CHECK);
  }

  private @Nullable Instant check(EnrichmentJob job, ProductEnrichmentModel ai) {
    Instant submittedAt = job.submittedAt();
    String batchId = job.batchId();
    if (batchId == null
        || submittedAt == null
        || submittedAt.plus(GIVE_UP_AFTER).isBefore(Instant.now())) {
      fail(job.id(), "AI suggestions took too long. Try again later.");
      return null;
    }
    Optional<BatchOutcome> outcome = ai.poll(batchId);
    if (outcome.isEmpty()) {
      return Instant.now().plus(CHECK_EVERY);
    }
    tx.executeWithoutResult(status -> save(job.id(), outcome.get()));
    return null;
  }

  private void save(UUID jobId, BatchOutcome outcome) {
    EnrichmentJob job = jobs.findById(jobId).orElseThrow();
    if (job.status() != EnrichmentStatus.SUBMITTED) {
      return;
    }
    suggestions.deleteByEnrichmentJobId(jobId);
    // Products as they are now: one deleted or archived since is skipped, and the version saved
    // with each suggestion is the one it was checked against.
    Map<UUID, ProductSnapshot> current =
        catalog.products(job.supplierId(), outcome.proposals().keySet()).stream()
            .collect(Collectors.toMap(ProductSnapshot::id, Function.identity()));
    Instant now = Instant.now();
    int created = 0;
    int dropped = 0;
    for (Map.Entry<UUID, List<ProposedChange>> entry : outcome.proposals().entrySet()) {
      ProductSnapshot product = current.get(entry.getKey());
      if (product == null) {
        continue;
      }
      SuggestionRules.Checked checked = SuggestionRules.check(product, entry.getValue());
      for (ProductSuggestion.Change change : checked.accepted()) {
        suggestions.save(
            new ProductSuggestion(
                job.supplierId(), job.id(), product.id(), product.version(), change, now));
      }
      created += checked.accepted().size();
      dropped += checked.dropped();
    }
    int failed = Math.max(0, job.productsTotal() - outcome.proposals().size());
    job.complete(failed, created, dropped, outcome.usage(), now);
    log.info(
        "Enrichment job {}: {} suggestions, {} dropped, {} products failed, {}",
        jobId,
        created,
        dropped,
        failed,
        outcome.usage());
  }

  private void fail(UUID jobId, String message) {
    update(jobId, job -> job.fail(message, Instant.now()));
  }

  private void update(UUID jobId, Consumer<EnrichmentJob> change) {
    tx.executeWithoutResult(status -> jobs.findById(jobId).ifPresent(change));
  }
}
