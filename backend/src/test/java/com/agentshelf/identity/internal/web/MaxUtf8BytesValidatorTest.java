package com.agentshelf.identity.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaxUtf8BytesValidatorTest {

  @Test
  void countsBytesNotCharacters() {
    assertThat(MaxUtf8BytesValidator.fits("a".repeat(72), 72)).isTrue();
    assertThat(MaxUtf8BytesValidator.fits("a".repeat(73), 72)).isFalse();
    // Sinhala letters are 3 bytes each: 24 = 72 bytes, 25 = 75 bytes.
    assertThat(MaxUtf8BytesValidator.fits("ශ".repeat(24), 72)).isTrue();
    assertThat(MaxUtf8BytesValidator.fits("ශ".repeat(25), 72)).isFalse();
  }

  @Test
  void leavesNullToNotBlank() {
    assertThat(MaxUtf8BytesValidator.fits(null, 72)).isTrue();
  }
}
