# UI V2 Revision Log

## 2026-09-01 — UI-V2-002

Status: Implemented; visual QA deferred by product-owner direction.

### Decision

- Keep Android compilation, tests, lint, packaging, model verification, dependency evidence, and permission audit on the mandatory cloud build path.
- Remove emulator screenshot comparison from automatic pull-request execution.
- Retain screenshot QA as a manual `workflow_dispatch` release-candidate check.
- Reject screenshots unless the BIMO package is confirmed as the foreground activity.

### Evidence and rationale

- Cloud builds #27, #28, #29, and #30 completed successfully.
- Screenshot QA run #5 produced a false-positive automated pass.
- Manual side-by-side inspection showed every actual screenshot obscured by the system dialog “Google Play services isn't responding”.
- The false-positive result was invalidated.
- Workflow commits changed the emulator to AOSP, added foreground assertions, and removed visual QA from the development critical path.

### Current checkpoint

- Design baseline: LOCKED.
- Navigation contract: IMPLEMENTED AND TESTED.
- Native Android flow Beranda → Pindai → Workspace: IMPLEMENTED.
- Mandatory cloud build gates: BUILD VERIFIED.
- Cloud screenshot parity: VISUAL DEFERRED / MANUAL.
- Physical-device parity: NOT YET RUN.
- No claim of 100% pixel fidelity is made.

## 2026-09-01 — UI-V2-001

Status: Approved and locked.

### Decision

The three approved visual concepts are one continuous application hierarchy:

1. Home / Document Hub
2. Scan
3. Document Workspace

They are not competing alternatives.

### Locked changes

- Introduced five-destination global navigation.
- Introduced hierarchical scan modes.
- Introduced focused Document Workspace.
- Reduced workspace tabs to Baca, Edit, Review.
- Moved Export from tab-level navigation to contextual action.
- Moved OCR and Excel verification into Review.
- Required contextual toolbars.
- Required visual and navigation screenshot gates.
- Preserved all existing OCR, Clipboard, scale, scrollbar, Share, Markdown, Word, and Excel capabilities.

### Owner acceptance criterion

The implementation must follow the approved concept without unapproved tab, menu, label, navigation, palette, or component changes.

### Evidence state at lock

- Design baseline: LOCKED.
- Navigation contract: LOCKED.
- Android implementation: NOT YET VERIFIED.
- Cloud screenshot parity: NOT YET RUN.
- Physical-device parity: NOT YET RUN.
