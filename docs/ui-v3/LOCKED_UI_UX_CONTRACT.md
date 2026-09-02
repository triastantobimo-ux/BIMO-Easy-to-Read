# BIMO EasyDocs - Locked UI/UX Contract V3

> Obsolete after device evidence and product-owner redesign request dated 2 September 2026.
> Use `../ui-v4/PRODUCT_ARCHITECTURE.md` and `../ui-v4/SCREEN_CONTRACTS.md`.

Status: locked by product owner  
Effective date: 2 September 2026  
Supersedes: UI/UX Baseline V2 where navigation or Workspace actions conflict

## 1. Global hierarchy

The bottom navigation contains exactly five destinations, in this order:

1. Beranda
2. Dokumen
3. Pindai
4. Alat
5. Aktivitas

Pindai is visually centered. Pengaturan is not a sixth destination and is opened only from the
header gear. Workspace is contextual and never appears in global navigation.

## 2. Route ownership

| Destination | Purpose | Primary output |
|---|---|---|
| Beranda | Orientation and two shortcuts | Route to the same Dokumen or Pindai flows |
| Dokumen | Open an existing PDF or image | Source forwarded to Workspace processing |
| Pindai | Choose scan mode and source | OCR result opened in Workspace |
| Alat | Discover implemented transformations | Route to the required source flow |
| Aktivitas | Processing/export history | Honest empty state until persisted history exists |
| Workspace | Read, edit, review, copy, share, export | Text or generated document artifact |

Home shortcuts do not implement separate workflows. `Buka dokumen` invokes the same Android system
document picker used by Dokumen. `Pindai dokumen` invokes the same Pindai screen used by the centered
navigation item.

## 3. Pindai contract

- Scan modes remain: Dokumen, Tabel / Excel, Teks cepat, Beberapa halaman.
- Source controls are distinct and non-overlapping:
  - Galeri: images only.
  - Center camera: Android system camera, preserving the device camera application capability.
  - File: PDF or image through Android Storage Access Framework.
- There is no duplicate camera or duplicate file link.
- Local and installed cloud providers can appear in the Android picker without the app receiving
  cloud credentials.

## 4. Workspace contract

Workspace tabs remain exactly: Baca, Edit, Review.

The sticky action bar is adaptive:

| State | Actions |
|---|---|
| Normal OCR | Salin, Bagikan, Export |
| Structured table available and unchanged | Salin, Bagikan, Export, Excel |

There is one action bar only. Thumbnail, Find, Bookmark, per-format buttons, and a second export shelf
must not be stacked below it. Search remains a header action.

## 5. Export Center

1. Export opens one format selector.
2. Markdown and Word are available for non-empty OCR results.
3. Excel is offered only when a structured worksheet exists; plain text must not be silently placed
   into a one-column workbook.
4. After format selection, the user chooses exactly one destination:
   - Save As: Android system file picker.
   - Open with: compatible installed application chooser.
   - Bagikan file: Android Sharesheet with the generated file.
5. Direct Excel is a shortcut to the same XLSX exporter and destination selector, not a separate
   export implementation.

## 6. Integrity and privacy rules

- Never display fabricated recent files, review counts, or activity records.
- Never expose an unfinished tool as an active production control.
- No ads, analytics, telemetry, hidden network calls, or broad storage permissions.
- Temporary exported files use the app cache and a grant-scoped content URI.
- Package ID remains `com.bimo.easytoread` for continuity; the product label is BIMO EasyDocs.

## 7. Build acceptance criteria

- One arm64-v8a installable debug APK for device testing.
- No unsigned release APK in the development artifact.
- Unit test locks the global order, Workspace tabs, and adaptive action order.
- Android resources parse successfully.
- GitHub Actions performs compilation, unit tests, lint, model packaging, and permission audit.
- Screenshot comparison is run on demand for material UI changes and does not block functional build
  progress if emulator infrastructure fails.
