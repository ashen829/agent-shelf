package com.agentshelf.catalog.internal.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class XlsxReaderTest {

  private static List<CsvReader.Row> rows(byte[] xlsx) {
    List<CsvReader.Row> rows = new ArrayList<>();
    CsvReader.forEachRow(XlsxReader.toCsv(xlsx), ',', rows::add);
    return rows;
  }

  @Test
  void readsTextNumbersBooleansAndSavedFormulaResults() {
    byte[] xlsx =
        TestXlsx.workbook(
            TestXlsx.row(1, List.of("SKU", "Name", "Price", "Stock", "Active"))
                + TestXlsx.row(2, List.of("A-1", "Pipe, 20 mm \"PVC\"", 12.5, 40, true))
                + "<row r=\"3\">"
                + TestXlsx.cell("A3", "B-2")
                + TestXlsx.cell("B3", "Tape\nwide")
                + "<c r=\"C3\"><v>19.989999999999998</v></c>"
                + "<c r=\"D3\"><f>D2*2</f><v>80</v></c>"
                + "<c r=\"E3\" t=\"str\"><f>\"yes\"</f><v>yes</v></c>"
                + "</row>");

    CsvReader.Summary summary = CsvReader.inspectConverted(XlsxReader.toCsv(xlsx));
    List<CsvReader.Row> rows = rows(xlsx);

    assertThat(summary.delimiter()).isEqualTo(',');
    assertThat(summary.headers()).containsExactly("SKU", "Name", "Price", "Stock", "Active");
    assertThat(rows.get(0).values())
        .containsExactly("A-1", "Pipe, 20 mm \"PVC\"", "12.5", "40", "TRUE");
    assertThat(rows.get(1).values()).containsExactly("B-2", "Tape\nwide", "19.99", "80", "yes");
  }

  @Test
  void keepsExcelRowNumbersAcrossBlankRowsAndRowsBeforeTheHeader() {
    byte[] xlsx =
        TestXlsx.workbook(
            TestXlsx.row(2, List.of("sku", "name"))
                + TestXlsx.row(3, List.of("A", "Alpha"))
                + "<row r=\"4\"><c r=\"A4\" s=\"1\"/></row>"
                + TestXlsx.row(6, List.of("B", "Beta")));

    assertThat(rows(xlsx).stream().map(CsvReader.Row::rowNumber)).containsExactly(3L, 6L);
    assertThat(CsvReader.inspectConverted(XlsxReader.toCsv(xlsx)).rowCount()).isEqualTo(2);
  }

  @Test
  void readsOnlyTheFirstSheetAndDropsTrailingEmptyCells() {
    byte[] xlsx =
        TestXlsx.workbook(
            TestXlsx.row(1, List.of("sku", "name", "", ""))
                + TestXlsx.row(2, List.of("A", "", "", "")),
            TestXlsx.row(1, List.of("other", "sheet")));

    CsvReader.Summary summary = CsvReader.inspectConverted(XlsxReader.toCsv(xlsx));

    assertThat(summary.headers()).containsExactly("sku", "name");
    assertThat(rows(xlsx).getFirst().values()).containsExactly("A");
  }

  @Test
  void rejectsSheetsWithTooManyColumns() {
    String far = TestXlsx.column(XlsxReader.MAX_COLUMNS);
    byte[] xlsx =
        TestXlsx.workbook(
            TestXlsx.row(1, List.of("sku"))
                + "<row r=\"2\">"
                + TestXlsx.cell(far + "2", "x")
                + "</row>");

    assertThatThrownBy(() -> XlsxReader.toCsv(xlsx))
        .isInstanceOf(InvalidImportFileException.class)
        .hasMessageContaining("more than 200 columns");
  }

  @Test
  void stopsReadingSoonAfterTheRowLimit() {
    StringBuilder data = new StringBuilder(TestXlsx.row(1, List.of("sku")));
    for (int r = 2; r <= CsvReader.MAX_ROWS + 50; r++) {
      data.append(TestXlsx.row(r, List.of("S" + r)));
    }
    byte[] csv = XlsxReader.toCsv(TestXlsx.workbook(data.toString()));

    assertThat(new String(csv, StandardCharsets.UTF_8).lines().count())
        .isEqualTo(CsvReader.MAX_ROWS + 2L);
    assertThatThrownBy(() -> CsvReader.inspectConverted(csv))
        .isInstanceOf(InvalidImportFileException.class)
        .hasMessageContaining("more than 20,000 product rows");
  }

  @Test
  void rejectsZipBombsBeforeParsing() {
    byte[] bomb =
        TestXlsx.zip(
            Map.of("xl/worksheets/sheet1.xml", new byte[(int) XlsxReader.MAX_UNPACKED_BYTES + 1]));

    assertThat(bomb.length).isLessThan(1024 * 1024);
    assertThatThrownBy(() -> XlsxReader.toCsv(bomb))
        .isInstanceOf(InvalidImportFileException.class)
        .hasMessageContaining("more than 100 MB");
  }

  @Test
  void neverLoadsExternalEntities() {
    String sheet =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE worksheet [<!ENTITY secret SYSTEM "file:///etc/hosts">]>
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
        <row r="1"><c r="A1" t="inlineStr"><is><t>sku</t></is></c></row>
        <row r="2"><c r="A2" t="inlineStr"><is><t>&secret;</t></is></c></row>
        </sheetData></worksheet>
        """;
    byte[] valid = TestXlsx.workbook("");
    Map<String, byte[]> parts = new LinkedHashMap<>(unzip(valid));
    parts.put("xl/worksheets/sheet1.xml", sheet.getBytes(StandardCharsets.UTF_8));
    byte[] xlsx = TestXlsx.zip(parts);

    String result;
    try {
      result = new String(XlsxReader.toCsv(xlsx), StandardCharsets.UTF_8);
    } catch (InvalidImportFileException e) {
      result = e.getMessage();
    }
    assertThat(result).doesNotContain("localhost");
  }

  @Test
  void rejectsDamagedFilesWithAClearMessage() {
    byte[] damaged = {0x50, 0x4b, 0x03, 0x04, 1, 2, 3, 4, 5};
    byte[] notAWorkbook = TestXlsx.zip(Map.of("hello.txt", new byte[] {1}));

    for (byte[] file : List.of(damaged, notAWorkbook)) {
      assertThatThrownBy(() -> XlsxReader.toCsv(file))
          .isInstanceOf(InvalidImportFileException.class)
          .hasMessage(XlsxReader.UNREADABLE);
    }
  }

  @Test
  void detectsFileTypesFromTheirFirstBytes() {
    byte[] ole2 = {
      (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0
    };

    assertThat(XlsxReader.isXlsx(TestXlsx.workbook(""))).isTrue();
    assertThat(XlsxReader.isXlsx("sku,name".getBytes(StandardCharsets.UTF_8))).isFalse();
    assertThat(XlsxReader.isXlsx(new byte[] {0x50})).isFalse();
    assertThat(XlsxReader.isOldOrProtectedExcel(ole2)).isTrue();
    assertThat(XlsxReader.isOldOrProtectedExcel(TestXlsx.workbook(""))).isFalse();
  }

  @Test
  void writesNumbersTheWayExcelShowsThem() {
    assertThat(XlsxReader.number(new BigDecimal("19.989999999999998"))).isEqualTo("19.99");
    assertThat(XlsxReader.number(new BigDecimal("1E+3"))).isEqualTo("1000");
    assertThat(XlsxReader.number(new BigDecimal("-1.50"))).isEqualTo("-1.5");
    assertThat(XlsxReader.number(new BigDecimal("0.000"))).isEqualTo("0");
    assertThat(XlsxReader.number(new BigDecimal("0.0001"))).isEqualTo("0.0001");
  }

  private static Map<String, byte[]> unzip(byte[] zip) {
    Map<String, byte[]> parts = new LinkedHashMap<>();
    try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
      for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
        parts.put(entry.getName(), in.readAllBytes());
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return parts;
  }
}
