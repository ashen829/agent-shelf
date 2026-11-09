package com.agentshelf.catalog.internal.importing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ImportServiceTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "prices.csv|prices.csv",
        "C:\\Users\\me\\prices.csv|prices.csv",
        "../../etc/passwd|passwd",
        "'  '|upload.csv",
      })
  void keepsOnlyAPlainFileName(String input, String expected) {
    assertThat(ImportService.cleanFileName(input)).isEqualTo(expected);
  }
}
