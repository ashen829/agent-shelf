package com.agentshelf.identity.internal.domain;

import com.agentshelf.shared.CurrencyCode;
import com.agentshelf.shared.Ids;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A wholesaler using AgentShelf. The tenant that owns users, products, rules and quotes. */
@Entity
@Table(schema = "identity", name = "supplier")
public class Supplier {

  @Id private UUID id;
  private String name;
  private String slug;
  private String currency;
  private Instant createdAt;
  private Instant updatedAt;
  @Version private @Nullable Long version;

  protected Supplier() {}

  public Supplier(String name, String slug, CurrencyCode currency, Instant now) {
    String trimmed = name.strip();
    if (trimmed.length() < 2 || trimmed.length() > 100) {
      throw new IllegalArgumentException("Supplier name must be 2 to 100 characters");
    }
    this.id = Ids.newId();
    this.name = trimmed;
    this.slug = slug;
    this.currency = currency.value();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void touch() {
    updatedAt = Instant.now();
  }

  public UUID id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String slug() {
    return slug;
  }

  public CurrencyCode currency() {
    return new CurrencyCode(currency);
  }
}
