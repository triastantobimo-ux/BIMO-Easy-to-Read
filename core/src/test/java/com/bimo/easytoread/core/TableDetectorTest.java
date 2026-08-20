package com.bimo.easytoread.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class TableDetectorTest {
    @Test
    public void reconstructsRowsAndColumnsWithSmallVerticalJitter() {
        DocumentModel document = new DocumentModel("test", Collections.singletonList(
                new DocumentModel.Block(DocumentModel.BlockType.TABLE, Arrays.asList(
                        line("Item", 10, 10, 80, 24),
                        line("Qty", 120, 12, 160, 26),
                        line("Price", 210, 9, 280, 23),
                        line("Mask", 10, 42, 80, 56),
                        line("2", 120, 40, 140, 54),
                        line("15000", 210, 43, 270, 57),
                        line("Glove", 10, 72, 80, 86),
                        line("5", 120, 74, 140, 88),
                        line("25000", 210, 71, 270, 85)
                ))
        ));

        TableModel table = TableDetector.detect(document);

        assertTrue(table.isDetected());
        assertEquals(3, table.getColumnCount());
        assertEquals(3, table.getRows().size());
        assertEquals("Qty", table.getRows().get(0).get(1));
        assertEquals("25000", table.getRows().get(2).get(2));
    }

    @Test
    public void fallsBackToSingleColumnForParagraphs() {
        DocumentModel document = new DocumentModel("test", Collections.singletonList(
                new DocumentModel.Block(DocumentModel.BlockType.PARAGRAPH, Arrays.asList(
                        line("First sentence", 10, 10, 250, 24),
                        line("Second sentence", 10, 40, 250, 54)
                ))
        ));

        TableModel table = TableDetector.detect(document);

        assertFalse(table.isDetected());
        assertEquals(1, table.getColumnCount());
        assertEquals(2, table.getRows().size());
    }

    private static DetectedLine line(String text, int left, int top, int right, int bottom) {
        return new DetectedLine(text, new Box(left, top, right, bottom), 0.95f);
    }
}
