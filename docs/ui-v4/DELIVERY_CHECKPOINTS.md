# BIMO EasyDocs V4 Delivery Checkpoints

## CP0 - Architecture lock

- V4 global hierarchy and bridge rules documented.
- V3 marked obsolete.
- No new APK is promoted as a user-test baseline until CP1 and CP2 are complete.

## CP1 - Navigation and honest surfaces

- Global navigation: Beranda, Dokumen, Pindai, Alat PDF, OCR.
- Remove Read/Edit/Review OCR tabs.
- Remove fake scan detection and non-functional tool controls.
- Implement route-level instrumentation tests.

## CP2 - Functional document scanner

- Single and batch sessions.
- OEM system-camera capture loop.
- Auto edge detection and perspective correction.
- Manual four-corner override.
- Enhancement presets.
- Page add/delete/rotate/reorder.
- PDF/image assembly and Save/Share/Open/OCR bridge.

## CP3 - OCR Workspace

- One editable text surface.
- Adaptive table grid.
- Copy, Share, Export, conditional Excel.
- Real find, confidence review, 50-150 percent text scale.
- Export failure fallback.

## CP4 - PDF Reader and annotation

- Multipage rendering, zoom, search, selection, thumbnails, bookmarks.
- Highlight, underline, strikeout, note, and freehand drawing.
- Signature placement and save-copy behavior.

## CP5 - PDF Editor

- Native text/object inspection.
- OCR overlay editing for scanned PDF.
- Image insertion, object move/resize, undo/redo.
- Visual fidelity regression suite.

## CP6 - PDF tools

- Image to PDF, PDF to image, merge, split/extract, reorder, rotate, delete.
- Searchable PDF, compress, password, watermark, page numbers.
- Word/Excel/text conversion only after format-specific benchmark and fidelity checks.

## CP7 - Release quality

- GitHub Actions build and test evidence.
- Same-state screenshot comparison on representative Android dimensions.
- Xiaomi 15T Pro device workflow test.
- OCR and table benchmark; no 100 percent claim without reconciled ground truth.
- Privacy, permission, dependency, and license audit.
