# UI Fidelity and Navigation QA Gate

## Purpose

A successful Gradle build does not prove design fidelity. UI V2 is complete only when structure, behavior, and screenshots all pass.

## Required cloud-only evidence

All Android compilation, emulator execution, screenshot capture, and artifact generation must run in GitHub Actions. The Windows office device must not run Gradle, Android SDK, emulator, ADB, APK installation, or the application.

For each protected screen, GitHub Actions must produce:

1. fixed-viewport screenshot;
2. semantic/accessibility hierarchy dump;
3. navigation test result;
4. screenshot comparison result;
5. APK/AAB build result.

## Protected screenshots

- Home light
- Home dark
- Scan light: Document selected
- Scan light: Table / Excel selected
- Scan dark
- Document Workspace light: Baca
- Document Workspace light: Edit
- Document Workspace light: Review
- Document Workspace dark: Baca
- OCR text review
- Excel cell review
- Settings light/dark

## Comparison rule

- Reference and implementation must use the same app-owned viewport.
- Android-owned status/navigation bars are excluded.
- Dynamic content regions are masked only when their variability is intentional.
- P0/P1/P2 visual differences block handoff.
- P3 differences may be documented but cannot change navigation, labels, palette, or component family.

## Navigation assertions

Automated tests must verify:

1. Home shows only Buka dokumen and Pindai dokumen as primary actions.
2. Global navigation order is fixed.
3. Pindai opens the four approved scan modes.
4. Document Workspace hides global navigation.
5. Workspace tabs are exactly Baca, Edit, Review.
6. Export remains a contextual action.
7. OCR tools appear only under Review.
8. Table cell review appears only when a table is available.
9. Edit tools appear only in Edit.
10. Back returns to the prior hierarchy level.
11. All existing OCR and export features remain reachable.
12. No duplicate or orphan destination exists.

## Fidelity status

Use only:

- **PASSED** — screenshot and interaction gates passed.
- **BLOCKED** — build passed but screenshots or device evidence are unavailable.
- **FAILED** — a protected mismatch or behavior defect exists.

Do not describe an implementation as 100% faithful while status is BLOCKED or FAILED.

## Change control

Any intentional change to:

- menu label;
- navigation destination;
- tab order;
- action placement;
- palette;
- typography family;
- icon family;
- component shape;
- hierarchy;

requires an explicit owner instruction, revision-log entry, updated reference, and re-baselined screenshot test.
