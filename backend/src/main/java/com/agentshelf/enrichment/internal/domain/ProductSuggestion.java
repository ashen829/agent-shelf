package com.agentshelf.enrichment.internal.domain;

import com.agentshelf.shared.Ids;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One suggested change to one product field. It never changes the product by itself: the supplier
 * approves or rejects it. {@code productVersion} is the product's version when it was suggested.
 */
@Entity
@Table(schema = "enrichment", name = "product_suggestion")
public class ProductSuggestion {

  @Id private UUID id;
  private UUID supplierId;
  private UUID enrichmentJobId;
  private UUID productId;
  private long productVersion;

  @Enumerated(EnumType.STRING)
  private SuggestionField field;

  private @Nullable String oldValue;
  private String newValue;
  private String reason;
  private BigDecimal confidence;

  @Enumerated(EnumType.STRING)
  private SuggestionStatus status;

  private Instant createdAt;
  private Instant updatedAt;
  @Version private @Nullable Long version;

  protected ProductSuggestion() {}

  /** Built only from a checked {@link Change}, see {@link SuggestionRules}. */
  public ProductSuggestion(
      UUID supplierId,
      UUID enrichmentJobId,
      UUID productId,
      long productVersion,
      Change change,
      Instant now) {
    this.id = Ids.newId();
    this.supplierId = supplierId;
    this.enrichmentJobId = enrichmentJobId;
    this.productId = productId;
    this.productVersion = productVersion;
    this.field = change.field();
    this.oldValue = change.oldValue();
    this.newValue = change.newValue();
    this.reason = change.reason();
    this.confidence = change.confidence();
    this.status = SuggestionStatus.PENDING;
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** A change that passed every check in {@link SuggestionRules}. */
  public record Change(
      SuggestionField field,
      @Nullable String oldValue,
      String newValue,
      String reason,
      BigDecimal confidence) {}

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

  public UUID enrichmentJobId() {
    return enrichmentJobId;
  }

  public UUID productId() {
    return productId;
  }

  public long productVersion() {
    return productVersion;
  }

  public SuggestionField field() {
    return field;
  }

  public @Nullable String oldValue() {
    return oldValue;
  }

  public String newValue() {
    return newValue;
  }

  public String reason() {
    return reason;
  }

  public BigDecimal confidence() {
    return confidence;
  }

  public SuggestionStatus status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
