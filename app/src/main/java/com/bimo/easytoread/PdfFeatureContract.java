package com.bimo.easytoread;

/** Auditable Phase 1 boundary for the standalone BIMO PDF processor. */
public final class PdfFeatureContract {
    public static final String ENGINE = "Bundled PDFium 1.0.33";
    public static final boolean REQUIRES_SDK_EXTENSION = false;
    public static final boolean REQUIRES_RUNTIME_DOWNLOAD = false;

    private PdfFeatureContract() {}

    public static String[] deliveredReaderFeatures() {
        return new String[] {
                "Open PDF from device or Android document provider",
                "Bundled native PDF rendering",
                "Password-protected PDF open",
                "Text-layer extraction, all-match highlighting, and previous-next search",
                "Page jump, bookmark, and resume",
                "Bounded pinch zoom, stable pan, and four-direction page swipe",
                "Share, print, save copy, and OCR current page"
        };
    }

    public static String[] editorAcceptanceCriteria() {
        return new String[] {
                "Annotation and highlighting saved inside the PDF",
                "Form field editing",
                "Visual signature annotation",
                "Text and image object editing without flattening untouched pages",
                "Undo, redo, dirty-state protection, and save copy",
                "No AndroidX PDF or SDK Extension dependency"
        };
    }

    public static String[] explicitNonClaims() {
        return new String[] {
                "Certificate-based digital signature",
                "Exact embedded-font glyph guarantee for arbitrary replacement text",
                "Password removal or security bypass"
        };
    }
}
