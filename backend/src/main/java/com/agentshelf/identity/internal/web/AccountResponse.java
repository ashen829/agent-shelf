package com.agentshelf.identity.internal.web;

import com.agentshelf.identity.Role;
import com.agentshelf.identity.internal.application.AccountView;
import java.util.UUID;

/** The signed-in user and their supplier. */
record AccountResponse(UUID userId, String email, Role role, SupplierResponse supplier) {

  record SupplierResponse(UUID id, String name, String slug, String currency) {}

  static AccountResponse from(AccountView view) {
    return new AccountResponse(
        view.userId(),
        view.email(),
        view.role(),
        new SupplierResponse(
            view.supplierId(), view.supplierName(), view.supplierSlug(), view.currency()));
  }
}
