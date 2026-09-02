# BIMO EasyDocs UI/UX Revision Log

## V3 - 2 September 2026

Product-owner decisions implemented:

- Product label changed from BIMO Easy to Read to BIMO EasyDocs.
- Global navigation changed to Beranda, Dokumen, Pindai, Alat, Aktivitas.
- Pengaturan moved from global navigation to the header gear.
- Home shortcuts retained but explicitly routed to the same Dokumen and Pindai workflows.
- Fabricated recent documents and fabricated cell-review task removed.
- Pindai source controls separated into Galeri, system Kamera, and File.
- Duplicate camera and file controls removed.
- Workspace export shelf and utility action bar consolidated into one adaptive action bar.
- Standard actions locked to Salin, Bagikan, Export.
- Excel shortcut shown only for an unchanged structured worksheet.
- Export Center added: format, then Save As/Open with/Bagikan file.
- Temporary file sharing secured through the existing non-exported content provider.
- Development build renamed to BIMO-EasyDocs-debug-arm64.apk and remains arm64 debug only.

Unchanged:

- PP-OCRv6 Medium primary OCR architecture and ML Kit compatibility fallback.
- Baca, Edit, Review Workspace tabs.
- Automatic Clipboard copy preference.
- Text scale range 50-150%.
- Visible result scrollbar.
- Markdown, Word, and structured Excel exporters.
- No ads, analytics, telemetry, or hidden transmission.
