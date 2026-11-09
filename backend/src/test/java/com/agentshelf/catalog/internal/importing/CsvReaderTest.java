package com.agentshelf.catalog.internal.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvReaderTest {

  private static byte[] utf8(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void detectsCommaSemicolonAndTab() {
    assertThat(CsvReader.inspect(utf8("sku,name\nA,B\n")).delimiter()).isEqualTo(',');
    assertThat(CsvReader.inspect(utf8("sku;name;price\nA;B;1,5\n")).delimiter()).isEqualTo(';');
    assertThat(CsvReader.inspect(utf8("sku\tname\nA\tB\n")).delimiter()).isEqualTo('\t');
  }

  @Test
  void ignoresSeparatorsInsideQuotesWhenDetecting() {
    assertThat(CsvReader.inspect(utf8("\"a,b,c\";name\nx;y\n")).delimiter()).isEqualTo(';');
  }

  @Test
  void readsHeadersRowsQuotesAndLineBreaksInsideQuotes() {
    byte[] csv = utf8("SKU,Name,Notes\nA-1,\"Pipe, 20 mm\",\"two\nlines\"\n\n,,\nB-2,Tape,\n");

    CsvReader.Summary summary = CsvReader.inspect(csv);
    List<CsvReader.Row> rows = new ArrayList<>();
    CsvReader.forEachRow(csv, ',', rows::add);

    assertThat(summary.headers()).containsExactly("SKU", "Name", "Notes");
    assertThat(summary.rowCount()).isEqualTo(2);
    assertThat(rows.get(0).values()).containsExactly("A-1", "Pipe, 20 mm", "two\nlines");
    assertThat(rows.get(0).rowNumber()).isEqualTo(2);
  }

  @Test
  void stripsTheUtf8ByteOrderMarkAndNamesBlankHeaders() {
    byte[] csv = utf8("\uFEFFsku,,price\nA,x,1\n");

    assertThat(CsvReader.inspect(csv).headers()).containsExactly("sku", "Column 2", "price");
  }

  @Test
  void previewsOnlyTheFirstRows() {
    StringBuilder csv = new StringBuilder("sku\n");
    for (int i = 0; i < 50; i++) {
      csv.append("S").append(i).append('\n');
    }
    assertThat(CsvReader.preview(utf8(csv.toString()), ',', 5)).hasSize(5);
  }

  @Test
  void rejectsFilesThatAreNotUtf8() {
    byte[] latin1 = "sku,name\nA,Café\n".getBytes(StandardCharsets.ISO_8859_1);

    assertThatThrownBy(() -> CsvReader.inspect(latin1))
        .isInstanceOf(InvalidCsvException.class)
        .hasMessageContaining("CSV UTF-8");
  }

  @Test
  void rejectsEmptyFilesAndHeaderOnlyFiles() {
    assertThatThrownBy(() -> CsvReader.inspect(new byte[0]))
        .isInstanceOf(InvalidCsvException.class);
    assertThatThrownBy(() -> CsvReader.inspect(utf8("sku,name\n")))
        .isInstanceOf(InvalidCsvException.class)
        .hasMessageContaining("header row");
  }

  @Test
  void rejectsMoreThanTheRowLimit() {
    StringBuilder csv = new StringBuilder("sku\n");
    for (int i = 0; i <= CsvReader.MAX_ROWS; i++) {
      csv.append('S').append(i).append('\n');
    }
    assertThatThrownBy(() -> CsvReader.inspect(utf8(csv.toString())))
        .isInstanceOf(InvalidCsvException.class)
        .hasMessageContaining("20,000");
  }

  @Test
  void rejectsAnUnclosedQuote() {
    assertThatThrownBy(() -> CsvReader.inspect(utf8("sku,name\nA,\"broken\n")))
        .isInstanceOf(InvalidCsvException.class)
        .hasMessageContaining("quote");
  }
}
