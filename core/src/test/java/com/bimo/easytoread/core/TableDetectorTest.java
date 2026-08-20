package com.bimo.easytoread.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
    public void spreadsheetFrameExportsCellsAndRejectsApplicationChrome() {
        int[] columns = {50, 260, 390, 500, 650, 800, 980};
        List<DetectedLine> lines = new ArrayList<>();
        lines.add(line("File Home Insert Draw Page Layout", 0, 0, 640, 16));
        lines.add(line("A B C D E F G", 42, 27, 1030, 41));
        lines.add(row(1, 50, columns, "WP2-G â€” HOSPITAL ANALYSIS"));
        lines.add(row(2, 80, columns,
                "Hospital portfolio analytics from validated Theme Analysis"));
        lines.add(row(3, 110, columns,
                "Hospital Analysis Objects", "22", "Expected Objects", "22",
                "Movement Available", "", ""));
        lines.add(row(4, 140, columns,
                "Control", "Actual", "Expected", "Status", "Actual", "Expected", "Status"));
        lines.add(row(5, 170, columns,
                "Hospital object reconciliation", "22", "22", "PASS", "0", "0", "PASS"));
        lines.add(row(6, 200, columns,
                "Contributor mapping exceptions", "0", "0", "PASS", "0", "0", "PASS"));
        lines.add(row(7, 230, columns,
                "Movement reconciliation exceptions", "0", "0", "PASS", "0", "0", "PASS"));
        lines.add(row(8, 260, columns,
                "Lineage readiness exceptions", "0", "0", "PASS", "0", "0", "PASS"));
        lines.add(row(9, 290, columns,
                "Hospital Analysis ID", "Domain", "RS Code", "RS Name",
                "Active RF Count", "Earliest Portfolio Period", "Latest"));
        lines.add(row(10, 320, columns,
                "HOSP|1100", "Revenue Cycle", "1100", "BKS", "6", "30 Apr 2026", "31 Mei 2026"));
        lines.add(row(11, 350, columns,
                "HOSP1200", "Revenue Cycle", "1200", "KMY", "O", "30 Apr 2026", "31 Mei 2026"));
        lines.add(row(12, 380, columns,
                "HOSP|1300", "Revenue Cycle", "1300", "SBY", "6", "30 Apr 2026", "31 Mei 2026"));
        lines.add(row(13, 410, columns,
                "HOSP|1400", "Revenue Cycle", "1400", "KGA", "6", "30 Apr 2026", "31 Mei 2026"));
        lines.add(line("06_THEME_ANALYSIS", 1300, 320, 1450, 336));
        lines.add(line("07_HOSPITAL_ANALYSIS +", 20, 520, 430, 536));
        lines.add(line("100%", 1200, 550, 1260, 566));

        DocumentModel document = new DocumentModel("test", Collections.singletonList(
                new DocumentModel.Block(DocumentModel.BlockType.TABLE, lines)
        ));

        TableModel table = TableDetector.detect(document);

        assertTrue(table.isDetected());
        assertEquals(7, table.getColumnCount());
        assertEquals(13, table.getRows().size());
        assertEquals("WP2-G â€” HOSPITAL ANALYSIS", table.getRows().get(0).get(0));
        assertEquals("Hospital Analysis ID", table.getRows().get(8).get(0));
        assertEquals("HOSP|1100", table.getRows().get(9).get(0));
        assertEquals("HOSP|1200", table.getRows().get(10).get(0));
        assertEquals("0", table.getRows().get(10).get(4));
        assertEquals("30 Apr 2026", table.getRows().get(12).get(5));
        assertEquals(8, table.getPrimaryHeaderRowIndex());
        assertEquals(TableModel.RowRole.TITLE, table.getRowRoles().get(0));
        assertEquals(TableModel.RowRole.SUMMARY, table.getRowRoles().get(5));

        String allCells = table.getRows().toString();
        assertFalse(allCells.contains("File Home"));
        assertFalse(allCells.contains("06_THEME"));
        assertFalse(allCells.contains("07_HOSPITAL"));
        assertFalse(allCells.contains("100%"));
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

    private static DetectedLine row(int row, int top, int[] columns, String... values) {
        List<DetectedToken> tokens = new ArrayList<>();
        tokens.add(new DetectedToken(
                Integer.toString(row),
                new Box(5, top, 20, top + 14),
                0.99f
        ));
        StringBuilder text = new StringBuilder(Integer.toString(row));
        Box box = tokens.get(0).getBox();
        for (int index = 0; index < values.length; index++) {
            String value = values[index];
            if (value == null || value.isEmpty()) continue;
            int left = columns[index];
            DetectedToken token = new DetectedToken(
                    value,
                    new Box(left, top, left + Math.max(18, value.length() * 6), top + 14),
                    0.96f
            );
            tokens.add(token);
            box = box.union(token.getBox());
            text.append(' ').append(value);
        }
        return new DetectedLine(text.toString(), box, 0.96f, tokens);
    }

    private static DetectedLine line(String text, int left, int top, int right, int bottom) {
        return new DetectedLine(text, new Box(left, top, right, bottom), 0.95f);
    }
}

