package com.agentshelf.identity;

import com.agentshelf.identity.internal.persistence.SupplierRepository;
import com.agentshelf.identity.internal.security.SessionUser;
import com.agentshelf.shared.CurrencyCode;
import com.agentshelf.shared.UnauthenticatedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Public API of the identity module: the only class other modules call. */
@Component
public class IdentityApi {

  private final SupplierRepository suppliers;

  IdentityApi(SupplierRepository suppliers) {
    this.suppliers = suppliers;
  }

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

  /** The currency the current user's supplier sells in. Prices are always in this currency. */
  @Transactional(readOnly = true)
  public CurrencyCode currentSupplierCurrency() {
    return suppliers
        .findById(currentUser().supplierId())
        .orElseThrow(() -> new UnauthenticatedException("Authentication required."))
        .currency();
  }
}
