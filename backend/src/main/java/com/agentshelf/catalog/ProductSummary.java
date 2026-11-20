package com.agentshelf.catalog;

import com.agentshelf.shared.Money;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** What other modules see of a product in search results. Never the entity itself. */
public record ProductSummary(
    UUID id, String sku, String name, @Nullable String unit, Money price, boolean inStock) {}
