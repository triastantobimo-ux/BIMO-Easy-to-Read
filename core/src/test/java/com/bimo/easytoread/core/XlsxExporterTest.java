package com.bimo.easytoread.core;

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

public class XlsxExporterTest {
    @Test
    public void writesDetectedTableAndAuditTextSheets() throws Exception {
        DocumentModel document = new DocumentModel("test", Collections.singletonList(
                new DocumentModel.Block(DocumentModel.BlockType.TABLE, Arrays.asList(
                        line("Item", 10, 10), line("Qty", 120, 10),
                        line("Mask", 10, 40), line("2", 120, 40)
                ))
        ));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        XlsxExporter.write(document, output);
        Map<String, String> entries = unzip(output.toByteArray());

        assertTrue(entries.containsKey("xl/workbook.xml"));
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"));
        assertTrue(entries.containsKey("xl/worksheets/sheet2.xml"));
        assertTrue(entries.get("xl/workbook.xml").contains("name='Table'"));
        assertTrue(entries.get("xl/worksheets/sheet1.xml").contains("Mask"));
        assertTrue(entries.get("xl/worksheets/sheet1.xml").contains("autoFilter"));
    }

    private static DetectedLine line(String text, int left, int top) {
        return new DetectedLine(text, new Box(left, top, left + 70, top + 14), 0.95f);
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
