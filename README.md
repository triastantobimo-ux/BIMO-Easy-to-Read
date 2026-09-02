# BIMO EasyDocs

Android document intelligence app with offline-first OCR, structured text, automatic Clipboard copy,
direct Share, a unified Export Center, and cell-aware XLSX export.

## Accuracy-first CP2 build

- Primary candidate: PP-OCRv6 Medium, bundled ONNX models.
- Orientation probe and compatibility fallback: bundled ML Kit Latin OCR.
- Wired-table reconstruction: OpenCV grid detection, geometry assignment, blank-cell preservation,
  and selective low-confidence cell re-OCR.
- Typed Excel values: decimal, percentage, Rupiah/IDR, date, and identifier preservation.
- Minimum: Android 12 / API 31; arm64-v8a; reference device Xiaomi 15T Pro.
- No runtime model download, account, ads, analytics, telemetry, cloud OCR, or Internet permission.

## V4 mobile workflow

- Global navigation: Beranda, Dokumen, Pindai, Alat PDF, OCR; Pindai remains centered.
- Settings is a header action, while history belongs to Beranda and Dokumen.
- Focused scan, PDF, and OCR workspaces hide global navigation.
- Scan supports single or batch sessions through the device's system camera, page review,
  rotation, deletion, reordering, PDF output, sharing, and an OCR bridge.
- The basic PDF Reader supports multi-page navigation, pinch zoom, sharing, and sending the
  current page to OCR.
- OCR uses one immediately editable workspace; there are no Read/Edit/Review tabs.
- A detected worksheet is shown as an editable cell grid with visible low-confidence cells.
- Standard result actions: Copy, Share, Export.
- Table result actions: Copy, Share, Export, Excel.
- Export Center: choose Markdown, Word, or eligible Excel, then Save As, Open with, or Share file.
- The first active PDF tool converts one or more images into a single PDF. Unimplemented tools are
  not presented as active controls.
- Device and cloud files are selected through Android's system document picker; no private cloud
  credential or hidden transfer is added by the app.

## Build policy

Android build and validation run only in GitHub Actions. The workflow downloads official model files,
verifies the published ONNX SHA-256 values, builds APK/AAB artifacts, runs tests and lint, audits APK
permissions, records dependencies/model provenance, and publishes a checksummed artifact package.

## Evidence boundary

Cloud success proves compilation, tests, packaging, and declared permissions. It does not prove
real-device PP-OCRv6 Medium operator compatibility or 100% worksheet accuracy. The production engine
lock requires a reconciled Indonesian/English and worksheet benchmark on the Xiaomi 15T Pro.

See:

- [Architecture](docs/ARCHITECTURE.md)
- [Excel contract](docs/EXCEL_EXPORT.md)
- [Model provenance](docs/MODEL_PROVENANCE.md)
- [Privacy](PRIVACY.md)
- [Delivery checkpoints](docs/DELIVERY_CHECKPOINTS.md)
- [V4 architecture](docs/ui-v4/PRODUCT_ARCHITECTURE.md)
- [V4 implementation status](docs/ui-v4/IMPLEMENTATION_STATUS.md)
