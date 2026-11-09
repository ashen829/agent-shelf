package com.agentshelf.catalog.internal.importing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ErrorReportTest {

  @ParameterizedTest
  @ValueSource(strings = {"=HYPERLINK(\"http://x\")", "+1+1", "-2+3", "@SUM(A1)", "\tcmd"})
  void neutralisesCellsASpreadsheetWouldRun(String value) {
    assertThat(ErrorReport.safe(value)).startsWith("'");
  }

  @ParameterizedTest
  @ValueSource(strings = {"-5", "-1,234.50", "PVC-20", "Pipe", ""})
  void leavesNumbersAndNormalTextAlone(String value) {
    assertThat(ErrorReport.safe(value)).isEqualTo(value);
  }

  @Test
  void writesOneLineWithTheOriginalSeparator() {
    assertThat(ErrorReport.toCsvLine(java.util.List.of("A", "b;c", "d\"e"), ';'))
        .isEqualTo("A;\"b;c\";\"d\"\"e\"");
  }
}
