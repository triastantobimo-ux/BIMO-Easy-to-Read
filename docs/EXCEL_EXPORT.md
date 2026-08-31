# Excel export contract

## Inclusion rule

When a wired table is detected, the app constructs a `WorksheetModel` from grid intersections and
only exports values assigned to those cells. Page chrome, ribbon text, formula bar content, row/column
headers outside the reconstructed grid, sheet tabs, status controls, and adjacent-screen text are not
worksheet values.

Blank cells inside the detected topology remain blank cells; neighboring text must not shift across
their position.

## Recognition and verification

1. PP-OCRv6 Medium performs page-level detection and recognition.
2. Recognized boxes are assigned to cells by geometry.
3. A visually non-empty cell with blank or low-confidence OCR receives a bounded crop-level OCR pass.
4. The result carries topology confidence, text confidence, and AUTOMATIC or REVIEW_REQUIRED status.
5. VERIFIED is reserved for a future explicit cell-review action; it is never assigned automatically.

## Visible values and formats

- Integers and localized decimals are stored as numeric values with the visible decimal scale.
- Percentages are stored as numeric fractions with percentage formatting.
- `Rp` and `IDR` values are stored as numeric values with Rupiah formatting.
- Recognized dates are stored as Excel date serials with date formatting.
- Identifier/code columns and leading-zero values remain text.
- Formula cells export their visible rendered value only; formulas are never invented.

## Unsupported source semantics

A photograph does not contain hidden rows/columns, formulas, conditional-format rules, validation,
named ranges, macros, comments, external links, or exact source fonts. These are not recoverable
unless visibly represented and independently recognized.

## Current table coverage

- Wired/grid tables: implemented.
- Borderless/wireless tables: pending neural structure model integration.
- Complex merged cells: pending structure/cell detector and merge reconciliation.
- If reliable cell topology is unavailable, the exporter uses the conservative one-column OCR
  fallback instead of fabricating cells.
