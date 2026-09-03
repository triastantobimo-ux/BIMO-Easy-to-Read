# UI Fidelity and Navigation QA Gate

## Purpose

A successful Gradle build proves that the implementation compiles and packages, but it does not prove pixel-level design fidelity. Per product-owner direction on 1 September 2026, screenshot comparison is an on-demand QA activity and is not part of the development critical path.

## Development-critical gates

The following remain mandatory for every development commit:

1. navigation contract unit tests;
2. Android unit tests;
3. lint;
4. debug and release APK build;
5. release AAB build;
6. bundled OCR model packaging verification;
7. dependency evidence;
8. permission and outbound-connection audit.

These gates run only in GitHub Actions. The Windows office device must not run Gradle, Android SDK, emulator, ADB, APK installation, or the application.

## Manual visual QA

The workflow `.github/workflows/android-ui-qa.yml` is started only with `workflow_dispatch`. It is used when a release candidate or material UI change needs screenshot evidence.

When invoked, GitHub Actions should produce:

1. fixed-viewport screenshots;
2. foreground-activity evidence;
3. navigation test results;
4. screenshot comparison results;
5. APK build results.

The foreground-activity assertion must pass before any screenshot can be treated as valid evidence. A similarity score cannot override an obscured screen, system dialog, crash, blank screen, or wrong foreground package.

## Protected screenshots

- Home light
- Scan light: Document selected
- Document Workspace light: Baca
- Additional light/dark and contextual states when a release candidate is tested

## Comparison rule

- Reference and implementation use the same app-owned viewport and state.
- Android-owned status/navigation bars are excluded where practical.
- Dynamic content regions are masked only when variability is intentional.
- Reference and actual screenshots must be reviewed side by side.
- P0/P1/P2 visual differences block only the visual-fidelity sign-off, not unrelated development.
- P3 differences may be documented but cannot change navigation, labels, palette, or component family.

## Navigation assertions

Automated tests verify:

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
11. Existing OCR and export features remain reachable.
12. No duplicate or orphan destination exists.

## Evidence status terms

- **BUILD VERIFIED** — mandatory development-critical gates passed.
- **VISUAL PASSED** — valid screenshots and manual side-by-side review passed.
- **VISUAL DEFERRED** — visual QA intentionally not run or not relied upon.
- **FAILED** — a mandatory gate or verified behavior failed.

Do not describe an implementation as 100% visually faithful while visual status is DEFERRED or FAILED.

## Invalidated evidence

Screenshot QA run #5 reported an automated pass, but manual inspection found all three screenshots obscured by the system dialog “Google Play services isn't responding”. That result is invalid and cannot support visual-fidelity claims.

The workflow was subsequently changed to an AOSP image and foreground-package assertions, then moved to manual execution so it does not delay development.

## Change control

Any intentional change to menu labels, navigation destinations, tab order, action placement, palette, typography family, icon family, component shape, or hierarchy requires an explicit owner instruction and revision-log entry.
