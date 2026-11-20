# 0005: Excel import — convert the first sheet to CSV with fastexcel-reader

- Status: Accepted
- Date: 2026-11-20

## Context
Most suppliers keep their price list in Excel. Asking them to "Save As CSV UTF-8" works, but it's
an extra step and a common source of garbled text. M1.1 adds direct `.xlsx` uploads.

The CSV import already has a column-mapping screen, a background job, an error report and tests.
We want Excel support without a second copy of that pipeline.

An `.xlsx` file is a zip archive of XML files. It's untrusted input, so the reader must:
- not run formulas or macros;
- not load external XML entities (XXE);
- survive "zip bombs": a 10 MB upload that unpacks to gigabytes;
- read large sheets without holding every cell in memory.

## Decision
On upload, the server converts the **first sheet** of an `.xlsx` file into UTF-8 CSV and stores
that. Everything after the upload (preview, mapping, background job, error report) is unchanged.
The file type is detected from the content's first bytes, never from the file name.

The reader is [fastexcel-reader](https://github.com/dhatim/fastexcel) (`org.dhatim:fastexcel-reader`,
0.20.2 at the time of writing, Apache-2.0).

How we convert:
- **Row numbers stay the same as in Excel.** Blank rows and rows above the header become empty CSV
  records, so "row 14" in the error report is row 14 in Excel.
- **Values, not formulas.** A formula cell gives the value Excel saved with the file.
- **Numbers as Excel shows them**: rounded to 15 significant digits, so `19.989999999999998` becomes
  `19.99`. Booleans become `TRUE`/`FALSE`. Trailing empty cells are dropped.
- **Limits**: the CSV limits still apply (10 MB upload, 20,000 product rows), plus at most
  100 MB unpacked, 1,000 zip entries and 200 columns. The unpacked size is measured by actually
  unpacking every entry before parsing, so a forged size in the zip header doesn't help.
- Old `.xls` files and password-protected `.xlsx` files (both OLE2 containers) are rejected with a
  message that says how to save the file as a plain `.xlsx`.

## Options considered
- **fastexcel-reader**: small, streams rows with StAX, turns off DTDs and external entities,
  returns saved formula results without evaluating them. Two transitive dependencies
  (`aalto-xml`, `commons-compress`). Reads `.xlsx` only, not `.xls`.
- **Apache POI**: the standard, reads `.xls` too and can evaluate formulas. Much larger
  (`poi-ooxml`, `xmlbeans`, `log4j-api` and more) and its default API loads the whole workbook into
  memory; streaming needs the lower-level SAX API. More than we need.
- **Ask suppliers to save as CSV**: no dependency, but a worse first experience and more support
  questions about encodings.
- **Keep the original `.xlsx` and read it in the background job**: the job and the error report
  would need a second code path for every step. Converting once on upload keeps one path.

## Consequences
- Easier: one import pipeline for both file types. Excel users skip the "CSV UTF-8" step and
  avoid encoding problems.
- Harder: one more library to keep up to date (Dependabot covers it). The stored file is the
  converted CSV, so the original workbook is not kept.
- Limits we accept: only the first sheet is read; `.xls` isn't supported; dates come out as
  Excel's internal numbers (no product field is a date); SKUs that Excel stored as numbers have
  already lost their leading zeros, so the upload page advises formatting SKU columns as text.
