package com.agentshelf.identity.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailAddressTest {

  @Test
  void trimsAndLowerCases() {
    assertThat(EmailAddress.normalize("  Owner@Example.COM ")).isEqualTo("owner@example.com");
  }
}
