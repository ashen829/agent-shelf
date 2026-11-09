package com.agentshelf.catalog.internal.importing;

import com.agentshelf.catalog.internal.domain.ImportError;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;

/**
 * The rows that failed, as a CSV file in the supplier's own layout plus an "Error" column, ready to
 * fix and upload again. Cells that a spreadsheet would run as a formula are made safe.
 */
public final class ErrorReport {

  private static final Pattern NUMBER = Pattern.compile("-?[\\d.,\\s]+");

  private ErrorReport() {}

  public static byte[] build(List<String> headers, char delimiter, List<ImportError> errors) {
    CSVFormat format = CSVFormat.RFC4180.builder().setDelimiter(delimiter).get();
    StringWriter out = new StringWriter();
    // Byte order mark: Excel then opens the file as UTF-8.
    out.write('﻿');
    try (CSVPrinter printer = new CSVPrinter(out, format)) {
      List<String> headerRow = new ArrayList<>(headers);
      headerRow.add("Error");
      printer.printRecord(headerRow.stream().map(ErrorReport::safe).toList());
      for (ImportError error : errors) {
        List<String> values = new ArrayList<>(parseLine(error.rawRecord(), delimiter));
        while (values.size() < headers.size()) {
          values.add("");
        }
        values.add("Row " + error.rowNumber() + ": " + error.message());
        printer.printRecord(values.stream().map(ErrorReport::safe).toList());
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  /** One row as CSV text, to store with its error. */
  static String toCsvLine(List<String> values, char delimiter) {
    StringWriter out = new StringWriter();
    try (CSVPrinter printer =
        new CSVPrinter(
            out,
            CSVFormat.RFC4180.builder().setDelimiter(delimiter).setRecordSeparator("").get())) {
      printer.printRecord(values);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toString();
  }

  /**
   * Prevents CSV injection: a cell starting with = + - @ (or tab/carriage return) would run as a
   * formula in Excel. Plain numbers like -5 are left alone.
   */
  static String safe(String value) {
    if (value.isEmpty() || NUMBER.matcher(value).matches()) {
      return value;
    }
    char first = value.charAt(0);
    return first == '='
            || first == '+'
            || first == '-'
            || first == '@'
            || first == '\t'
            || first == '\r'
        ? "'" + value
        : value;
  }

  private static List<String> parseLine(String line, char delimiter) {
    try (CSVParser parser =
        CSVParser.parse(
            new StringReader(line), CSVFormat.RFC4180.builder().setDelimiter(delimiter).get())) {
      return parser.getRecords().stream().findFirst().map(r -> r.toList()).orElse(List.of());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
