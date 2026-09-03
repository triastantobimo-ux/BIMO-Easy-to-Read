package com.bimo.easytoread.core;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class DocxExporter {
    private DocxExporter() {}

    public static void write(DocumentModel document, OutputStream target) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(target, StandardCharsets.UTF_8);
        put(zip, "[Content_Types].xml", contentTypes());
        put(zip, "_rels/.rels", packageRelationships());
        put(zip, "word/_rels/document.xml.rels", documentRelationships());
        put(zip, "word/styles.xml", styles());
        put(zip, "word/document.xml", documentXml(document));
        zip.finish();
        zip.flush();
    }

    private static void put(ZipOutputStream zip, String path, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String documentXml(DocumentModel document) {
        StringBuilder body = new StringBuilder();
        for (DocumentModel.Block block : document.getBlocks()) {
            if (block.getType() == DocumentModel.BlockType.LIST) {
                for (DetectedLine line : block.getLines()) {
                    paragraph(body, "ListParagraph", line.getText());
                }
            } else {
                String style = block.getType() == DocumentModel.BlockType.HEADING
                        ? "Heading1" : "Normal";
                paragraph(body, style, block.joinedText());
            }
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:body>" + body
                + "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
                + "<w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\"/></w:sectPr>"
                + "</w:body></w:document>";
    }

    private static void paragraph(StringBuilder output, String style, String text) {
        output.append("<w:p><w:pPr><w:pStyle w:val=\"")
                .append(style)
                .append("\"/></w:pPr><w:r><w:t xml:space=\"preserve\">")
                .append(escapeXml(text))
                .append("</w:t></w:r></w:p>");
    }

    private static String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String contentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>"
                + "</Types>";
    }

    private static String packageRelationships() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                + "</Relationships>";
    }

    private static String documentRelationships() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                + "</Relationships>";
    }

    private static String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>"
                + "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/>"
                + "<w:basedOn w:val=\"Normal\"/><w:qFormat/><w:rPr><w:b/><w:sz w:val=\"32\"/></w:rPr></w:style>"
                + "<w:style w:type=\"paragraph\" w:styleId=\"ListParagraph\"><w:name w:val=\"List Paragraph\"/>"
                + "<w:basedOn w:val=\"Normal\"/><w:pPr><w:ind w:left=\"720\"/></w:pPr></w:style>"
                + "</w:styles>";
    }
}
