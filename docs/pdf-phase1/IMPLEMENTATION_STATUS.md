# Phase 1 implementation status

Status labels: `Implemented`, `Cloud verified`, `Device validation pending`, `Out of Phase 1`.

| Capability | Status before CI | Acceptance evidence |
|---|---|---|
| Dedicated PDF Center and recent files | Implemented | Source and unit contract |
| PDF picker from device/document providers | Implemented | Source; device validation pending |
| Continuous PDF reader | Implemented | AndroidX PDF integration; cloud compile pending |
| Search and text selection | Implemented | AndroidX PDF viewer; device validation pending |
| Page jump, bookmark, resume | Implemented | Source and local-state implementation |
| Zoom 50%-2500%, one/two-page layout | Implemented | Source; device validation pending |
| Share and print | Implemented | Android intents/print adapter; device validation pending |
| OCR current page bridge | Implemented | Existing OCR entry route; device validation pending |
| Ink/highlight/erase/undo/redo | Implemented with capability gate | Requires Android S extension 18; device validation pending |
| Form filling | Implemented with capability gate | Requires supported PDF form and Android S extension 18 |
| Save original/save copy | Implemented | SAF write path; device validation pending |
| Supported text-object edit preserving existing object styling | Implemented with capability gate | Requires Android S extension 19; exact new-glyph rendering remains PDF-font dependent |
| Insert/delete supported image objects | Implemented with capability gate | Requires Android S extension 19; device validation pending |
| Certificate digital signing | Out of Phase 1 | Must not be represented as visual ink signing |
| Exact embedded-font glyph guarantee for arbitrary new text | Out of Phase 1 | Source font may not contain replacement glyphs |
| Merge/split/reorder/convert/compress | Out of Phase 1 | Assigned to PDF All-in-One Tools |

This file must be updated to `Cloud verified` only after GitHub Actions tests, lint, permission audit, and debug APK build pass. Physical-device behavior remains unverified until tested on the target Xiaomi device.

