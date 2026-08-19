# Third-party notices

This repository depends on third-party software. No component listed below requires a per-scan or runtime license payment, but applicable notices and terms remain mandatory.

## Google ML Kit Text Recognition v2

- Artifact: `com.google.mlkit:text-recognition:16.0.1`
- Use: bundled, on-device Latin text recognition
- Runtime model download: none for the bundled artifact
- Reference: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- Terms: https://developers.google.com/terms

ML Kit is a no-cost Google SDK, not an open model. It remains a benchmark baseline until the open-source PP-OCR candidate is evaluated in CP2.

## JUnit

- Artifact: `junit:junit:4.13.2`
- License: Eclipse Public License 1.0
- Use: test scope only; not packaged in the Android application

## Android Gradle Plugin and Android build tools

- Used only for build and packaging.
- Reference: https://developer.android.com/studio/terms

A generated dependency report is included in each GitHub Actions build artifact. Before production release, the complete transitive dependency notice set must be reviewed and bundled where required.
