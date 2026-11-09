package com.agentshelf.catalog.internal.domain;

import com.agentshelf.shared.DomainException;
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

/** One CSV import of one supplier: the file's shape, the column mapping, progress and result. */
@Entity
@Table(schema = "catalog", name = "import_job")
public class ImportJob {

  @Id private UUID id;
  private UUID supplierId;
  private String fileName;
  private char delimiter;
  private int rowsTotal;

  @Enumerated(EnumType.STRING)
  private ImportStatus status;

  private @Nullable Integer columnSku;
  private @Nullable Integer columnName;
  private @Nullable Integer columnDescription;
  private @Nullable Integer columnCategory;
  private @Nullable Integer columnUnit;
  private @Nullable Integer columnPrice;
  private @Nullable Integer columnStockQty;
  private @Nullable Character decimalSeparator;
  private boolean completeCatalog;

  private int rowsProcessed;
  private int rowsCreated;
  private int rowsUpdated;
  private int rowsUnchanged;
  private int rowsFailed;
  private int productsArchived;
  private @Nullable String failureMessage;
  private @Nullable Instant startedAt;
  private @Nullable Instant finishedAt;
  private Instant createdAt;
  private Instant updatedAt;
  @Version private @Nullable Long version;

  protected ImportJob() {}

  public ImportJob(UUID supplierId, String fileName, char delimiter, int rowsTotal, Instant now) {
    this.id = Ids.newId();
    this.supplierId = supplierId;
    this.fileName = fileName;
    this.delimiter = delimiter;
    this.rowsTotal = rowsTotal;
    this.status = ImportStatus.UPLOADED;
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** The supplier matched the columns and pressed Import. */
  public void queue(ColumnMapping mapping, char decimalSeparator, boolean completeCatalog) {
    if (status != ImportStatus.UPLOADED) {
      throw new DomainException("This import has already been started.");
    }
    this.columnSku = mapping.sku();
    this.columnName = mapping.name();
    this.columnDescription = mapping.description();
    this.columnCategory = mapping.category();
    this.columnUnit = mapping.unit();
    this.columnPrice = mapping.price();
    this.columnStockQty = mapping.stockQty();
    this.decimalSeparator = decimalSeparator;
    this.completeCatalog = completeCatalog;
    this.status = ImportStatus.QUEUED;
  }

  /** Starts (or, after a crash, restarts) the run from the first row. */
  public void begin(Instant now) {
    status = ImportStatus.RUNNING;
    startedAt = now;
    rowsProcessed = 0;
    rowsCreated = 0;
    rowsUpdated = 0;
    rowsUnchanged = 0;
    rowsFailed = 0;
    productsArchived = 0;
  }

  public void recordBatch(int processed, int created, int updated, int unchanged, int failed) {
    rowsProcessed += processed;
    rowsCreated += created;
    rowsUpdated += updated;
    rowsUnchanged += unchanged;
    rowsFailed += failed;
  }

  public void recordArchived(int archived) {
    productsArchived = archived;
  }

  public void finish(Instant now) {
    status = rowsFailed > 0 ? ImportStatus.COMPLETED_WITH_ERRORS : ImportStatus.COMPLETED;
    finishedAt = now;
  }

  public void fail(String message, Instant now) {
    status = ImportStatus.FAILED;
    failureMessage = message.length() > 500 ? message.substring(0, 500) : message;
    finishedAt = now;
  }

  @PreUpdate
  void touch() {
    updatedAt = Instant.now();
  }

  public ColumnMapping mapping() {
    return new ColumnMapping(
        columnSku,
        columnName,
        columnDescription,
        columnCategory,
        columnUnit,
        columnPrice,
        columnStockQty);
  }

  public UUID id() {
    return id;
  }

  public UUID supplierId() {
    return supplierId;
  }

  public String fileName() {
    return fileName;
  }

  public char delimiter() {
    return delimiter;
  }

  public int rowsTotal() {
    return rowsTotal;
  }

  public ImportStatus status() {
    return status;
  }

  public char decimalSeparator() {
    return decimalSeparator == null ? '.' : decimalSeparator;
  }

  public boolean completeCatalog() {
    return completeCatalog;
  }

  public int rowsProcessed() {
    return rowsProcessed;
  }

  public int rowsCreated() {
    return rowsCreated;
  }

  public int rowsUpdated() {
    return rowsUpdated;
  }

  public int rowsUnchanged() {
    return rowsUnchanged;
  }

  public int rowsFailed() {
    return rowsFailed;
  }

  public int productsArchived() {
    return productsArchived;
  }

  public @Nullable String failureMessage() {
    return failureMessage;
  }

  public @Nullable Instant startedAt() {
    return startedAt;
  }

  public @Nullable Instant finishedAt() {
    return finishedAt;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
