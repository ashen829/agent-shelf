package com.agentshelf.catalog;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A product's descriptive fields at one version, for other modules that suggest changes (AI
 * cleanup). Prices and stock are left out on purpose: those are never suggested.
 */
public record ProductSnapshot(
    UUID id,
    long version,
    String sku,
    String name,
    @Nullable String description,
    @Nullable String category,
    @Nullable String unit) {}
