package com.agentshelf.identity.internal.security;

import com.agentshelf.identity.Role;
import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The principal stored in the session (in PostgreSQL). No password and no secrets. The session
 * row's principal name is the user ID.
 */
public record SessionUser(UUID userId, UUID supplierId, String email, Role role)
    implements Principal, Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Override
  public String getName() {
    return userId.toString();
  }

  public List<GrantedAuthority> authorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }
}
