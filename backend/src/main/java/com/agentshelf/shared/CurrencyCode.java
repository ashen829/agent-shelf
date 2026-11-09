package com.agentshelf.shared;

import java.util.Currency;
import java.util.regex.Pattern;

/** An ISO 4217 currency code such as {@code EUR} or {@code LKR}. */
public record CurrencyCode(String value) {

  private static final Pattern THREE_UPPER_CASE_LETTERS = Pattern.compile("[A-Z]{3}");

  public CurrencyCode {
    if (!isValid(value)) {
      throw new IllegalArgumentException("Not an ISO 4217 currency code: " + value);
    }
  }

  public static boolean isValid(String value) {
    if (!THREE_UPPER_CASE_LETTERS.matcher(value).matches()) {
      return false;
    }
    try {
      Currency.getInstance(value);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
