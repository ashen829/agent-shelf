package com.agentshelf.enrichment.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** What the catalog page needs: is AI cleanup available, and the latest job if there is one. */
record EnrichmentOverview(
    boolean enabled, @Schema(nullable = true) @Nullable EnrichmentJobResponse latestJob) {}
