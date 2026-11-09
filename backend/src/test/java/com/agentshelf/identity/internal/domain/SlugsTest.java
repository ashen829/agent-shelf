package com.agentshelf.identity.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlugsTest {

  @ParameterizedTest
  @CsvSource({
    "Acme Hardware Ltd.,  acme-hardware-ltd",
    "'  PVC & Pipes  ',   pvc-pipes",
    "Café Ñandú,          cafe-nandu",
    "!!!,                 supplier",
    "ශ්‍රී ලංකා,           supplier",
  })
  void makesUrlSafeSlugs(String name, String expected) {
    assertThat(Slugs.fromName(name)).isEqualTo(expected);
  }

  @Test
  void limitsLengthWithoutTrailingHyphen() {
    String slug = Slugs.fromName("a".repeat(49) + " b" + "c".repeat(30));

    assertThat(slug).hasSizeLessThanOrEqualTo(Slugs.MAX_BASE_LENGTH).doesNotEndWith("-");
  }

  @Test
  void suffixIsSixLowerCaseCharacters() {
    assertThat(Slugs.withRandomSuffix("acme")).matches("acme-[a-z0-9]{6}");
  }
}
