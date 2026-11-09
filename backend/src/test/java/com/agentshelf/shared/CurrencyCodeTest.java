package com.agentshelf.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CurrencyCodeTest {

  @ParameterizedTest
  @ValueSource(strings = {"EUR", "USD", "LKR", "GBP"})
  void acceptsIsoCodes(String code) {
    assertThat(CurrencyCode.isValid(code)).isTrue();
    assertThat(new CurrencyCode(code).value()).isEqualTo(code);
  }

  @ParameterizedTest
  @ValueSource(strings = {"eur", "EU", "EURO", "QQQ", "", "E1R"})
  void rejectsAnythingElse(String code) {
    assertThat(CurrencyCode.isValid(code)).isFalse();
    assertThatThrownBy(() -> new CurrencyCode(code)).isInstanceOf(IllegalArgumentException.class);
  }
}
