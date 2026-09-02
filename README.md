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

## Locked mobile workflow

- Global navigation: Home, Documents, Scan, Tools, Activity; Scan remains centered.
- Settings is a header action, not a duplicate global destination.
- Workspace tabs: Read, Edit, Review.
- Standard result actions: Copy, Share, Export.
- Table result actions: Copy, Share, Export, Excel.
- Export Center: choose Markdown, Word, or eligible Excel, then Save As, Open with, or Share file.
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
