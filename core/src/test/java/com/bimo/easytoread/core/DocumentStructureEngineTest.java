package com.bimo.easytoread.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class DocumentStructureEngineTest {
    @Test
    public void createsHeadingParagraphAndListInReadingOrder() {
        DocumentModel document = new DocumentStructureEngine().structure(
                "test-engine",
                Arrays.asList(
                        new DetectedLine("First paragraph line.", new Box(10, 50, 400, 60), 0.95f),
                        new DetectedLine("DOCUMENT TITLE", new Box(10, 10, 400, 40), 0.99f),
                        new DetectedLine("Second paragraph line.", new Box(10, 64, 400, 74), 0.94f),
                        new DetectedLine("1. First action", new Box(10, 100, 400, 110), 0.91f),
                        new DetectedLine("2. Second action", new Box(10, 114, 400, 124), 0.90f)
                )
        );

        assertEquals(3, document.getBlocks().size());
        assertEquals(DocumentModel.BlockType.HEADING, document.getBlocks().get(0).getType());
        assertEquals(DocumentModel.BlockType.PARAGRAPH, document.getBlocks().get(1).getType());
        assertEquals(DocumentModel.BlockType.LIST, document.getBlocks().get(2).getType());
        assertEquals(5, document.countLines());
        assertTrue(document.toPlainText().startsWith("DOCUMENT TITLE"));
    }

    @Test
    public void restoresBulletsOmittedByOcrForIndentedChildrenAfterNumberedColon() {
        DocumentModel document = new DocumentStructureEngine().structure(
                "test-engine",
                Arrays.asList(
                        new DetectedLine("5. Masukkan ke GitHub Actions Secrets:", new Box(10, 10, 390, 24), 0.96f),
                        new DetectedLine("ANDROID_KEYSTORE_BASE64", new Box(42, 28, 330, 42), 0.92f),
                        new DetectedLine("ANDROID_KEYSTORE_PASSWORD", new Box(42, 46, 350, 60), 0.91f),
                        new DetectedLine("ANDROID_KEY_ALIAS", new Box(42, 64, 300, 78), 0.93f),
                        new DetectedLine("6. Buat tag versi beta.", new Box(10, 84, 320, 98), 0.95f)
                )
        );

        String plain = document.toPlainText();
        assertTrue(plain.contains("5. Masukkan ke GitHub Actions Secrets:\n"
                + "• ANDROID_KEYSTORE_BASE64\n"
                + "• ANDROID_KEYSTORE_PASSWORD\n"
                + "• ANDROID_KEY_ALIAS\n"
                + "6. Buat tag versi beta."));
    }

    @Test
    public void manualEditPreservesParagraphBoundaries() {
        DocumentModel document = DocumentModel.fromPlainText(
                "First paragraph.\nStill first.\n\nSecond paragraph."
        );

        assertEquals(2, document.getBlocks().size());
        assertEquals("First paragraph. Still first.\n\nSecond paragraph.", document.toPlainText());
    }
}
