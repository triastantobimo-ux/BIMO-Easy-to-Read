# OCR model provenance

## Primary candidate

| Component | Source | SHA-256 |
|---|---|---|
| PP-OCRv6 Medium detection ONNX | https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_det_onnx | `eb13b44b25bb36f89528b68720af8a61d9cf381176107f465db1757b65d086e1` |
| PP-OCRv6 Medium recognition ONNX | https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_rec_onnx | `9c09abf0957f7968c7586464b7397b84ad2387a0497a351af40e9acc71b673ba` |
| Android SDK source | https://github.com/PaddlePaddle/PaddleOCR/tree/main/deploy/ppocr-android/ppocr-sdk | Apache-2.0 source retained with copyright headers |
| Runtime | `com.microsoft.onnxruntime:onnxruntime-android:1.21.1` | Maven dependency evidence recorded by CI |
| Computer vision | `com.quickbirdstudios:opencv:4.5.3` | Maven dependency evidence recorded by CI |

The recognition YAML is downloaded from the same official model repository. Its actual build hash is
recorded in `audit/ocr-model-sha256.txt`.

## Redistribution and runtime behavior

- Model and PaddleOCR source license: Apache License 2.0.
- ONNX Runtime license: MIT.
- No runtime model download.
- No per-scan payment.
- No cloud OCR.
- No analytics, ads, telemetry, or Internet permission.

## Verification boundary

The official Android example directly documents Small/Tiny. Medium uses the same SDK interface but is
an accuracy-first engineering candidate. Cloud compilation does not prove that all Medium operators
execute correctly or within acceptable memory on the target device.
