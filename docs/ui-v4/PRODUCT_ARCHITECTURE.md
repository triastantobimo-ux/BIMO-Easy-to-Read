# BIMO EasyDocs - Product Architecture V4

Status: redesign baseline after device evidence dated 2 September 2026  
Supersedes: V3 navigation and Workspace model  
Product objective: combine document scanning, PDF reading/editing, PDF utilities, and high-accuracy
OCR without mixing their workspaces or duplicating routes.

## 1. Global information architecture

The global bottom navigation contains exactly five destinations:

1. Beranda
2. Dokumen
3. Pindai
4. Alat PDF
5. OCR

`Pindai` is the centered primary action. `Pengaturan` remains a header gear. `Aktivitas` is not a
global destination; history belongs to Beranda and Dokumen. Focused workspaces hide global
navigation to maximize the document canvas.

## 2. Module ownership

| Module | Owns | Must not own |
|---|---|---|
| Beranda | Orientation, real recent work, route shortcuts | File management or processing logic |
| Dokumen | Local/cloud file access, real history, document metadata | Camera capture or conversion settings |
| Pindai | Single/batch capture, edge detection, crop, enhance, page assembly | OCR text editor or PDF annotation |
| Alat PDF | Conversion, organize, optimize, protect jobs | General PDF reading or scan capture |
| OCR | Source-to-structured-text/table recognition and export | PDF page organization or annotations |
| PDF Workspace | Reading, search, annotation, signing, PDF edit mode | Global tool catalogue |

## 3. Shared document model

All modules exchange a `DocumentAsset`, not ad-hoc text or bitmap values.

```text
DocumentAsset
|- source URI and MIME type
|- immutable original
|- ordered pages
|  |- original bitmap/PDF page
|  |- detected corner geometry
|  |- crop and perspective transform
|  |- enhancement recipe
|  |- OCR text blocks and confidence
|  `- table/cell model when detected
|- PDF text/annotation layer metadata
|- derived exports
`- provenance and processing status
```

The immutable original prevents destructive edits and enables reprocessing. Derived files are saved
only after explicit user action.

## 4. Bridge rules

| From | User action | To | Data preserved |
|---|---|---|---|
| Pindai review | OCR | OCR Workspace | ordered cropped pages and originals |
| Pindai review | Save PDF | PDF Workspace or destination | page order, crop, enhancement |
| Dokumen | Open | PDF Workspace | original URI and read permission |
| Dokumen | Open as OCR | OCR Workspace | source URI and page selection |
| PDF Workspace | OCR/Edit text | OCR-backed PDF edit mode | original PDF plus text geometry |
| PDF Workspace | Use tool | Selected Tool Job | current PDF as preselected input |
| OCR Workspace | Export | Export Center | structured text and optional worksheet |
| Tool Job | Open result | PDF Workspace or compatible app | generated artifact and provenance |

No bridge may duplicate the target engine. For example, direct `Excel` and Export-to-Excel both call
the same worksheet exporter.

## 5. Privacy and cloud boundary

- OCR, crop, enhancement, PDF assembly, and document transformations are local by default.
- Android Storage Access Framework is the default gateway to device and installed cloud providers
  such as Google Drive and OneDrive.
- The app never receives a cloud password and never sends a file to an undisclosed endpoint.
- A future direct cloud connector requires explicit OAuth, provider identification, destination
  confirmation, and per-operation user consent.
- No ads, analytics, telemetry, or hidden network requests.

## 6. Accuracy boundary

- OCR remains PP-OCRv6 Medium as primary engine with bounded fallback.
- Table detection must produce an explicit cell grid before Excel is enabled.
- No OCR or photo-to-Excel system can truthfully guarantee 100 percent accuracy for arbitrary
  photos. Low-confidence cells require visible review and user confirmation.
- PDF visual fidelity and semantic editability are separate modes. A scanned page can preserve its
  exact appearance by retaining the original as a background and placing editable OCR overlays; it
  cannot guarantee recovery of the exact original font file from pixels.
