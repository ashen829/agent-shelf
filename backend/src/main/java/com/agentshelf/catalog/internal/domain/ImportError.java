package com.agentshelf.catalog.internal.domain;

import com.agentshelf.shared.Ids;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A row that could not be imported, with the reason and the original row. Never changed. */
@Entity
@Table(schema = "catalog", name = "import_error")
public class ImportError {

  @Id private UUID id;
  private UUID importJobId;
  private UUID supplierId;
  private int rowNumber;
  private @Nullable String field;
  private String message;
  private String rawRecord;
  private Instant createdAt;

  protected ImportError() {}

  public ImportError(
      ImportJob job, long rowNumber, @Nullable String field, String message, String rawRecord) {
    this.id = Ids.newId();
    this.importJobId = job.id();
    this.supplierId = job.supplierId();
    this.rowNumber = Math.toIntExact(rowNumber);
    this.field = field;
    this.message = message.length() > 500 ? message.substring(0, 500) : message;
    this.rawRecord = rawRecord;
    this.createdAt = Instant.now();
  }

  public int rowNumber() {
    return rowNumber;
  }

  public @Nullable String field() {
    return field;
  }

  public String message() {
    return message;
  }

  public String rawRecord() {
    return rawRecord;
  }
}
