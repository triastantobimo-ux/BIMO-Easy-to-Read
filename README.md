# BIMO Easy to Read

Lightweight Android OCR with structured, editable output and automatic Clipboard copy.

## V1 capabilities

- Camera, Gallery, and Android Share input.
- Bundled on-device Latin OCR for Indonesian and English text.
- No runtime OCR model download.
- Reading-order reconstruction for headings, paragraphs, and lists.
- Editable OCR result.
- Automatic system Clipboard copy.
- Plain-text and styled Clipboard representation.
- Markdown and DOCX export.
- Android Sharesheet.
- Indonesian and English interface.
- Light, dark, and system appearance.
- No ads, analytics, telemetry, cloud OCR, or INTERNET permission.

## Supported Android versions

- Minimum: Android 8 / API 26.
- Target and compile SDK: Android 16 / API 36.

## Build policy

This repository is built and tested only in GitHub Actions.

The **Android Cloud Build** workflow performs:

1. JDK/Gradle/Android SDK setup on a GitHub runner.
2. Core unit tests.
3. Android unit tests and lint.
4. Debug APK, release APK, and release AAB builds.
5. Generated-APK permission audit.
6. Dependency evidence export.
7. Source ZIP packaging with generated Gradle Wrapper.
8. SHA-256 generation.

Open a workflow run and download the artifact named `BIMO-Easy-to-Read-<commit SHA>`.

## Release signing

Unsigned build artifacts are sufficient for technical testing but cannot be uploaded to Google Play as a production release.

Configure these GitHub Actions secrets before creating a `v*` tag:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The **Android Signed Release** workflow then publishes signed APK/AAB files, source ZIP, permission evidence, and SHA-256 checksums to GitHub Releases.

## Architecture and privacy

- [Architecture baseline](docs/ARCHITECTURE.md)
- [Privacy baseline](PRIVACY.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)
- [Delivery checkpoints](docs/DELIVERY_CHECKPOINTS.md)

## OCR quality status

The bundled ML Kit engine is a CP1 functional baseline, not yet a claim of best OCR. CP2 will compare it against open-source PP-OCRv6 Small/Tiny and Tesseract using an independent Indonesian/English benchmark. The UI and exporters are isolated behind an OCR engine contract so the production engine can be replaced without rewriting the application.
