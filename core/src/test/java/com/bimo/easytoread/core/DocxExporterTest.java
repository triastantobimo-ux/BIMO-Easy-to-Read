package com.bimo.easytoread.core;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.Test;

public class DocxExporterTest {
    @Test
    public void writesMinimumValidDocxPackageAndEscapesText() throws Exception {
        DocumentModel document = new DocumentStructureEngine().structure(
                "test",
                Arrays.asList(
                        new DetectedLine("TITLE", new Box(0, 0, 100, 30), 0.9f),
                        new DetectedLine("A & B < C", new Box(0, 40, 100, 50), 0.9f)
                )
        );

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DocxExporter.write(document, output);

        Map<String, String> entries = unzip(output.toByteArray());
        assertNotNull(entries.get("[Content_Types].xml"));
        assertNotNull(entries.get("_rels/.rels"));
        assertNotNull(entries.get("word/document.xml"));
        assertNotNull(entries.get("word/styles.xml"));
        assertTrue(entries.get("word/document.xml").contains("A &amp; B &lt; C"));
    }

    private static Map<String, String> unzip(byte[] bytes) throws Exception {
        Map<String, String> output = new HashMap<>();
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8);
        ZipEntry entry;
        byte[] buffer = new byte[1024];
        while ((entry = zip.getNextEntry()) != null) {
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            int count;
            while ((count = zip.read(buffer)) >= 0) {
                if (count > 0) content.write(buffer, 0, count);
            }
            output.put(entry.getName(), content.toString(StandardCharsets.UTF_8.name()));
        }
        return output;
    }
}
