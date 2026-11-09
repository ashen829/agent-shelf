package com.agentshelf.identity.internal.security;

import com.agentshelf.identity.Role;
import java.util.Collection;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Used only while checking a password; never stored in the session. */
final class UserAccount implements UserDetails, CredentialsContainer {

  private final UUID userId;
  private final UUID supplierId;
  private final String email;
  private final Role role;
  private @Nullable String passwordHash;

  UserAccount(UUID userId, UUID supplierId, String email, Role role, String passwordHash) {
    this.userId = userId;
    this.supplierId = supplierId;
    this.email = email;
    this.role = role;
    this.passwordHash = passwordHash;
  }

  SessionUser toSessionUser() {
    return new SessionUser(userId, supplierId, email, role);
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return toSessionUser().authorities();
  }

  @Override
  public @Nullable String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public void eraseCredentials() {
    passwordHash = null;
  }
}
