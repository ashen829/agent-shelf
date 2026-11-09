package com.agentshelf.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An amount of money in one currency. Up to four decimal places, because wholesale unit prices are
 * often fractions of a cent (for example 0.0125 per screw). Never negative.
 */
public record Money(BigDecimal amount, CurrencyCode currency) {

  public static final int SCALE = 4;

  public Money {
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("Money can't be negative");
    }
    if (amount.stripTrailingZeros().scale() > SCALE) {
      throw new IllegalArgumentException("Money has at most " + SCALE + " decimal places");
    }
    amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
  }

  /** Plain decimal text with four places, for example {@code "12.5000"}: no exponent, no loss. */
  public String toPlainString() {
    return amount.toPlainString();
  }
}
