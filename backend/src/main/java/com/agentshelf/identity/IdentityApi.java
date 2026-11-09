package com.agentshelf.identity;

import com.agentshelf.identity.internal.security.SessionUser;
import com.agentshelf.shared.UnauthenticatedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Public API of the identity module: the only class other modules call. */
@Component
public class IdentityApi {

  /**
   * The user making the current request.
   *
   * @throws UnauthenticatedException if nobody is signed in
   */
  public CurrentUser currentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof SessionUser user) {
      return new CurrentUser(user.userId(), user.supplierId(), user.role());
    }
    throw new UnauthenticatedException("Authentication required.");
  }
}
