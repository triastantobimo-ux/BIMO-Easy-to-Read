package com.bimo.easytoread;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PdfFeatureContractTest {
    @Test
    public void pdfProcessorIsBundledAndIndependentFromSdkExtensions() {
        assertEquals("Bundled PDFium 1.0.33", PdfFeatureContract.ENGINE);
        assertFalse(PdfFeatureContract.REQUIRES_SDK_EXTENSION);
        assertFalse(PdfFeatureContract.REQUIRES_RUNTIME_DOWNLOAD);
    }

    @Test
    public void readerAndEditorContractsRemainExplicit() {
        assertEquals(7, PdfFeatureContract.deliveredReaderFeatures().length);
        assertEquals(6, PdfFeatureContract.editorAcceptanceCriteria().length);
        assertEquals(3, PdfFeatureContract.explicitNonClaims().length);
        assertTrue(PdfFeatureContract.editorAcceptanceCriteria()[5].contains("No AndroidX PDF"));
    }
}
