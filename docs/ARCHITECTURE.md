# Architecture baseline

## Objective

Provide seamless Android OCR with structured output, automatic Clipboard copy, Markdown/DOCX export, no ads, and no cloud OCR.

## Runtime flow

```text
Camera / Gallery / Share
        |
Safe image decode, sampling, EXIF rotation
        |
Bundled OCR engine adapter
        |
Normalized lines, boxes, confidence
        |
DocumentStructureEngine
        |
Canonical DocumentModel
        |
Editor / Clipboard / Markdown / DOCX / Sharesheet
```

## Module boundaries

- `app`: Android UI, image acquisition, platform Clipboard, storage access framework, and OCR adapter.
- `core`: Android-independent document model, layout heuristics, renderers, and DOCX generator.
- `ocr.OcrEngine`: replaceable contract. UI and exporters do not depend directly on ML Kit.

## Current engine status

The CP1 build uses bundled ML Kit Latin OCR as a functional baseline. It performs on-device and requires no model download.

The production engine is not yet declared best. CP2 must benchmark:

1. PP-OCRv6 Small through ONNX Runtime Mobile.
2. PP-OCRv6 Tiny.
3. Bundled ML Kit baseline.
4. Tesseract Indonesian/English.

Hard gates are offline operation, zero royalty, redistribution permission, API 26 compatibility, and no runtime model download. Accuracy is then measured using CER/WER and reading-order metrics on an independent Indonesian/English corpus.

## Integrity controls

- OCR text is never generatively rewritten.
- Manual edits become the canonical export source.
- Clipboard, Markdown, and DOCX are rendered from the same model.
- DOCX text is XML-escaped and generated as minimal OOXML.
- Export failure does not remove the editor result.
- No user image or recognized text is logged.

## Cloud-only delivery

GitHub Actions is the build and test environment. CI produces:

- debug APK;
- release APK/AAB, unsigned unless signing secrets are configured;
- source ZIP with generated Gradle Wrapper;
- SHA-256 manifest;
- dependency report;
- Android permission evidence;
- unit, lint, and build reports.


## Layout-aware OCR pipeline

Before document rendering, the bundled OCR engine now performs four-orientation evaluation
(0, 180, 90, and 270 degrees) and chooses the most plausible result using recognition
confidence, readable-character ratio, word-like token ratio, and noise penalties.

The `ReadingOrderResolver` then classifies detected geometry into:

- single-column: natural top-to-bottom order;
- multi-column/newspaper: complete the left column top-to-bottom before moving right;
- poster/freeform: prioritize visually prominent headings, then nearby content zones;
- grid: preserve row-major top-to-bottom and left-to-right order.

The selected rotation and layout class are retained in the engine identifier for traceability.
These heuristics improve reading order but do not recreate arbitrary graphic design or guarantee
semantic interpretation of every package, poster, table, or heavily distorted photograph.
