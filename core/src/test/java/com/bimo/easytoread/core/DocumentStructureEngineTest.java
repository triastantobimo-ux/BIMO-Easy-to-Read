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
    public void manualEditPreservesParagraphBoundaries() {
        DocumentModel document = DocumentModel.fromPlainText(
                "First paragraph.\nStill first.\n\nSecond paragraph."
        );

        assertEquals(2, document.getBlocks().size());
        assertEquals("First paragraph. Still first.\n\nSecond paragraph.", document.toPlainText());
    }
}
