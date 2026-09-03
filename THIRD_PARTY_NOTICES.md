# Third-party notices

This project contains no per-scan licensing, advertising, analytics, telemetry, or cloud OCR.

## PaddleOCR / PP-OCRv6 Medium

- Source and models: PaddlePaddle/PaddleOCR and official PaddlePaddle model repositories.
- License: Apache License 2.0.
- Use: bundled on-device text detection and recognition.
- Runtime model download: none.

## ONNX Runtime Android

- Artifact: `com.microsoft.onnxruntime:onnxruntime-android:1.21.1`.
- License: MIT.
- Use: on-device PP-OCRv6 ONNX inference.

## OpenCV Android

- Artifact: `com.quickbirdstudios:opencv:4.5.3`.
- License: Apache License 2.0.
- Use: image quality assessment, OCR preprocessing support, and wired-table grid extraction.

## Google ML Kit Text Recognition v2

- Artifact: `com.google.mlkit:text-recognition:16.0.1`.
- Use: bundled orientation probe and explicit compatibility fallback.
- Runtime model download: none for the bundled artifact.
- Terms: https://developers.google.com/terms

The release evidence contains the resolved Gradle dependency tree and generated APK permission audit.
