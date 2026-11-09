package com.agentshelf.catalog.internal.importing;

import com.agentshelf.catalog.internal.domain.ProductRuleViolation;
import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Turns price and stock cells into numbers, with the decimal separator the supplier chose. */
public final class CellParser {

  private static final Pattern DECIMAL = Pattern.compile("\\d{1,13}(\\.\\d+)?");
  private static final Pattern WHOLE = Pattern.compile("\\d{1,10}");
  private static final Pattern SPACES = Pattern.compile("[\\s\\u00A0\\u202F]");

  private CellParser() {}

  /** "1,234.50" with '.' or "1.234,50" with ','. At most 4 decimals, never negative. */
  public static BigDecimal price(String raw, char decimalSeparator) {
    String value = normalize(raw, decimalSeparator);
    if (value.isEmpty()) {
      throw new ProductRuleViolation("price", "Price is required.");
    }
    if (!DECIMAL.matcher(value).matches()) {
      throw new ProductRuleViolation(
          "price",
          "Price \"" + shorten(raw) + "\" is not a number like " + example(decimalSeparator) + ".");
    }
    BigDecimal price = new BigDecimal(value);
    if (price.stripTrailingZeros().scale() > 4) {
      throw new ProductRuleViolation("price", "Price can have at most 4 decimal places.");
    }
    return price;
  }

  /** A whole number of items. An empty cell means 0. */
  public static int stock(String raw, char decimalSeparator) {
    String value = normalize(raw, decimalSeparator);
    if (value.isEmpty()) {
      return 0;
    }
    if (!WHOLE.matcher(value).matches() || Long.parseLong(value) > 2_000_000_000L) {
      throw new ProductRuleViolation(
          "stockQty", "Stock \"" + shorten(raw) + "\" is not a whole number of 0 or more.");
    }
    return Integer.parseInt(value);
  }

  private static String normalize(String raw, char decimalSeparator) {
    String value = SPACES.matcher(raw).replaceAll("");
    char thousands = decimalSeparator == ',' ? '.' : ',';
    value = value.replace(String.valueOf(thousands), "");
    return decimalSeparator == ',' ? value.replace(',', '.') : value;
  }

  private static String example(char decimalSeparator) {
    return decimalSeparator == ',' ? "12,50" : "12.50";
  }

  /** Cell text is untrusted: quote at most 40 characters of it back. */
  static String shorten(String raw) {
    String value = raw.strip();
    return value.length() <= 40 ? value : value.substring(0, 40) + "…";
  }
}
