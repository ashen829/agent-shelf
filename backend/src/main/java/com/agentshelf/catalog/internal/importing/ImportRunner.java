package com.agentshelf.catalog.internal.importing;

import com.agentshelf.catalog.internal.domain.ImportJob;
import com.agentshelf.catalog.internal.domain.ImportStatus;
import com.agentshelf.catalog.internal.persistence.ImportErrorRepository;
import com.agentshelf.catalog.internal.persistence.ImportFileStore;
import com.agentshelf.catalog.internal.persistence.ImportJobRepository;
import com.agentshelf.catalog.internal.persistence.ProductRepository;
import com.agentshelf.identity.IdentityApi;
import com.agentshelf.shared.CurrencyCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs one import in the background (a db-scheduler task, ADR 0003). There is no signed-in user
 * here: the supplier ID always comes from the import job itself.
 *
 * <p>Safe to run again after a crash: it restarts from the first row, and applying a row twice
 * gives the same product (rows are matched by SKU).
 */
@Component
public class ImportRunner {

  static final int BATCH_SIZE = 500;
  static final int MAX_ATTEMPTS = 3;
  private static final Logger log = LoggerFactory.getLogger(ImportRunner.class);

  private final ImportJobRepository jobs;
  private final ImportErrorRepository errors;
  private final ImportFileStore files;
  private final ProductRepository products;
  private final ProductImporter importer;
  private final IdentityApi identity;
  private final TransactionTemplate tx;

  ImportRunner(
      ImportJobRepository jobs,
      ImportErrorRepository errors,
      ImportFileStore files,
      ProductRepository products,
      ProductImporter importer,
      IdentityApi identity,
      TransactionTemplate tx) {
    this.jobs = jobs;
    this.errors = errors;
    this.files = files;
    this.products = products;
    this.importer = importer;
    this.identity = identity;
    this.tx = tx;
  }

  /**
   * @param previousFailures how often this run has already failed; on the last attempt a failure is
   *     recorded on the job instead of being retried.
   */
  public void run(UUID jobId, int previousFailures) {
    try {
      runOnce(jobId);
    } catch (RuntimeException e) {
      if (previousFailures + 1 < MAX_ATTEMPTS) {
        log.warn("Import {} failed (attempt {}), will retry", jobId, previousFailures + 1, e);
        throw e;
      }
      log.error("Import {} failed for good", jobId, e);
      tx.executeWithoutResult(status ->
          jobs.findById(jobId)
              .ifPresent(job ->
                  job.fail(
                      "The import stopped because of an unexpected error. Try again,"
                          + " and contact support if it keeps happening.",
                      Instant.now())));
    }
  }

  private void runOnce(UUID jobId) {
    ImportJob started = tx.execute(status -> begin(jobId));
    if (started == null) {
      return;
    }
    UUID supplierId = started.supplierId();
    CurrencyCode currency = identity.supplierCurrency(supplierId);
    byte[] content = files.load(jobId, supplierId);
    Map<String, Long> firstRowBySku = new HashMap<>();
    List<CsvReader.Row> batch = new ArrayList<>(BATCH_SIZE);

    CsvReader.forEachRow(
        content,
        started.delimiter(),
        row -> {
          batch.add(row);
          if (batch.size() == BATCH_SIZE) {
            applyBatch(jobId, currency, batch, firstRowBySku);
            batch.clear();
          }
        });
    if (!batch.isEmpty()) {
      applyBatch(jobId, currency, batch, firstRowBySku);
    }
    tx.executeWithoutResult(status -> finish(jobId, firstRowBySku));
  }

  /** Returns null when there is nothing to do (already finished, or not started). */
  private @Nullable ImportJob begin(UUID jobId) {
    ImportJob job = jobs.findById(jobId).orElse(null);
    if (job == null || job.status().isFinished() || job.status() == ImportStatus.UPLOADED) {
      return null;
    }
    errors.deleteByImportJobId(jobId);
    job.begin(Instant.now());
    return job;
  }

  private void applyBatch(
      UUID jobId,
      CurrencyCode currency,
      List<CsvReader.Row> rows,
      Map<String, Long> firstRowBySku) {
    tx.executeWithoutResult(status -> {
      ImportJob job = jobs.findById(jobId).orElseThrow();
      ProductImporter.BatchResult result = importer.importBatch(job, currency, rows, firstRowBySku);
      job.recordBatch(
          rows.size(), result.created(), result.updated(), result.unchanged(), result.failed());
    });
  }

  /**
   * "Complete catalog": archive active products whose SKU is not anywhere in the file. A row with
   * an error still counts as present, so a typo never archives a product.
   */
  private void finish(UUID jobId, Map<String, Long> skusInFile) {
    ImportJob job = jobs.findById(jobId).orElseThrow();
    int archived = job.completeCatalog() ? archiveMissing(job.supplierId(), skusInFile) : 0;
    // Load again: the bulk archive query clears the persistence context, detaching "job".
    ImportJob current = jobs.findById(jobId).orElseThrow();
    current.recordArchived(archived);
    current.finish(Instant.now());
  }

  private int archiveMissing(UUID supplierId, Map<String, Long> skusInFile) {
    List<UUID> missing =
        products.findActiveSkus(supplierId).stream()
            .filter(row -> !skusInFile.containsKey((String) row[1]))
            .map(row -> (UUID) row[0])
            .toList();
    int archived = 0;
    for (int from = 0; from < missing.size(); from += BATCH_SIZE) {
      archived +=
          products.archiveAll(
              supplierId,
              missing.subList(from, Math.min(from + BATCH_SIZE, missing.size())),
              Instant.now());
    }
    return archived;
  }
}
