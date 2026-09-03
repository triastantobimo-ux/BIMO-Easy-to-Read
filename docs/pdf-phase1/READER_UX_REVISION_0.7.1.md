# PDF Reader UX Revision 0.7.1

## Scope

This checkpoint corrects the physical-device defects reported against the bundled PDF reader. It does not claim that object-level PDF editing is complete.

## Implemented

- Compact PDF Center with single-line labels and compact recent-document rows.
- Removed engine/runtime copy and instructional capability cards from the primary UI.
- Compact icon toolbar for previous page, page jump, next page, fit, and bookmark.
- Fixed-width bottom action shelf; no horizontally clipped action buttons.
- Bounded pinch zoom and pan; a page cannot be dragged beyond its viewport bounds.
- Swipe left/up for next page and right/down for previous page while at fit scale.
- Persistent inline search bar with total-result counter and previous/next navigation.
- All matches on the visible page are highlighted; the active result has a stronger outline.
- Search remains local to the bundled PDF text layer. Scanned pages are routed to OCR.

## Explicitly open

- True object-level text replacement preserving the original embedded font and content stream.
- Image-object insertion and deletion.
- Visual signature placement and saved annotation layers.
- Undo/redo and dirty-state protection for editor operations.

These editor functions require a write-capable PDF layer and save-validation tests. They must not be represented as delivered by the read-only PDFium rendering layer.

## Verification boundary

- Local office device: source inspection and static XML checks only.
- Android compilation, lint, unit tests, APK assembly, dependency checks, and permission audit: GitHub Actions only.
- Physical-device interaction and visual acceptance: product-owner test on the generated debug APK.
