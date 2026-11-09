package com.agentshelf.identity.internal.domain;

import com.agentshelf.identity.Role;
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

/** A person who signs in to the dashboard for one supplier. */
@Entity
@Table(schema = "identity", name = "app_user")
public class AppUser {

  @Id private UUID id;
  private UUID supplierId;
  private String email;
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  private Role role;

  private Instant createdAt;
  private Instant updatedAt;
  @Version private @Nullable Long version;

  protected AppUser() {}

  public AppUser(UUID supplierId, String email, String passwordHash, Role role, Instant now) {
    this.id = Ids.newId();
    this.supplierId = supplierId;
    this.email = EmailAddress.normalize(email);
    this.passwordHash = passwordHash;
    this.role = role;
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

  public UUID supplierId() {
    return supplierId;
  }

  public String email() {
    return email;
  }

  public String passwordHash() {
    return passwordHash;
  }

  public Role role() {
    return role;
  }
}
