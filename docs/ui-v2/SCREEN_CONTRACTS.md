# Screen Contracts

These contracts translate the approved mockups into implementation requirements. They are acceptance criteria, not suggestions.

## A. Home / Document Hub

### Structure

1. Compact brand header.
2. Heading: **Apa yang ingin Anda kerjakan?**
3. Primary action: **Buka dokumen**.
4. Secondary action: **Pindai dokumen**.
5. Supporting format text: **PDF, gambar, dokumen, dan tabel**.
6. One grouped **Terakhir dibuka** list.
7. Optional **Lanjutkan pekerjaan** row when an unresolved review exists.
8. Fixed five-destination global bottom navigation.

### Interactions

- Buka dokumen → Android document picker → content classification → appropriate workspace.
- Pindai dokumen → Scan screen.
- Recent item → Document Workspace.
- Pending review → Review mode at first unresolved item.
- Search → global local-document search.
- Settings/profile → Settings.

### Prohibited

- No feature grid.
- No direct Merge, Split, Sign, OCR, Excel, Word, cloud, or converter cards.
- No nested cards.
- No persistent OCR result panel.

## B. Scan

### Structure

1. Header: **Pindai**, History, Settings.
2. Heading: **Pilih jenis pemindaian**.
3. Four modes:
   - Dokumen
   - Tabel / Excel
   - Teks cepat
   - Beberapa halaman
4. Focused camera surface.
5. Source controls:
   - Galeri
   - Shutter
   - Kamera sistem
6. Text action: **Buka PDF atau gambar**.
7. Global bottom navigation remains visible.

### Mode behavior

| Mode | Default processing | Destination |
|---|---|---|
| Dokumen | edge detection, crop, perspective, dewarp | Document Review |
| Tabel / Excel | document correction plus table structure | Cell Review |
| Teks cepat | fast OCR and auto-copy | Text Review |
| Beberapa halaman | page tray, reorder, batch correction | Document Review |

Auto-classification may recommend another mode but cannot silently replace the user's explicit selection.

### Capture states

- No document
- Detecting
- Document detected
- Blur warning
- Glare warning
- Edge cut off
- Capturing
- Processing
- Manual corner adjustment

## C. Document Workspace

### Global rule

Focused workspace hides the global bottom navigation and uses Back to return to Documents.

### Header

- Back
- File type
- Filename
- Page count and storage location
- Search
- Overflow

### Workspace switch

Exactly:

1. **Baca**
2. **Edit**
3. **Review**

Export is never added as a fourth tab.

### Baca toolbar

- Thumbnail
- Cari
- Tandai
- Export

Status line explains whether native text exists or OCR is required.

### Edit toolbar

Appears only in Edit:

- Text
- Image
- Annotation
- Signature
- More

Edit opens a derived copy. The original remains immutable.

### Review toolbar

Appears only in Review:

- Text
- Table / Cells, when detected
- Page issues
- Previous exception
- Next exception
- Verify

Existing OCR text scale 50–150%, permanent scrollbar, selection, correction, auto-copy, and manual copy remain available here.

## D. Tools

Tools is a hierarchical catalogue, not a flat button wall.

```text
Tools
├── Convert
│   ├── PDF to Word
│   ├── PDF to Excel
│   ├── PDF to Image
│   ├── Image to PDF
│   └── Office/Image to PDF
├── Organize
│   ├── Merge
│   ├── Split
│   ├── Reorder
│   ├── Extract pages
│   └── Delete pages
├── Optimize
│   ├── Compress
│   ├── Deskew/dewarp
│   └── Enhance scan
├── Find and Extract
│   ├── Batch find words
│   ├── Extract text
│   ├── Extract images
│   └── Extract tables
├── Secure
│   ├── Password protect
│   ├── Remove password
│   ├── Redact
│   └── Permissions
└── Sign
    ├── Visual signature
    ├── Certificate signature
    └── Validate signature
```

Only the selected category exposes its commands.

## E. Settings

Groups:

1. Appearance
2. Language
3. OCR and processing
4. Clipboard
5. Storage and cloud
6. Privacy and network
7. Accessibility
8. About and licenses

No operational document actions belong in Settings.

## F. Responsive and accessibility contract

- Design reference width: 390 dp.
- Required widths: 360–480 dp portrait; tablets use adaptive two-pane layouts.
- Body: minimum 14 sp.
- Primary controls: minimum 48 dp touch target.
- Support Android font scaling without clipped labels.
- Selected state uses shape/fill plus text/icon treatment, never color alone.
- TalkBack labels must state control, state, and result.
- Landscape and foldable layouts must preserve hierarchy rather than shrink the phone layout.
