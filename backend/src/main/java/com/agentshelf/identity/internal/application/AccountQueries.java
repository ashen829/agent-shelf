package com.agentshelf.identity.internal.application;

import com.agentshelf.identity.CurrentUser;
import com.agentshelf.identity.internal.domain.AppUser;
import com.agentshelf.identity.internal.domain.Supplier;
import com.agentshelf.identity.internal.persistence.AppUserRepository;
import com.agentshelf.identity.internal.persistence.SupplierRepository;
import com.agentshelf.shared.UnauthenticatedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountQueries {

  private final AppUserRepository users;
  private final SupplierRepository suppliers;

  AccountQueries(AppUserRepository users, SupplierRepository suppliers) {
    this.users = users;
    this.suppliers = suppliers;
  }

  /** The current user's account. Scoped by supplier, so it can never return another tenant. */
  @Transactional(readOnly = true)
  public AccountView account(CurrentUser current) {
    AppUser user =
        users
            .findByIdAndSupplierId(current.userId(), current.supplierId())
            .orElseThrow(() -> new UnauthenticatedException("Authentication required."));
    Supplier supplier =
        suppliers
            .findById(user.supplierId())
            .orElseThrow(() -> new UnauthenticatedException("Authentication required."));
    return new AccountView(
        user.id(),
        user.email(),
        user.role(),
        supplier.id(),
        supplier.name(),
        supplier.slug(),
        supplier.currency().value());
  }
}
