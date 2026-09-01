# BIMO Easy to Read — Locked UI/UX Baseline V2

Status: **LOCKED BY PRODUCT OWNER**  
Effective date: 1 September 2026  
Scope: Android all-in-one document intelligence successor UI  
Decision: the three approved mockups are **sequential screens in one product**, not alternatives.

## 1. Visual source of truth

| Screen | Role | Source filename | SHA-256 |
|---|---|---|---|
| Home / Document Hub | Global entry and recent work | `exec-f65ec627-3e2d-4337-927e-d868e8bee4f7.png` | `4ED4446146D474B2F13AD62196ED52D5BD8DE0DF7CEB2EF13A730E51CA105E70` |
| Scan | Scan-mode selection and capture | `exec-8e1c82f9-84b7-4780-a74d-215218cd695b.png` | `149BD74142A946D44F3167AEC24595BBD25382E7DB1726910BB963AA89575D58` |
| Document Workspace | Focused PDF/document workspace | `exec-5b9e0e84-1924-457d-abde-4e5a820bc4c0.png` | `CAEA77DB6F5C675F9F110CD58B432B2EEC6D40242F75DBBB7F8A4576081815D0` |

The binary reference images were generated in the approved design session. Their checksums are retained so later replacements cannot silently redefine the target.

## 2. Locked product hierarchy

```text
Global application
├── Beranda
├── Dokumen
├── Pindai
├── Tools
└── Pengaturan

Pindai
├── Dokumen
├── Tabel / Excel
├── Teks cepat
└── Beberapa halaman

Document Workspace
├── Baca
├── Edit
└── Review
    ├── Teks
    ├── Tabel / Sel
    └── Masalah halaman

Contextual action
└── Export
    ├── Searchable PDF
    ├── Word
    ├── Excel
    ├── Markdown
    ├── Gambar
    ├── Bagikan
    └── Simpan ke provider
```

## 3. Non-negotiable navigation rules

1. Home contains only the two primary intents: **Buka dokumen** and **Pindai dokumen**.
2. PDF Editor, OCR, Excel, signature, merge, split, converter, cloud, and security tools must not be exposed as unrelated Home buttons.
3. The global bottom navigation is exactly: **Beranda, Dokumen, Pindai, Tools, Pengaturan**.
4. The global bottom navigation is hidden inside a focused Document Workspace.
5. Document Workspace navigation is exactly: **Baca, Edit, Review**.
6. **Export is an action, not a workspace tab.**
7. OCR and table verification live under **Review**, not as global navigation destinations.
8. Scan-mode options appear only after entering **Pindai**.
9. Edit tools appear only while **Edit** is active.
10. Reader tools appear only while **Baca** is active.
11. Low-confidence text/cell tools appear only while **Review** is active.
12. Existing quick OCR, automatic Clipboard copy, selectable text, 50–150% scale, visible scrollbar, Share, Markdown, Word, and Excel capabilities remain available through the relevant Review/Export context.

## 4. Visual language

### Light

- Base: `#EEEAF5`
- Top surface: `#F6F3FB`
- Elevated: `#F8F6FC`
- Editor/document surround: `#F3EFF8`
- Primary text: `#111338`
- Secondary text: `#625E7B`
- Primary indigo: `#211D78`
- Pressed indigo: `#171452`
- Mint: `#35D6A4`
- Warm accent: `#E7B84E`
- Outline: `#C9C3D8`

### Dark

- Base: `#040A13`
- Top surface: `#081321`
- Elevated: `#0C1A2A`
- Editor: `#06101D`
- Primary text: `#EAF4FF`
- Secondary text: `#8FA7BD`
- Primary purple: `#8F4DFF`
- Cyan: `#25D9FF`
- Outline: `#1A3850`

## 5. Fidelity boundary

The implementation must preserve the approved hierarchy, proportions, palette, component family, labels, active-state treatment, and navigation behavior. Differences are allowed only for:

- dynamic user content;
- responsive reflow on other Android widths;
- localization;
- accessibility/font scaling;
- Android-owned status/navigation bars;
- correction of generation artifacts such as duplicated labels, malformed logo lettering, or impossible camera imagery.

No menu, tab, label, or navigation destination may be renamed, moved, added, or removed without an explicit product-owner instruction and revision-log entry.
