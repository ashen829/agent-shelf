package com.agentshelf.enrichment.internal.web;

import com.agentshelf.enrichment.internal.domain.ProductSuggestion;
import com.agentshelf.enrichment.internal.domain.SuggestionField;
import com.agentshelf.enrichment.internal.domain.SuggestionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One suggested change to one product field. */
record SuggestionResponse(
    UUID id,
    UUID productId,
    long productVersion,
    SuggestionField field,
    @Schema(nullable = true) @Nullable String oldValue,
    String newValue,
    String reason,
    @Schema(description = "0 to 1, how sure the model is", example = "0.85") BigDecimal confidence,
    SuggestionStatus status) {

  static SuggestionResponse from(ProductSuggestion s) {
    return new SuggestionResponse(
        s.id(),
        s.productId(),
        s.productVersion(),
        s.field(),
        s.oldValue(),
        s.newValue(),
        s.reason(),
        s.confidence(),
        s.status());
  }
}
