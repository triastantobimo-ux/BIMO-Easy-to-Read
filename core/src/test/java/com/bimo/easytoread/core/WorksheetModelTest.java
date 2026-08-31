package com.bimo.easytoread.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.Test;

public final class WorksheetModelTest {
    @Test
    public void worksheetGeometryTakesPrecedenceAndPreservesBlankCells() throws Exception {
        WorksheetModel worksheet = new WorksheetModel(
                3,
                3,
                Arrays.asList(
                        new WorksheetModel.Cell(0, 0, 1, 1, "Item", 0.99f),
                        new WorksheetModel.Cell(0, 1, 1, 1, "Amount", 0.98f),
                        new WorksheetModel.Cell(0, 2, 1, 1, "Rate", 0.98f),
                        new WorksheetModel.Cell(1, 0, 1, 1, "Mask", 0.96f),
                        new WorksheetModel.Cell(1, 1, 1, 1, "Rp 1.234,50", 0.94f),
                        new WorksheetModel.Cell(1, 2, 1, 1, "12,5%", 0.93f),
                        new WorksheetModel.Cell(2, 0, 1, 1, "Glove", 0.95f),
                        new WorksheetModel.Cell(2, 2, 1, 1, "10%", 0.91f)
                ),
                0,
                0.97f,
                0.95f,
                WorksheetModel.VerificationStatus.REVIEW_REQUIRED,
                "wired-grid cell OCR"
        );
        DocumentModel document = new DocumentModel(
                "pp-ocrv6-medium",
                Collections.singletonList(new DocumentModel.Block(
                        DocumentModel.BlockType.PARAGRAPH,
                        Collections.singletonList(new DetectedLine(
                                "unrelated paragraph fallback",
                                new Box(0, 0, 100, 10),
                                0.9f
                        ))
                )),
                worksheet
        );

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        XlsxExporter.write(document, output);
        Map<String, String> entries = unzip(output.toByteArray());
        String sheet = entries.get("xl/worksheets/sheet1.xml");

        assertTrue(entries.get("xl/workbook.xml").contains("name='Table'"));
        assertFalse(sheet.contains("unrelated paragraph fallback"));
        assertTrue(sheet.contains("<c r='B2' s='18'><v>1234.50</v></c>"));
        assertTrue(sheet.contains("<c r='C2' s='12'><v>0.125</v></c>"));
        assertTrue(sheet.contains("<c r='B3'"));
        assertTrue(sheet.contains("<c r='C3' s='11'><v>0.1</v></c>"));
    }

    private static Map<String, String> unzip(byte[] bytes) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
