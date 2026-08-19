# Delivery checkpoints

| Checkpoint | Evidence | Status |
|---|---|---|
| CP0 Product and architecture baseline | Scope, privacy, license and architecture documents | Complete |
| CP1 Functional Android baseline | Cloud-compiled app, OCR, editor, Clipboard, Markdown, DOCX, Share | In progress |
| CP2 OCR benchmark and engine lock | Independent corpus, CER/WER, layout, latency, memory, app size | Not started |
| CP3 Input and device compatibility | API 26-36 test matrix, low-memory and camera/gallery tests | Not started |
| CP4 Structure quality | Reading order, heading, list, simple-table evidence | Not started |
| CP5 Export parity | Clipboard/Markdown/DOCX parity and opening tests | Not started |
| CP6 Security and privacy | Manifest, traffic, logging, dependency and permission audit | Partially automated |
| CP7 Beta | Signed internal build and representative device feedback | Not started |
| CP8 Production release | Signed AAB, release evidence pack and known limitations | Not started |

## CP1 exit gate

CP1 passes only when GitHub Actions confirms:

- core tests pass;
- Android lint passes;
- debug and release artifacts compile;
- APK declares no INTERNET, ACCESS_NETWORK_STATE, or AD_ID;
- artifact package contains APK/AAB/source/checksums/audit evidence.

## Known limitations of the CP1 baseline

- Printed text is the supported scope.
- Handwriting, complex tables, formulas, and exact page layout are not guaranteed.
- Camera and UI behavior still require real-device validation.
- Release artifacts remain unsigned until GitHub signing secrets are configured.
- ML Kit remains subject to Google terms and is not the final open-source engine decision.
