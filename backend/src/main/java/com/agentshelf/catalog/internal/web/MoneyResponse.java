package com.agentshelf.catalog.internal.web;

import com.agentshelf.shared.Money;
import io.swagger.v3.oas.annotations.media.Schema;

/** Money as text, so no client loses precision to floating point. */
record MoneyResponse(
    @Schema(type = "string", example = "12.5000", description = "Decimal, 4 places") String amount,
    @Schema(example = "USD") String currency) {

  static MoneyResponse from(Money money) {
    return new MoneyResponse(money.toPlainString(), money.currency().value());
  }
}
