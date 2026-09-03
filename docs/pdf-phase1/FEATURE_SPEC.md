# Phase 1 - PDF Reader & Editor

## Objective

Deliver a standalone PDF Reader & Editor. Opening a PDF must not initialize OCR and must not route the user to PDF conversion tools. OCR remains an optional action for the current page.

## Navigation contract

`Home/PDF tab -> PDF Center -> Open or Recent PDF -> PDF Workspace`

The PDF Workspace is a focused screen and therefore does not contain the five-item global navigation bar.

## Reader scope

- Open PDF through Android Storage Access Framework, including document providers installed on the device.
- Continuous paginated rendering.
- Native text search and text selection when a PDF contains a text layer.
- Page jump, bookmarks, resume last page, and one/two-page display.
- Zoom from 50% to 2500%.
- Share original PDF, Android print flow, and explicit OCR-current-page bridge.
- Password-protected, corrupt, or unsupported files return a visible error instead of a false success.

## Editor scope

On devices with Android S extension 18 or later:

- Ink annotation and highlighting.
- Erase, undo, and redo through the AndroidX PDF editing toolbox.
- Fill supported PDF form fields.
- Add a handwritten visual signature as an annotation.
- Save to the original URI when writable, otherwise save a copy through Android Storage Access Framework.
- Warn before closing with unsaved edits.

On devices with Android S extension 19 or later, the advanced content editor can select supported
text/image page objects, replace text using the existing object's font/style/matrix, insert an image
at a selected page position, delete a supported object, and save a new PDF copy.

## Explicit boundaries

- A handwritten mark is a visual signature, not a certificate-backed digital signature.
- Existing supported text-object styling is retained, but exact rendering of arbitrary replacement
  glyphs cannot be guaranteed when those glyphs are absent from the embedded font.
- Merge, split, reorder, convert, compress, redact, encrypt, and certificate signing belong to the standalone PDF Tools phase.
- OCR is not required to read a normal text-layer PDF.

## Privacy and connectivity

The application declares no `INTERNET`, `ACCESS_NETWORK_STATE`, advertising-ID, or broad-storage permission. A cloud-drive file is accessed only through the Android document provider selected by the user. The application receives a scoped URI grant, not cloud credentials.

