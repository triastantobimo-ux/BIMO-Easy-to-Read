# BIMO EasyDocs - Screen Contracts V4

## 1. Beranda

Purpose: choose a task without pretending to be the file library.

Visible controls:

- Header: brand, settings.
- Primary action: Pindai dokumen.
- Three route shortcuts: Buka PDF, OCR Scanner, Alat PDF.
- Recent section only when real persisted items exist; otherwise an honest empty state.

The shortcuts call the same destination routes as global navigation and contain no separate logic.

## 2. Dokumen

Purpose: locate and reopen documents.

Visible controls:

- Search real document history.
- Filter chips: Semua, PDF, Hasil pindai, OCR, Export.
- Buka file invokes Android's system picker, including installed cloud providers.
- File rows use an overflow menu: Open, Open as OCR, Share, Use PDF tool, Remove from history.

## 3. Pindai setup

Purpose: decide how pages are captured before opening a camera.

Initial choices:

- Dokumen tunggal.
- Batch beberapa halaman.

Future capture profiles such as ID card and book scan are not shown until implemented.

After selection, open a focused capture session with no global bottom navigation.

## 4. Capture session

Purpose: capture one or more high-quality pages.

- Uses the Android system camera by default for OEM camera processing quality.
- After each capture: auto edge detection, perspective correction, and a manual four-corner override.
- Page review controls: Retake, Rotate, Crop, Enhance, Delete.
- Batch controls: Add page, page thumbnails, reorder, Finish.
- Single mode shows Finish after the first accepted page.

## 5. Scan review

Purpose: review the complete page set before creating output.

- Main page preview and horizontal ordered thumbnails.
- Enhancement choices: Original, Auto, Document, Color, B&W.
- Primary action: Finish.
- Finish sheet: file name, PDF or image output, searchable-PDF option.
- Final actions: Save, Share, Open PDF, Continue to OCR.

## 6. OCR source

Purpose: choose what is recognized, not how a PDF is managed.

- Camera.
- Gallery.
- PDF/File.
- Recent scan.

Document/table/layout classification is automatic. A `Prioritize table` option may be exposed as an
advanced setting, not as a competing top-level workflow.

## 7. OCR Workspace

Purpose: review and correct recognition in one continuous screen.

There are no Read/Edit/Review tabs.

- Header: back, source name, find, overflow.
- Status strip: engine, blocks/lines, confidence warnings, text scale 50-150 percent.
- Main surface is immediately selectable and editable.
- Low-confidence text is marked inline without switching mode.
- Sticky actions: Salin, Bagikan, Export.
- If a structured table exists, the main surface is an editable cell grid and Excel is added as the
  fourth action. Plain-text view is available from overflow, not another permanent tab.
- Export Center: format, then Save As/Open with/Bagikan file.

## 8. PDF Workspace

Purpose: behave as a professional reader first, with context-dependent tools.

- Header: back, title, page indicator, search, overflow.
- Canvas: multipage vertical or single-page view, pinch zoom, page thumbnails.
- Default contextual toolbar: View, Annotate, Sign, Edit PDF, OCR.
- Selecting a tool replaces the toolbar with that tool's controls; it does not stack another bar.
- Reader actions: text selection, find, copy, bookmarks, print, share.
- Annotation: highlight, underline, strikeout, note, draw.
- Sign: typed, drawn, or image signature with move/resize before commit.
- Edit PDF:
  - native text uses available font metadata and original geometry;
  - scanned pages use immutable background plus OCR text/object overlays;
  - insert image is supported as an object layer;
  - changes require Save copy or explicit overwrite confirmation.

## 9. Alat PDF

Purpose: select a transformation, then run a predictable job wizard.

Categories:

- Convert: Image to PDF, PDF to Image, PDF to Word, PDF to Excel, PDF to text/Markdown.
- Organize: Merge, Split/Extract pages, Reorder, Rotate, Delete pages.
- Optimize: Compress, enhance scanned pages, make searchable.
- Protect and Sign: Password, unlock when authorized, redact, watermark, page numbers, sign.

Each enabled tool follows exactly four steps:

1. Select one or more inputs.
2. Configure output.
3. Review scope/order.
4. Process and show Save/Share/Open result.

Unimplemented tools are omitted from production builds rather than shown as active placeholders.
