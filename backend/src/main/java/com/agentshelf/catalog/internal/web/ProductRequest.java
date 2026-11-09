package com.agentshelf.catalog.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Create or edit a product. The currency is always the supplier's own. */
record ProductRequest(
    @NotBlank
        @Pattern(
            regexp = "[A-Za-z0-9][A-Za-z0-9._/-]{0,63}",
            message = "must be 1 to 64 letters, digits or . _ / - (no spaces)")
        String sku,
    @NotBlank @Size(max = 200) String name,
    @Schema(nullable = true) @Nullable @Size(max = 2000) String description,
    @Schema(nullable = true) @Nullable @Size(max = 100) String category,
    @Schema(nullable = true) @Nullable @Size(max = 30) String unit,
    @NotBlank
        @Pattern(
            regexp = "\\d{1,13}(\\.\\d{1,4})?",
            message = "must be a price like 12.50, with at most 4 decimal places")
        @Schema(type = "string", example = "12.50")
        String price,
    @NotNull @Min(0) @Max(2_000_000_000) Integer stockQty,
    @Schema(
            nullable = true,
            description = "Required when editing: the version you loaded, to detect lost updates")
        @Nullable Long version) {}
