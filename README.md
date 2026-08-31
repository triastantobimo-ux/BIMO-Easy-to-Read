# BIMO Easy to Read

Offline-first Android OCR with structured text, automatic Clipboard copy, Markdown/DOCX export, and
cell-aware XLSX export.

## Accuracy-first CP2 build

- Primary candidate: PP-OCRv6 Medium, bundled ONNX models.
- Orientation probe and compatibility fallback: bundled ML Kit Latin OCR.
- Wired-table reconstruction: OpenCV grid detection, geometry assignment, blank-cell preservation,
  and selective low-confidence cell re-OCR.
- Typed Excel values: decimal, percentage, Rupiah/IDR, date, and identifier preservation.
- Minimum: Android 12 / API 31; arm64-v8a; reference device Xiaomi 15T Pro.
- No runtime model download, account, ads, analytics, telemetry, cloud OCR, or Internet permission.

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
