package com.agentshelf.catalog.internal.importing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds small .xlsx files for tests, written by hand so tests control every byte (including
 * hostile ones). Only the parts a reader needs: content types, workbook, its relations and sheets.
 */
public final class TestXlsx {

  private TestXlsx() {}

  /** One sheet with these rows from row 1: strings, numbers (as text) and booleans. */
  public static byte[] of(List<List<Object>> rows) {
    StringBuilder data = new StringBuilder();
    for (int r = 0; r < rows.size(); r++) {
      data.append(row(r + 1, rows.get(r)));
    }
    return workbook(data.toString());
  }

  /** One row of cells starting at column A. */
  public static String row(int rowNumber, List<Object> values) {
    StringBuilder row = new StringBuilder("<row r=\"" + rowNumber + "\">");
    for (int c = 0; c < values.size(); c++) {
      row.append(cell(column(c) + rowNumber, values.get(c)));
    }
    return row.append("</row>").toString();
  }

  public static String cell(String ref, Object value) {
    return switch (value) {
      case Boolean b -> "<c r=\"" + ref + "\" t=\"b\"><v>" + (b ? 1 : 0) + "</v></c>";
      case Number n -> "<c r=\"" + ref + "\"><v>" + n + "</v></c>";
      default ->
          "<c r=\""
              + ref
              + "\" t=\"inlineStr\"><is><t>"
              + escape(value.toString())
              + "</t></is></c>";
    };
  }

  /** A workbook whose sheets have this raw {@code <sheetData>} content, first sheet first. */
  public static byte[] workbook(String... sheetData) {
    Map<String, byte[]> parts = new LinkedHashMap<>();
    StringBuilder sheets = new StringBuilder();
    StringBuilder rels = new StringBuilder();
    for (int i = 1; i <= sheetData.length; i++) {
      sheets.append("<sheet name=\"Sheet%d\" sheetId=\"%d\" r:id=\"rId%d\"/>".formatted(i, i, i));
      rels.append(
          "<Relationship Id=\"rId%d\" Target=\"worksheets/sheet%d.xml\" Type=\"%s\"/>"
              .formatted(i, i, OFFICE + "/relationships/worksheet"));
      parts.put(
          "xl/worksheets/sheet" + i + ".xml",
          utf8(
              "<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"%s\"><sheetData>%s</sheetData></worksheet>"
                  .formatted(MAIN, sheetData[i - 1])));
    }
    parts.put(
        "[Content_Types].xml",
        utf8(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/"
                + "vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/></Types>"));
    parts.put(
        "xl/workbook.xml",
        utf8(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"%s\" xmlns:r=\"%s\"><sheets>%s</sheets></workbook>"
                .formatted(MAIN, OFFICE + "/relationships", sheets)));
    parts.put(
        "xl/_rels/workbook.xml.rels",
        utf8(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">%s</Relationships>"
                .formatted(rels)));
    return zip(parts);
  }

  public static byte[] zip(Map<String, byte[]> entries) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue());
        zip.closeEntry();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  /** Column letters: 0 = A, 25 = Z, 26 = AA. */
  public static String column(int index) {
    StringBuilder name = new StringBuilder();
    for (int i = index + 1; i > 0; i = (i - 1) / 26) {
      name.insert(0, (char) ('A' + (i - 1) % 26));
    }
    return name.toString();
  }

  private static final String MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
  private static final String OFFICE = "http://schemas.openxmlformats.org/officeDocument/2006";

  private static byte[] utf8(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static String escape(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
