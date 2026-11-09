package com.agentshelf.catalog.internal.importing;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Reads supplier price lists: UTF-8 (with or without a byte order mark), comma, semicolon or tab
 * separated, first row = column headers. File content is untrusted data and is never interpreted.
 */
public final class CsvReader {

  public static final int MAX_BYTES = 10 * 1024 * 1024;
  public static final int MAX_ROWS = 20_000;
  private static final char[] DELIMITERS = {',', ';', '\t'};

  private CsvReader() {}

  /** What the upload screen needs: the separator, the headers and how many product rows. */
  public record Summary(char delimiter, List<String> headers, int rowCount) {}

  /** One product row. {@code rowNumber} is the row in the file, counting the header as row 1. */
  public record Row(long rowNumber, List<String> values) {}

  /** Checks a whole upload before anything is stored. */
  public static Summary inspect(byte[] content) {
    if (content.length == 0) {
      throw new InvalidCsvException("The file is empty.");
    }
    if (content.length > MAX_BYTES) {
      throw new InvalidCsvException("The file is larger than 10 MB. Split it into smaller files.");
    }
    String text = decode(content);
    char delimiter = detectDelimiter(text);
    List<String> headers = new ArrayList<>();
    int[] rows = {0};
    parse(
        text,
        delimiter,
        (row, isHeader) -> {
          if (isHeader) {
            headers.addAll(namedHeaders(row.values()));
          } else if (++rows[0] > MAX_ROWS) {
            throw new InvalidCsvException(
                "The file has more than 20,000 product rows. Split it into smaller files.");
          }
          return true;
        });
    if (headers.isEmpty() || rows[0] == 0) {
      throw new InvalidCsvException(
          "The file needs a header row with column names and at least one product row under it.");
    }
    return new Summary(delimiter, List.copyOf(headers), rows[0]);
  }

  /** The first {@code limit} product rows, for the column-matching screen. */
  public static List<Row> preview(byte[] content, char delimiter, int limit) {
    List<Row> preview = new ArrayList<>();
    parse(
        decode(content),
        delimiter,
        (row, isHeader) -> {
          if (!isHeader) {
            preview.add(row);
          }
          return preview.size() < limit;
        });
    return preview;
  }

  /** The column headers of a stored file. */
  public static List<String> headers(byte[] content, char delimiter) {
    List<String> headers = new ArrayList<>();
    parse(
        decode(content),
        delimiter,
        (row, isHeader) -> {
          headers.addAll(namedHeaders(row.values()));
          return false;
        });
    return headers;
  }

  /** Every product row, in order. */
  public static void forEachRow(byte[] content, char delimiter, RowHandler handler) {
    parse(
        decode(content),
        delimiter,
        (row, isHeader) -> {
          if (!isHeader) {
            handler.accept(row);
          }
          return true;
        });
  }

  /** Receives rows one by one, so a large file is never held twice in memory as objects. */
  @FunctionalInterface
  public interface RowHandler {
    void accept(Row row);
  }

  @FunctionalInterface
  private interface Visitor {
    /** Returns false to stop reading. */
    boolean visit(Row row, boolean isHeader);
  }

  private static void parse(String text, char delimiter, Visitor visitor) {
    CSVFormat format =
        CSVFormat.RFC4180.builder().setDelimiter(delimiter).setIgnoreEmptyLines(true).get();
    try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
      boolean header = true;
      for (CSVRecord record : parser) {
        List<String> values = record.toList();
        if (values.stream().allMatch(String::isBlank)) {
          continue;
        }
        if (!visitor.visit(new Row(record.getRecordNumber(), values), header)) {
          return;
        }
        header = false;
      }
    } catch (IOException | UncheckedIOException | IllegalStateException e) {
      throw new InvalidCsvException(
          "The file isn't valid CSV. Check for a quote (\") that is opened but never closed.");
    }
  }

  private static String decode(byte[] content) {
    try {
      String text =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(content))
              .toString();
      return text.startsWith("﻿") ? text.substring(1) : text;
    } catch (CharacterCodingException e) {
      throw new InvalidCsvException(
          "The file isn't saved as UTF-8. In Excel, use Save As and choose \"CSV UTF-8\".");
    }
  }

  /** The separator used most often in the header line, ignoring text inside quotes. */
  static char detectDelimiter(String text) {
    int end = text.indexOf('\n');
    String firstLine = end < 0 ? text : text.substring(0, end);
    int[] counts = new int[DELIMITERS.length];
    boolean quoted = false;
    for (char c : firstLine.toCharArray()) {
      if (c == '"') {
        quoted = !quoted;
      }
      for (int i = 0; i < DELIMITERS.length && !quoted; i++) {
        if (c == DELIMITERS[i]) {
          counts[i]++;
        }
      }
    }
    int best = 0;
    for (int i = 1; i < DELIMITERS.length; i++) {
      if (counts[i] > counts[best]) {
        best = i;
      }
    }
    return DELIMITERS[best];
  }

  private static List<String> namedHeaders(List<String> values) {
    List<String> headers = new ArrayList<>();
    for (int i = 0; i < values.size(); i++) {
      String header = values.get(i).strip();
      headers.add(header.isEmpty() ? "Column " + (i + 1) : header);
    }
    return headers;
  }
}
