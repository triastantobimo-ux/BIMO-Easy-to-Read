# Accuracy-first Android OCR architecture

## Decision

The CP2 primary candidate is **PP-OCRv6 Medium through ONNX Runtime Android**. The app
bundles the detection and recognition models at build time. It performs no runtime model
download and requests no Internet permission.

Bundled ML Kit remains an orientation probe and an explicit compatibility fallback. A fallback
result retains the ML Kit engine identifier; it must not be reported as a PP-OCRv6 result.

## Reference device and support boundary

- Reference device: Xiaomi 15T Pro, 12 GB RAM, arm64.
- Minimum OS for the accuracy-first build: Android 12 / API 31.
- Packaged ABI: arm64-v8a.
- JDK: 17; compile/target SDK: 36.
- Inference: FP32 ONNX Runtime CPU is the correctness baseline. NNAPI may only be enabled after
  output-parity testing on the reference device.

## Runtime flow

```text
Camera / Gallery / Share
        |
Safe decode + EXIF correction
        |
Bundled ML Kit orientation probe
        |
Physical rotation + image quality assessment
        |
PP-OCRv6 Medium detection and recognition
        |------------------------------|
        |                              |
Document reading order          Wired-grid projection
        |                              |
Canonical DocumentModel         OCR assignment per cell
        |                              |
Clipboard / Markdown / DOCX     Selective low-confidence cell re-OCR
                                       |
                                WorksheetModel + confidence status
                                       |
                                Typed XLSX visible-value export
```

## Excel integrity contract

The worksheet pipeline preserves detected row/column topology, including blank cells. Text from
the page-level OCR pass is assigned by cell geometry; cells that contain visual ink but are blank or
low-confidence receive a bounded second OCR pass on the cell crop.

The exporter only emits visible recognized values. It does not invent formulas, hidden rows or
columns, data validation, named ranges, macros, comments, or source workbook semantics.

A photo cannot justify a universal 100% claim. The operational target is **100% verified visible-cell
accuracy**: a workbook can be treated as verified only after every low-confidence cell is reviewed.
The current build exposes AUTOMATIC versus REVIEW_REQUIRED status; a dedicated cell-review editor
and VERIFIED transition remain a required checkpoint.

## Table classes

1. Wired/grid tables: implemented with OpenCV line morphology and projection.
2. Borderless/wireless tables: requires the planned SLANeXt/PP-TableMagic path.
3. Merged or complex cells: basic output is available, but neural structure/cell detection and merge
   reconciliation are not yet implemented on Android.

## Model provenance and packaging

GitHub Actions downloads official PaddlePaddle ONNX artifacts during the build, verifies the published
SHA-256 values for both large ONNX files, records model/config hashes, and embeds the models into the
APK. The source archive excludes generated ONNX binaries and retains the reproducible preparation
script.

## Evidence levels

- Cloud compile/test/lint/package success: confirms source and packaging only.
- Xiaomi 15T Pro instrumentation: required to confirm Medium operator compatibility, latency, peak
  memory, thermal behavior, and real image accuracy.
- Benchmark corpus reconciliation: required before declaring PP-OCRv6 Medium the production engine.
