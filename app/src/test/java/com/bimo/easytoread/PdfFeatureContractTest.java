package com.bimo.easytoread;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PdfFeatureContractTest {
    @Test
    public void readerAndEditorRemainStandaloneFromOcrAndPdfTools() {
        assertEquals(6, PdfFeatureContract.readerFeatures().length);
        assertEquals(6, PdfFeatureContract.editorFeatures().length);
        assertEquals(4, PdfFeatureContract.explicitNonClaims().length);
    }

    @Test
    public void editorRequiresAndroidSExtension18() {
        assertFalse(PdfFeatureContract.supportsEditor(30, 18));
        assertFalse(PdfFeatureContract.supportsEditor(31, 17));
        assertTrue(PdfFeatureContract.supportsEditor(31, 18));
        assertTrue(PdfFeatureContract.supportsEditor(36, 20));
    }
}

