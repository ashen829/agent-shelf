package com.agentshelf.enrichment.internal.application;

import com.agentshelf.enrichment.internal.domain.EnrichmentJob;
import com.agentshelf.enrichment.internal.domain.ProductSuggestion;
import com.agentshelf.enrichment.internal.model.EnrichmentProperties;
import com.agentshelf.enrichment.internal.model.ProductEnrichmentModel;
import com.agentshelf.enrichment.internal.persistence.EnrichmentJobRepository;
import com.agentshelf.enrichment.internal.persistence.ProductSuggestionRepository;
import com.agentshelf.identity.CurrentUser;
import com.agentshelf.shared.ConflictException;
import com.agentshelf.shared.DomainException;
import com.agentshelf.shared.NotFoundException;
import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.helper.CustomTask;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** AI cleanup use cases for the signed-in supplier. The job itself runs in EnrichmentRunner. */
@Service
public class EnrichmentService {

  static final String NOT_FOUND = "AI suggestion job not found.";
  static final String SWITCHED_OFF = "AI suggestions are not switched on yet.";
  static final String ALREADY_RUNNING =
      "AI suggestions are already being prepared for your catalog. Wait until they are ready.";

  private final EnrichmentJobRepository jobs;
  private final ProductSuggestionRepository suggestions;
  private final ObjectProvider<ProductEnrichmentModel> model;
  private final EnrichmentProperties properties;
  private final Scheduler scheduler;
  private final CustomTask<String> enrichmentJobTask;

  EnrichmentService(
      EnrichmentJobRepository jobs,
      ProductSuggestionRepository suggestions,
      ObjectProvider<ProductEnrichmentModel> model,
      EnrichmentProperties properties,
      Scheduler scheduler,
      CustomTask<String> enrichmentJobTask) {
    this.jobs = jobs;
    this.suggestions = suggestions;
    this.model = model;
    this.properties = properties;
    this.scheduler = scheduler;
    this.enrichmentJobTask = enrichmentJobTask;
  }

  /** Feature flag: true only when switched on and the model is configured. */
  public boolean enabled() {
    return properties.enabled() && model.getIfAvailable() != null;
  }

  /** Queues a job over the supplier's active products. One active job per supplier. */
  @Transactional
  public EnrichmentJob start(CurrentUser user) {
    ProductEnrichmentModel ai = properties.enabled() ? model.getIfAvailable() : null;
    if (ai == null) {
      throw new DomainException(SWITCHED_OFF);
    }
    EnrichmentJob job;
    try {
      job = jobs.saveAndFlush(new EnrichmentJob(user.supplierId(), ai.modelName(), Instant.now()));
    } catch (DataIntegrityViolationException e) {
      if (String.valueOf(e.getMostSpecificCause().getMessage())
          .contains("enrichment_job_one_active_uk")) {
        throw new ConflictException(ALREADY_RUNNING);
      }
      throw e;
    }
    String id = job.id().toString();
    // Same transaction: if this fails, no job row is left behind.
    scheduler.scheduleIfNotExists(enrichmentJobTask.instance(id, id), Instant.now());
    // The scheduler only sees the new task after commit; this starts it without waiting for a poll.
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            scheduler.triggerCheckForDueExecutions();
          }
        });
    return job;
  }

  @Transactional(readOnly = true)
  public EnrichmentJob get(CurrentUser user, UUID id) {
    return find(user, id);
  }

  @Transactional(readOnly = true)
  public Optional<EnrichmentJob> latest(CurrentUser user) {
    return jobs.findFirstBySupplierIdOrderByCreatedAtDesc(user.supplierId());
  }

  @Transactional(readOnly = true)
  public Page<EnrichmentJob> list(CurrentUser user, int page, int size) {
    return jobs.findBySupplierIdOrderByCreatedAtDesc(user.supplierId(), PageRequest.of(page, size));
  }

  @Transactional(readOnly = true)
  public Page<ProductSuggestion> suggestions(CurrentUser user, UUID jobId, int page, int size) {
    find(user, jobId);
    return suggestions.findBySupplierIdAndEnrichmentJobIdOrderByProductIdAscFieldAsc(
        user.supplierId(), jobId, PageRequest.of(page, size));
  }

  private EnrichmentJob find(CurrentUser user, UUID id) {
    return jobs.findByIdAndSupplierId(id, user.supplierId())
        .orElseThrow(() -> new NotFoundException(NOT_FOUND));
  }
}
