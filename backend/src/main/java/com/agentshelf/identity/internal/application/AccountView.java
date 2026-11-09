package com.agentshelf.identity.internal.application;

import com.agentshelf.identity.Role;
import java.util.UUID;

/** The signed-in user and their supplier, as shown in the dashboard. */
public record AccountView(
    UUID userId,
    String email,
    Role role,
    UUID supplierId,
    String supplierName,
    String supplierSlug,
    String currency) {}
