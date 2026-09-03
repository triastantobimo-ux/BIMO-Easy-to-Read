package com.bimo.easytoread.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.Test;

public class XlsxExporterTest {
    @Test
    public void tableWorkbookContainsOnlyCellsAndTypedVisibleFormats() throws Exception {
        DocumentModel document = new DocumentModel("test", Collections.singletonList(
                new DocumentModel.Block(DocumentModel.BlockType.TABLE, Arrays.asList(
                        line("Item", 10, 10), line("Amount", 150, 10),
                        line("Rate", 280, 10), line("Date", 390, 10), line("Code", 510, 10),
                        line("Mask", 10, 40), line("Rp 1.234,50", 150, 40),
                        line("12,5%", 280, 40), line("30 Apr 2026", 390, 40), line("00123", 510, 40),
                        line("Glove", 10, 70), line("2.500", 150, 70),
                        line("10%", 280, 70), line("1 Mei 2026", 390, 70), line("00007", 510, 70)
                ))
        ));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        XlsxExporter.write(document, output);
        Map<String, String> entries = unzip(output.toByteArray());
        String workbook = entries.get("xl/workbook.xml");
        String sheet = entries.get("xl/worksheets/sheet1.xml");
        String styles = entries.get("xl/styles.xml");

        assertTrue(entries.containsKey("docProps/core.xml"));
        assertTrue(workbook.contains("name='Table'"));
        assertFalse(workbook.contains("OCR Text"));
        assertFalse(entries.containsKey("xl/worksheets/sheet2.xml"));
        assertTrue(sheet.contains("<autoFilter ref='A1:E3'/>"));
        assertTrue(sheet.contains("ySplit='1'"));
        assertTrue(sheet.contains("<c r='B2' s='18'><v>1234.50</v></c>"));
        assertTrue(sheet.contains("<c r='C2' s='12'><v>0.125</v></c>"));
        assertTrue(sheet.contains("<c r='D2' s='19'><v>"));
        assertTrue(sheet.contains("<c r='E2' t='inlineStr' s='5'><is><t xml:space='preserve'>00123"));
        assertFalse(sheet.contains("<f>"));
        assertTrue(styles.contains("&quot;Rp&quot; #,##0.00"));
        assertTrue(styles.contains("formatCode='0.0%'"));
        assertTrue(styles.contains("formatCode='dd mmm yyyy'"));
    }

    @Test
    public void nonTableExportUsesSingleConservativeTextFallback() throws Exception {
        List<DetectedLine> lines = new ArrayList<>();
        lines.add(line("First paragraph", 10, 10));
        lines.add(line("Second paragraph", 10, 40));
        DocumentModel document = new DocumentModel("test", Collections.singletonList(
                new DocumentModel.Block(DocumentModel.BlockType.PARAGRAPH, lines)
        ));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        XlsxExporter.write(document, output);
        Map<String, String> entries = unzip(output.toByteArray());

        assertTrue(entries.get("xl/workbook.xml").contains("name='OCR Text'"));
        assertFalse(entries.containsKey("xl/worksheets/sheet2.xml"));
        assertTrue(entries.get("xl/worksheets/sheet1.xml").contains("First paragraph"));
    }

    private static DetectedLine line(String text, int left, int top) {
        return new DetectedLine(text, new Box(left, top, left + 90, top + 14), 0.95f);
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

