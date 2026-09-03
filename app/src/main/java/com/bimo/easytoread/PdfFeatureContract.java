package com.bimo.easytoread;

/** Auditable Phase 1 boundary for the standalone PDF Reader & Editor module. */
public final class PdfFeatureContract {
    public static final int EDITOR_EXTENSION_BASE = 31;
    public static final int EDITOR_EXTENSION_VERSION = 18;

    private PdfFeatureContract() {}

    public static String[] readerFeatures() {
        return new String[] {
                "Open PDF from device or Android document provider",
                "Continuous page rendering",
                "Native text search and text selection",
                "Page jump, bookmark, resume, and one/two-page layout",
                "Zoom 50%-2500%",
                "Share, print, and OCR current page"
        };
    }

    public static String[] editorFeatures() {
        return new String[] {
                "Ink annotation and highlighting",
                "Erase, undo, and redo",
                "PDF form filling",
                "Visual signature as ink annotation",
                "Save original when writable or save a copy"
        };
    }

    public static String[] explicitNonClaims() {
        return new String[] {
                "Certificate-based digital signature",
                "Arbitrary text-object replacement with exact embedded-font guarantee",
                "Page merge, split, reorder, or compression",
                "Password removal or security bypass"
        };
    }

    public static boolean supportsEditor(int sdkInt, int extensionVersion) {
        return sdkInt >= EDITOR_EXTENSION_BASE
                && extensionVersion >= EDITOR_EXTENSION_VERSION;
    }
}

