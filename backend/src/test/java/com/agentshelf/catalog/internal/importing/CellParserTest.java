package com.agentshelf.catalog.internal.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentshelf.catalog.internal.domain.ProductRuleViolation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CellParserTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "12.50|.|12.50",
        "1,234.5|.|1234.5",
        "0.0125|.|0.0125",
        "' 7 '|.|7",
        "12,50|,|12.50",
        "1.234,5|,|1234.5",
        "1 234,5|,|1234.5",
      })
  void readsPricesWithTheChosenDecimalSeparator(String raw, char separator, String expected) {
    assertThat(CellParser.price(raw, separator)).isEqualByComparingTo(new BigDecimal(expected));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "abc", "-5", "12.5O", "$12", "1.23456"})
  void rejectsBadPricesOnThePriceField(String raw) {
    assertThatThrownBy(() -> CellParser.price(raw, '.'))
        .isInstanceOf(ProductRuleViolation.class)
        .satisfies(e -> assertThat(((ProductRuleViolation) e).field()).isEqualTo("price"));
  }

  @Test
  void readsStockAndTreatsAnEmptyCellAsZero() {
    assertThat(CellParser.stock("1,200", '.')).isEqualTo(1200);
    assertThat(CellParser.stock("1.200", ',')).isEqualTo(1200);
    assertThat(CellParser.stock("  ", '.')).isZero();
    assertThatThrownBy(() -> CellParser.stock("2.5", '.')).isInstanceOf(ProductRuleViolation.class);
    assertThatThrownBy(() -> CellParser.stock("-3", '.')).isInstanceOf(ProductRuleViolation.class);
  }

  @Test
  void quotesAtMostFortyCharactersOfUntrustedText() {
    assertThat(CellParser.shorten("x".repeat(100))).hasSize(41).endsWith("…");
  }
}
