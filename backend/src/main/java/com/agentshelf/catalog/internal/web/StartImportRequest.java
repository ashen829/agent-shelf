package com.agentshelf.catalog.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

record StartImportRequest(
    @NotNull @Valid ColumnMappingDto mapping,
    @NotNull
        @Pattern(regexp = "[.,]", message = "must be . or ,")
        @Schema(
            allowableValues = {".", ","},
            example = ".")
        String decimalSeparator,
    @Schema(description = "Archive active products whose SKU is not in the file")
        boolean completeCatalog) {}
