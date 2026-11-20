package com.agentshelf.catalog.internal.importing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.jspecify.annotations.Nullable;

/**
 * Turns the first sheet of an Excel workbook (.xlsx) into CSV, so the rest of the import works the
 * same for both file types. Row numbers stay the same as in Excel, so error reports point to the
 * right rows. File content is untrusted data and is never interpreted: formulas are not run (the
 * value Excel saved is used) and the XML parser does not load external entities.
 */
public final class XlsxReader {

  /** An .xlsx file is a zip archive. Limits stop "zip bombs" that unpack to huge sizes. */
  static final long MAX_UNPACKED_BYTES = 100L * 1024 * 1024;

  static final int MAX_ZIP_ENTRIES = 1_000;
  static final int MAX_COLUMNS = 200;
  private static final int MAX_EXCEL_ROWS = 1_048_576;

  /** Excel shows at most 15 significant digits; the rest is binary floating-point noise. */
  private static final MathContext EXCEL_PRECISION = new MathContext(15);

  private static final byte[] ZIP_HEADER = {0x50, 0x4b, 0x03, 0x04};
  private static final byte[] OLE2_HEADER = {
    (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
  };

  static final String UNREADABLE =
      "We couldn't read this Excel file. Open it in Excel, save it again as .xlsx and upload it"
          + " again.";
  static final String OLD_OR_PROTECTED =
      "This is an old Excel file (.xls) or a password-protected one. In Excel, use Save As, choose"
          + " .xlsx, remove any password and upload it again.";

  private XlsxReader() {}

  /** True for zip-based files (.xlsx). Detected from the content, never from the file name. */
  public static boolean isXlsx(byte[] content) {
    return startsWith(content, ZIP_HEADER);
  }

  /** True for old .xls files and password-protected .xlsx files (both are OLE2 containers). */
  public static boolean isOldOrProtectedExcel(byte[] content) {
    return startsWith(content, OLE2_HEADER);
  }

  /** The first sheet as UTF-8 CSV with commas, one CSV record per Excel row. */
  public static byte[] toCsv(byte[] content) {
    CsvReader.checkSize(content);
    checkUnpackedSize(content);
    ByteArrayOutputStream out = new ByteArrayOutputStream(content.length * 2);
    try (ReadableWorkbook workbook = new ReadableWorkbook(new ByteArrayInputStream(content));
        Stream<Row> rows = workbook.getFirstSheet().openStream();
        CSVPrinter csv =
            new CSVPrinter(
                new OutputStreamWriter(out, StandardCharsets.UTF_8), CSVFormat.RFC4180)) {
      writeRows(rows.iterator(), csv);
    } catch (InvalidImportFileException e) {
      throw e;
    } catch (IOException | RuntimeException e) {
      // Damaged or unusual files fail in many ways inside the reader; all mean the same to users.
      throw new InvalidImportFileException(UNREADABLE);
    }
    return out.toByteArray();
  }

  private static void writeRows(Iterator<Row> rows, CSVPrinter csv) throws IOException {
    int nextRow = 1;
    int filledRows = 0;
    while (rows.hasNext()) {
      Row row = rows.next();
      // Rows without a row number count on from the previous one.
      int rowNumber = Math.max(row.getRowNum(), nextRow);
      if (rowNumber > MAX_EXCEL_ROWS) {
        throw new InvalidImportFileException(UNREADABLE);
      }
      // Blank placeholder records keep CSV record numbers equal to Excel row numbers.
      for (; nextRow < rowNumber; nextRow++) {
        csv.printRecord("");
      }
      List<String> values = values(row);
      if (values.isEmpty()) {
        csv.printRecord("");
      } else {
        csv.printRecord(values);
        // Header plus one row too many is enough for CsvReader to report the row limit.
        if (++filledRows > CsvReader.MAX_ROWS + 1) {
          return;
        }
      }
      nextRow = rowNumber + 1;
    }
  }

  /** The row's cell texts, without trailing empty cells. */
  private static List<String> values(Row row) {
    List<String> values = new ArrayList<>();
    int lastFilled = -1;
    for (int i = 0; i < row.getCellCount(); i++) {
      String text = text(row.getCell(i));
      if (!text.isBlank()) {
        if (i >= MAX_COLUMNS) {
          throw new InvalidImportFileException(
              "The sheet has more than " + MAX_COLUMNS + " columns. Remove the extra columns.");
        }
        lastFilled = i;
      }
      if (i < MAX_COLUMNS) {
        values.add(text);
      }
    }
    return values.subList(0, lastFilled + 1);
  }

  static String text(@Nullable Cell cell) {
    if (cell == null) {
      return "";
    }
    return switch (cell.getType()) {
      case EMPTY -> "";
      case ERROR -> cell.getRawValue() == null ? "" : cell.getRawValue();
      default -> text(cell.getValue());
    };
  }

  private static String text(@Nullable Object value) {
    return switch (value) {
      case null -> "";
      case BigDecimal number -> number(number);
      case Boolean bool -> bool ? "TRUE" : "FALSE";
      default -> value.toString();
    };
  }

  /** 12.5 stays "12.5" and 19.989999999999998 becomes "19.99", as Excel shows them. */
  static String number(BigDecimal value) {
    BigDecimal rounded = value.round(EXCEL_PRECISION).stripTrailingZeros();
    return rounded.signum() == 0 ? "0" : rounded.toPlainString();
  }

  /** Unpacks every entry once, counting bytes, and stops as soon as a limit is passed. */
  private static void checkUnpackedSize(byte[] content) {
    try (ZipFile zip =
        ZipFile.builder().setSeekableByteChannel(new SeekableInMemoryByteChannel(content)).get()) {
      long total = 0;
      int entries = 0;
      byte[] buffer = new byte[64 * 1024];
      Enumeration<ZipArchiveEntry> all = zip.getEntries();
      while (all.hasMoreElements()) {
        ZipArchiveEntry entry = all.nextElement();
        if (++entries > MAX_ZIP_ENTRIES) {
          throw new InvalidImportFileException(UNREADABLE);
        }
        try (InputStream in = zip.getInputStream(entry)) {
          for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
            total += read;
            if (total > MAX_UNPACKED_BYTES) {
              throw new InvalidImportFileException(
                  "The Excel file unpacks to more than 100 MB. Split it into smaller files.");
            }
          }
        }
      }
    } catch (IOException | UncheckedIOException | IllegalArgumentException e) {
      throw new InvalidImportFileException(UNREADABLE);
    }
  }

  private static boolean startsWith(byte[] content, byte[] header) {
    if (content.length < header.length) {
      return false;
    }
    for (int i = 0; i < header.length; i++) {
      if (content[i] != header[i]) {
        return false;
      }
    }
    return true;
  }
}
