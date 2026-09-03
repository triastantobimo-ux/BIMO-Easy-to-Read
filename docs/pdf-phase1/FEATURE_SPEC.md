# Phase 1 - BIMO PDF Reader and Editor

## Locked architecture

The application owns its PDF processing path. `io.legere:pdfiumandroid:2.0.3`
packages PDFium native binaries inside the APK. Opening, rendering, text extraction,
search, and the PDF-to-OCR page bridge do not depend on AndroidX PDF, Android SDK
Extensions, a browser, an external PDF application, an account, or a runtime download.

## Delivered reader checkpoint

- Open a PDF through Android Storage Access Framework, including a cloud provider
  explicitly selected by the user.
- Open password-protected PDFs after an explicit password prompt.
- Render native PDF pages with zoom, pan, fit, page navigation, page jump, bookmark,
  and resume-last-page state.
- Extract and select an existing text layer, copy page text, and search across pages.
- Share the original PDF, send the original vector PDF to Android Print Framework,
  save a byte-identical copy, or run the existing bundled OCR on the current page.
- Fail visibly for corrupt or unsupported files; never report a false success.

## Editor acceptance criteria

The internal editor checkpoint remains open until all of the following are backed by
the BIMO processor and device tests: highlight/annotation persistence, form editing,
visual signatures, object-level text/image editing, undo/redo, dirty-state protection,
and non-destructive save. A rendered or flattened imitation is not accepted as a
replacement for object-level PDF editing.

## Explicit boundaries

- Visual signatures are not certificate-backed digital signatures.
- Arbitrary replacement glyphs cannot be guaranteed when absent from an embedded font.
- Password removal and security bypass are not product capabilities.
- Merge, split, reorder, conversion, compression, redaction, encryption, and signing
  remain in the separate PDF All-in-One Tools phase.

## Privacy and connectivity

The manifest declares no internet, network-state, advertising-ID, or broad-storage
permission. Cloud-drive files are accessed only through a user-selected Android
document provider and a scoped URI grant. The app receives neither the provider
password nor general cloud-account access.
