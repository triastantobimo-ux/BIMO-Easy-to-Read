# BIMO EasyDocs V4 - Implementation Status

Status date: 2 September 2026  
Target build: `0.5.0-easydocs-v4`  
Verification boundary: source inspection complete; GitHub Actions compile/test evidence pending.

## Implemented in source

- Five-item global navigation: Beranda, Dokumen, Pindai, Alat PDF, OCR.
- Pindai remains the centered primary action.
- Focused scan review, PDF Reader, and OCR result screens hide global navigation.
- Single and batch capture use the OEM/system camera.
- Batch review supports add, previous/next, rotate, delete, and move left/right.
- Scan output supports Save PDF, Share PDF, Open PDF, and Continue to OCR.
- Basic PDF Reader supports all-page navigation, pinch zoom, reset zoom, share, and current-page OCR.
- OCR source supports camera, image picker, and PDF/file picker.
- OCR result is one immediately editable workspace without Read/Edit/Review tabs.
- A detected worksheet is rendered as an editable cell grid.
- Low-confidence worksheet cells receive a visible orange border.
- Copy, Share, Export, conditional Excel, Find, and 50-150 percent text scaling remain available.
- Export retains Save As, Open with, and Share file destinations, with a Save As fallback when no
  compatible app is installed.
- Image to PDF is the first functional PDF tool.
- Device and installed cloud providers are accessed through Android Storage Access Framework.

## Not yet implemented and therefore not represented as active controls

- Automatic document-edge detection and perspective correction.
- Manual four-corner crop override and enhancement presets.
- Searchable-PDF text layer generation for scan output.
- PDF text search, selection, bookmarks, print, annotations, signature, and PDF editing.
- PDF merge, split, reorder, compression, protection, redaction, and watermark tools.
- Persistent document library and activity history.

## Checkpoint assessment

| Checkpoint | Status | Evidence boundary |
|---|---|---|
| CP0 Architecture lock | Complete | V4 architecture and screen contracts documented |
| CP1 Navigation and honest surfaces | Source complete | Cloud compile/test pending |
| CP2 Functional document scanner | Partial | Capture, batch review, PDF output implemented; crop pipeline open |
| CP3 OCR Workspace | Substantially implemented | Device validation and table benchmark pending |
| CP4 PDF Reader and annotation | Partial | Reader core implemented; annotation/signing open |
| CP5 PDF Editor | Open | No active controls exposed |
| CP6 PDF tools | Partial | Image-to-PDF implemented; remaining tools open |
| CP7 Release quality | Open | Requires GitHub Actions and Xiaomi 15T Pro validation |

No completion claim may exceed this table. A successful GitHub Actions build will establish cloud
compile, unit-test, lint, packaging, permission, and model-bundling evidence only.
