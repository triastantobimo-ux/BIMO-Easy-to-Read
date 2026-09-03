# Device UI Evidence Audit - 2 September 2026

Evidence inspected:

- `WhatsApp Image 2026-09-02 at 11.50.24 AM.jpeg`
- `WhatsApp Image 2026-09-02 at 11.43.37 AM.jpeg`
- `WhatsApp Image 2026-09-02 at 11.43.38 AM.jpeg`
- `WhatsApp Image 2026-09-02 at 11.43.38 AM (1).jpeg`
- `WhatsApp Image 2026-09-02 at 11.43.58 AM.jpeg`
- `WhatsApp Image 2026-09-02 at 11.43.58 AM (1).jpeg`

## Confirmed findings

| Priority | Finding | Evidence and impact | Required correction |
|---|---|---|---|
| P0 | Scan modes are visual state only | Multiple pages still shows one preview and no page session controls | Separate Pindai setup, Capture session, and Scan review |
| P0 | Document edge indicator is false | The green frame is static and does not align with actual paper corners | Implement edge geometry and manual corner override before showing detection success |
| P0 | Screen content collides | Scan cards and preview extend behind the sticky bottom navigation | Focused capture session hides global navigation and uses bounded review layout |
| P0 | PDF is not a PDF reader | Code renders only the first PDF page for OCR | Add a multipage PDF Workspace with page navigation and zoom |
| P0 | Tools are not executable | Tools screen is descriptive content only | Hide every tool until its job wizard and processor work |
| P1 | OCR task is fragmented | Read/Edit/Review tabs divide one correction task and consume vertical space | Use one immediately editable OCR Workspace |
| P1 | Error recovery is weak | Open-with failure only displays `No app can open this format` | Keep user in Export Center and offer Save As or Share fallback |
| P1 | Language is inconsistent | English labels appear while prior device screens used Indonesian | Apply the selected locale consistently to all visible strings |
| P1 | Icon hierarchy dominates content | Center scan controls and bottom nav consume excessive area | Reserve dominant scan button for setup only; hide nav during capture/workspace |

## Design health

Current V3 implementation health: poor. The app compiles, but the screenshots show that the
navigation model does not represent the requested product workflows. A successful build is not
evidence of usable UX or implemented scanner/PDF functionality.
