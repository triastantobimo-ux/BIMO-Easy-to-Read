# Privacy baseline

BIMO Easy to Read V1 is designed for on-device processing.

## Data handling

- Images are decoded locally from a user-selected URI or a temporary camera file.
- Recognized text is processed locally.
- The application manifest deliberately requests no INTERNET permission.
- No cloud OCR, advertising SDK, analytics SDK, telemetry, or remote crash reporter is included.
- OCR history is not stored by V1.
- Temporary camera images are stored in the application cache.
- Recognized text is copied to the Android system Clipboard when auto-copy is enabled.
- Clipboard content is marked sensitive by default on Android versions that support the flag.

## Important Clipboard limitation

After content is placed on the system Clipboard, Android and the destination application control its subsequent handling. Users should avoid copying sensitive information on unmanaged devices.

## Release control

GitHub Actions fails the build if the generated APK declares INTERNET, ACCESS_NETWORK_STATE, or AD_ID.
