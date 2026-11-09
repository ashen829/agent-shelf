package com.agentshelf.catalog.internal.domain;

import com.agentshelf.shared.CurrencyCode;
import com.agentshelf.shared.DomainException;
import com.agentshelf.shared.Ids;
import com.agentshelf.shared.Money;
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

/** A product one supplier sells. */
@Entity
@Table(schema = "catalog", name = "product")
public class Product {

  @Id private UUID id;
  private UUID supplierId;
  private String sku;
  private String name;
  private @Nullable String description;
  private @Nullable String category;
  private @Nullable String unit;
  private BigDecimal priceAmount;
  private String priceCurrency;
  private int stockQty;

  @Enumerated(EnumType.STRING)
  private ProductStatus status;

  private Instant createdAt;
  private Instant updatedAt;
  @Version private @Nullable Long version;

  protected Product() {}

  public Product(UUID supplierId, ProductDetails details, Instant now) {
    this.id = Ids.newId();
    this.supplierId = supplierId;
    this.status = ProductStatus.ACTIVE;
    this.createdAt = now;
    this.updatedAt = now;
    this.sku = details.sku();
    this.name = details.name();
    this.priceAmount = details.price().amount();
    this.priceCurrency = details.price().currency().value();
    this.stockQty = details.stockQty();
    apply(details);
  }

  /** Replaces the editable fields. The currency can't change: it is the supplier's currency. */
  public void update(ProductDetails details) {
    if (!details.price().currency().value().equals(priceCurrency)) {
      throw new DomainException("A product's currency can't change.");
    }
    apply(details);
  }

  public void archive() {
    status = ProductStatus.ARCHIVED;
  }

  public void restore() {
    status = ProductStatus.ACTIVE;
  }

  private void apply(ProductDetails details) {
    sku = details.sku();
    name = details.name();
    description = details.description();
    category = details.category();
    unit = details.unit();
    priceAmount = details.price().amount();
    priceCurrency = details.price().currency().value();
    stockQty = details.stockQty();
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

  public String sku() {
    return sku;
  }

  public String name() {
    return name;
  }

  public @Nullable String description() {
    return description;
  }

  public @Nullable String category() {
    return category;
  }

  public @Nullable String unit() {
    return unit;
  }

  public Money price() {
    return new Money(priceAmount, new CurrencyCode(priceCurrency));
  }

  public int stockQty() {
    return stockQty;
  }

  public ProductStatus status() {
    return status;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  /** Zero before the first save. */
  public long version() {
    return version == null ? 0 : version;
  }
}
