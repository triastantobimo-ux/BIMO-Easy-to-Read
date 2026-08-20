package com.bimo.easytoread.core;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class XlsxExporter {
    private XlsxExporter() {}

    public static void write(DocumentModel document, OutputStream target) throws IOException {
        TableModel table = TableDetector.detect(document);
        List<Sheet> sheets = new ArrayList<>();
        if (table.isDetected()) sheets.add(new Sheet("Table", table.getRows(), true));
        sheets.add(new Sheet("OCR Text", textRows(document), false));

        ZipOutputStream zip = new ZipOutputStream(target, StandardCharsets.UTF_8);
        put(zip, "[Content_Types].xml", contentTypes(sheets.size()));
        put(zip, "_rels/.rels", packageRelationships());
        put(zip, "xl/workbook.xml", workbookXml(sheets));
        put(zip, "xl/_rels/workbook.xml.rels", workbookRelationships(sheets.size()));
        put(zip, "xl/styles.xml", stylesXml());
        for (int index = 0; index < sheets.size(); index++) {
            put(zip, "xl/worksheets/sheet" + (index + 1) + ".xml", sheetXml(sheets.get(index)));
        }
        zip.finish();
        zip.flush();
    }

    private static List<List<String>> textRows(DocumentModel document) {
        List<List<String>> rows = new ArrayList<>();
        if (document != null) {
            for (DocumentModel.Block block : document.getBlocks()) {
                for (DetectedLine line : block.getLines()) {
                    rows.add(Collections.singletonList(line.getText()));
                }
            }
        }
        if (rows.isEmpty()) rows.add(Collections.singletonList(""));
        return rows;
    }

    private static String sheetXml(Sheet sheet) {
        int columns = 1;
        for (List<String> row : sheet.rows) columns = Math.max(columns, row.size());

        StringBuilder cols = new StringBuilder("<cols>");
        for (int column = 0; column < columns; column++) {
            int max = 8;
            for (List<String> row : sheet.rows) {
                String value = column < row.size() ? row.get(column) : "";
                max = Math.max(max, Math.min(42, value.length() + 2));
            }
            cols.append("<col min='").append(column + 1)
                    .append("' max='").append(column + 1)
                    .append("' width='").append(max)
                    .append("' customWidth='1'/>");
        }
        cols.append("</cols>");

        StringBuilder rows = new StringBuilder();
        for (int rowIndex = 0; rowIndex < sheet.rows.size(); rowIndex++) {
            List<String> row = sheet.rows.get(rowIndex);
            rows.append("<row r='").append(rowIndex + 1).append("'>");
            for (int column = 0; column < columns; column++) {
                String value = column < row.size() ? row.get(column) : "";
                int style = sheet.table ? (rowIndex == 0 ? 1 : 2) : 0;
                rows.append("<c r='").append(columnName(column + 1))
                        .append(rowIndex + 1)
                        .append("' t='inlineStr' s='").append(style)
                        .append("'><is><t xml:space='preserve'>")
                        .append(escapeXml(value))
                        .append("</t></is></c>");
            }
            rows.append("</row>");
        }

        String autoFilter = sheet.table && sheet.rows.size() > 1
                ? "<autoFilter ref='A1:" + columnName(columns) + sheet.rows.size() + "'/>"
                : "";
        String views = sheet.table
                ? "<sheetViews><sheetView workbookViewId='0'><pane ySplit='1' topLeftCell='A2' activePane='bottomLeft' state='frozen'/></sheetView></sheetViews>"
                : "<sheetViews><sheetView workbookViewId='0'/></sheetViews>";

        return "<?xml version='1.0' encoding='UTF-8' standalone='yes'?>"
                + "<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'>"
                + views + cols + "<sheetData>" + rows + "</sheetData>" + autoFilter
                + "<pageMargins left='0.3' right='0.3' top='0.5' bottom='0.5' header='0.2' footer='0.2'/>"
                + "</worksheet>";
    }

    private static String workbookXml(List<Sheet> sheets) {
        StringBuilder output = new StringBuilder(
                "<?xml version='1.0' encoding='UTF-8' standalone='yes'?>"
                        + "<workbook xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main' "
                        + "xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships'>"
                        + "<sheets>"
        );
        for (int index = 0; index < sheets.size(); index++) {
            output.append("<sheet name='")
                    .append(escapeXml(sheets.get(index).name))
                    .append("' sheetId='").append(index + 1)
                    .append("' r:id='rId").append(index + 1).append("'/>");
        }
        return output.append("</sheets></workbook>").toString();
    }

    private static String workbookRelationships(int sheetCount) {
        StringBuilder output = new StringBuilder(
                "<?xml version='1.0' encoding='UTF-8'?>"
                        + "<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>"
        );
        for (int index = 0; index < sheetCount; index++) {
            output.append("<Relationship Id='rId").append(index + 1)
                    .append("' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet' ")
                    .append("Target='worksheets/sheet").append(index + 1).append(".xml'/>");
        }
        output.append("<Relationship Id='rId").append(sheetCount + 1)
                .append("' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles' ")
                .append("Target='styles.xml'/>");
        return output.append("</Relationships>").toString();
    }

    private static String contentTypes(int sheetCount) {
        StringBuilder output = new StringBuilder(
                "<?xml version='1.0' encoding='UTF-8'?>"
                        + "<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'>"
                        + "<Default Extension='rels' ContentType='application/vnd.openxmlformats-package.relationships+xml'/>"
                        + "<Default Extension='xml' ContentType='application/xml'/>"
                        + "<Override PartName='/xl/workbook.xml' ContentType='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml'/>"
                        + "<Override PartName='/xl/styles.xml' ContentType='application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml'/>"
        );
        for (int index = 0; index < sheetCount; index++) {
            output.append("<Override PartName='/xl/worksheets/sheet")
                    .append(index + 1)
                    .append(".xml' ContentType='application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml'/>");
        }
        return output.append("</Types>").toString();
    }

    private static String stylesXml() {
        return "<?xml version='1.0' encoding='UTF-8' standalone='yes'?>"
                + "<styleSheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'>"
                + "<fonts count='2'><font><sz val='11'/><name val='Aptos'/></font>"
                + "<font><b/><sz val='11'/><color rgb='FF111338'/><name val='Aptos'/></font></fonts>"
                + "<fills count='3'><fill><patternFill patternType='none'/></fill>"
                + "<fill><patternFill patternType='gray125'/></fill>"
                + "<fill><patternFill patternType='solid'><fgColor rgb='FFE5E0F0'/><bgColor indexed='64'/></patternFill></fill></fills>"
                + "<borders count='2'><border/><border>"
                + "<left style='thin'><color rgb='FFC9C3D8'/></left>"
                + "<right style='thin'><color rgb='FFC9C3D8'/></right>"
                + "<top style='thin'><color rgb='FFC9C3D8'/></top>"
                + "<bottom style='thin'><color rgb='FFC9C3D8'/></bottom><diagonal/>"
                + "</border></borders>"
                + "<cellStyleXfs count='1'><xf numFmtId='0' fontId='0' fillId='0' borderId='0'/></cellStyleXfs>"
                + "<cellXfs count='3'>"
                + "<xf numFmtId='0' fontId='0' fillId='0' borderId='0' xfId='0'/>"
                + "<xf numFmtId='0' fontId='1' fillId='2' borderId='1' xfId='0' applyAlignment='1'><alignment vertical='center' wrapText='1'/></xf>"
                + "<xf numFmtId='0' fontId='0' fillId='0' borderId='1' xfId='0' applyAlignment='1'><alignment vertical='top' wrapText='1'/></xf>"
                + "</cellXfs>"
                + "<cellStyles count='1'><cellStyle name='Normal' xfId='0' builtinId='0'/></cellStyles>"
                + "</styleSheet>";
    }

    private static String packageRelationships() {
        return "<?xml version='1.0' encoding='UTF-8'?>"
                + "<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>"
                + "<Relationship Id='rId1' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument' Target='xl/workbook.xml'/>"
                + "</Relationships>";
    }

    private static String columnName(int index) {
        StringBuilder name = new StringBuilder();
        int value = index;
        while (value > 0) {
            int remainder = (value - 1) % 26;
            name.insert(0, (char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return name.toString();
    }

    private static String escapeXml(String value) {
        if (value == null) return "";
        StringBuilder output = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '&') output.append("&amp;");
            else if (character == '<') output.append("&lt;");
            else if (character == '>') output.append("&gt;");
            else if (character == '"') output.append("&quot;");
            else if (character == '\'') output.append("&apos;");
            else if (character >= 0x20 || character == '\n' || character == '\r' || character == '\t') {
                output.append(character);
            }
        }
        return output.toString();
    }

    private static void put(ZipOutputStream zip, String path, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static final class Sheet {
        private final String name;
        private final List<List<String>> rows;
        private final boolean table;

        Sheet(String name, List<List<String>> rows, boolean table) {
            this.name = name;
            this.rows = rows;
            this.table = table;
        }
    }
}
