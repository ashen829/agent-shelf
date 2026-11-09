package com.agentshelf.identity;

import java.util.UUID;

/**
 * The signed-in user. {@code supplierId} is the tenant: every supplier-owned query must be scoped
 * by it.
 */
public record CurrentUser(UUID userId, UUID supplierId, Role role) {}
