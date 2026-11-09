package com.agentshelf.catalog.internal.importing;

import com.agentshelf.catalog.internal.domain.ColumnMapping;
import com.agentshelf.catalog.internal.domain.ImportError;
import com.agentshelf.catalog.internal.domain.ImportJob;
import com.agentshelf.catalog.internal.persistence.ImportErrorRepository;
import com.agentshelf.catalog.internal.persistence.ImportFileStore;
import com.agentshelf.catalog.internal.persistence.ImportJobRepository;
import com.agentshelf.identity.CurrentUser;
import com.agentshelf.shared.ConflictException;
import com.agentshelf.shared.DomainException;
import com.agentshelf.shared.NotFoundException;
import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** CSV import use cases for the signed-in supplier. The import itself runs in ImportRunner. */
@Service
public class ImportService {

  static final String NOT_FOUND = "Import not found.";
  static final String ALREADY_RUNNING =
      "Another import is still running. Wait until it has finished, then start this one.";
  private static final int PREVIEW_ROWS = 5;

  private final ImportJobRepository jobs;
  private final ImportErrorRepository errors;
  private final ImportFileStore files;
  private final Scheduler scheduler;
  private final OneTimeTask<String> csvImportTask;

  ImportService(
      ImportJobRepository jobs,
      ImportErrorRepository errors,
      ImportFileStore files,
      Scheduler scheduler,
      OneTimeTask<String> csvImportTask) {
    this.jobs = jobs;
    this.errors = errors;
    this.files = files;
    this.scheduler = scheduler;
    this.csvImportTask = csvImportTask;
  }

  /** The file's columns, its first rows and a suggested column mapping. */
  public record Preview(
      List<String> headers, List<CsvReader.Row> rows, int rowCount, ColumnMapping suggested) {}

  /**
   * Checks and stores an upload. Only quick checks run here (encoding, size, row count); the
   * products are imported later, in the background.
   */
  @Transactional
  public ImportJob upload(CurrentUser user, String fileName, byte[] content) {
    CsvReader.Summary summary = CsvReader.inspect(content);
    // Flush now: the file row below references the job row (foreign key).
    ImportJob job =
        jobs.saveAndFlush(
            new ImportJob(
                user.supplierId(),
                cleanFileName(fileName),
                summary.delimiter(),
                summary.rowCount(),
                Instant.now()));
    files.save(job.id(), user.supplierId(), content);
    return job;
  }

  @Transactional(readOnly = true)
  public Preview preview(CurrentUser user, UUID id) {
    ImportJob job = find(user, id);
    byte[] content = files.load(job.id(), user.supplierId());
    List<String> headers = CsvReader.headers(content, job.delimiter());
    return new Preview(
        headers,
        CsvReader.preview(content, job.delimiter(), PREVIEW_ROWS),
        job.rowsTotal(),
        HeaderMatcher.suggest(headers));
  }

  /** Saves the mapping and queues the background run. One active import per supplier. */
  @Transactional
  public ImportJob start(
      CurrentUser user,
      UUID id,
      ColumnMapping mapping,
      char decimalSeparator,
      boolean completeCatalog) {
    ImportJob job = find(user, id);
    if (decimalSeparator != '.' && decimalSeparator != ',') {
      throw new DomainException("The decimal separator must be . or ,");
    }
    byte[] content = files.load(job.id(), user.supplierId());
    mapping.validate(CsvReader.headers(content, job.delimiter()).size());
    job.queue(mapping, decimalSeparator, completeCatalog);
    try {
      jobs.saveAndFlush(job);
    } catch (DataIntegrityViolationException e) {
      if (String.valueOf(e.getMostSpecificCause().getMessage())
          .contains("import_job_one_active_uk")) {
        throw new ConflictException(ALREADY_RUNNING);
      }
      throw e;
    }
    // Same transaction: if this fails, the job stays UPLOADED.
    scheduler.scheduleIfNotExists(
        csvImportTask.instance(job.id().toString(), job.id().toString()), Instant.now());
    // The scheduler can only see the new task after commit. Without this nudge it would wait
    // for its next poll (up to 10 s) before starting the import.
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
  public ImportJob get(CurrentUser user, UUID id) {
    return find(user, id);
  }

  @Transactional(readOnly = true)
  public Page<ImportJob> list(CurrentUser user, int page, int size) {
    return jobs.findBySupplierIdOrderByCreatedAtDesc(user.supplierId(), PageRequest.of(page, size));
  }

  @Transactional(readOnly = true)
  public Page<ImportError> errors(CurrentUser user, UUID id, int page, int size) {
    find(user, id);
    return errors.findByImportJobIdAndSupplierIdOrderByRowNumber(
        id, user.supplierId(), PageRequest.of(page, size));
  }

  /** The failed rows as a CSV file, to fix and upload again. */
  @Transactional(readOnly = true)
  public byte[] errorReport(CurrentUser user, UUID id) {
    ImportJob job = find(user, id);
    byte[] content = files.load(job.id(), user.supplierId());
    return ErrorReport.build(
        CsvReader.headers(content, job.delimiter()),
        job.delimiter(),
        errors.findAllByImportJobIdAndSupplierIdOrderByRowNumber(id, user.supplierId()));
  }

  private ImportJob find(CurrentUser user, UUID id) {
    return jobs.findByIdAndSupplierId(id, user.supplierId())
        .orElseThrow(() -> new NotFoundException(NOT_FOUND));
  }

  /** Only the name, never a path; at most 255 characters. Shown back, never used as a path. */
  static String cleanFileName(String fileName) {
    String name = fileName.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).strip();
    name = name.replaceAll("\\p{Cntrl}", "");
    if (name.isEmpty()) {
      name = "upload.csv";
    }
    return name.length() > 255 ? name.substring(0, 255) : name;
  }
}
