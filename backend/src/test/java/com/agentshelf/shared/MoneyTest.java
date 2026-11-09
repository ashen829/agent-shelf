package com.agentshelf.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

  private static final CurrencyCode USD = new CurrencyCode("USD");

  @Test
  void keepsFractionsOfACentAsPlainText() {
    assertThat(new Money(new BigDecimal("0.0125"), USD).toPlainString()).isEqualTo("0.0125");
    assertThat(new Money(new BigDecimal("12.5"), USD).toPlainString()).isEqualTo("12.5000");
    assertThat(new Money(new BigDecimal("1E+3"), USD).toPlainString()).isEqualTo("1000.0000");
  }

  @Test
  void equalAmountsAreEqualWhateverTheirScale() {
    assertThat(new Money(new BigDecimal("12.5"), USD))
        .isEqualTo(new Money(new BigDecimal("12.500"), USD));
  }

  @ParameterizedTest
  @ValueSource(strings = {"-0.01", "0.00001", "1.23456"})
  void rejectsNegativeOrTooPrecise(String amount) {
    assertThatThrownBy(() -> new Money(new BigDecimal(amount), USD))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
