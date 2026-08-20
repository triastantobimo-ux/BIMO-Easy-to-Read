# Excel export contract

## Inclusion rule

When the image is identified as a spreadsheet or table, the `Table` worksheet contains only
text recognized inside reconstructed cells. The exporter removes visible application chrome,
including ribbon/menu text, formula bar content, Excel row numbers, column letters, sheet tabs,
status/zoom controls, and text from an adjacent screen.

The workbook keeps title, subtitle, summary, header, data, and internal blank rows only when
their geometry is inside the detected worksheet frame. Empty trailing worksheet rows are trimmed.

## Visible value and format rules

- Integer and decimal text is written as a numeric cell while retaining the visible decimal scale.
- `%` values are written as numeric fractions with a percentage number format.
- `Rp` and `IDR` values are written as numeric values with a Rupiah number format.
- Recognized dates are written as Excel date serials with a visible date number format.
- ID, code, number, and identifier columns remain text so leading zeroes are not lost.
- Other text remains editable inline text.

## Evidence boundary

An image contains rendered values, not the source workbook model. Formula cells therefore export
their visible result only. Hidden rows/columns, formulas, conditional formatting rules, validation,
named ranges, macros, comments, and exact source fonts/colors are not recoverable unless they are
visibly represented and independently recognized.

If repeated cell geometry is not sufficiently reliable, Excel export uses a conservative one-column
`OCR Text` fallback instead of forcing unrelated text into guessed cells.

