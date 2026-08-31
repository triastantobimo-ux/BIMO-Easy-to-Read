package com.bimo.easytoread.core;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class XlsxExporter {
    private static final Pattern PERCENT = Pattern.compile("^([()\\-+0-9.,\\s]+)%$");
    private static final Pattern RUPIAH = Pattern.compile("^(?i:Rp\\.?|IDR)\\s*([()\\-+0-9.,\\s]+)$");
    private static final Pattern NUMBER = Pattern.compile("^[()\\-+0-9.,\\s]+$");
    private static final Pattern LEADING_ZERO = Pattern.compile("^[+-]?0\\d+$");
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.forLanguageTag("id-ID")),
            DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.forLanguageTag("id-ID")),
            DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ROOT),
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT),
            DateTimeFormatter.ISO_LOCAL_DATE
    };

    private XlsxExporter() {}

    public static void write(DocumentModel document, OutputStream target) throws IOException {
        TableModel table = document != null && document.getWorksheet() != null
                ? document.getWorksheet().toTableModel()
                : TableDetector.detect(document);
        List<Sheet> sheets = new ArrayList<>();
        if (table.isDetected()) {
            sheets.add(Sheet.table("Table", table));
        } else {
            sheets.add(Sheet.text("OCR Text", textRows(document)));
        }

        ZipOutputStream zip = new ZipOutputStream(target, StandardCharsets.UTF_8);
        put(zip, "[Content_Types].xml", contentTypes(sheets.size()));
        put(zip, "_rels/.rels", packageRelationships());
        put(zip, "docProps/app.xml", appProperties(sheets));
        put(zip, "docProps/core.xml", coreProperties());
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
            int max = column == 0 ? 12 : 8;
            for (List<String> row : sheet.rows) {
                String value = column < row.size() ? row.get(column) : "";
                max = Math.max(max, Math.min(column == 0 ? 48 : 34, value.length() + 2));
            }
            cols.append("<col min='").append(column + 1)
                    .append("' max='").append(column + 1)
                    .append("' width='").append(max)
                    .append("' customWidth='1'/>");
        }
        cols.append("</cols>");

        boolean[] identifierColumns = identifierColumns(sheet, columns);
        StringBuilder rows = new StringBuilder();
        List<String> mergeRefs = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < sheet.rows.size(); rowIndex++) {
            List<String> row = sheet.rows.get(rowIndex);
            TableModel.RowRole role = sheet.role(rowIndex);
            int height = rowHeight(role);
            rows.append("<row r='").append(rowIndex + 1)
                    .append("' ht='").append(height)
                    .append("' customHeight='1'>");

            int nonEmpty = nonEmpty(row);
            boolean mergeAcross = sheet.table
                    && columns > 1
                    && nonEmpty <= 2
                    && (role == TableModel.RowRole.TITLE || role == TableModel.RowRole.SUBTITLE);
            if (mergeAcross) {
                mergeRefs.add("A" + (rowIndex + 1) + ":" + columnName(columns) + (rowIndex + 1));
            }

            for (int column = 0; column < columns; column++) {
                String value = column < row.size() ? row.get(column) : "";
                if (mergeAcross && column > 0) value = "";
                CellData cell = parseCell(value, identifierColumns[column], role, sheet.table);
                String reference = columnName(column + 1) + (rowIndex + 1);
                rows.append(cellXml(reference, cell));
            }
            rows.append("</row>");
        }

        int header = sheet.headerRowIndex;
        String autoFilter = sheet.table && header >= 0 && header + 1 < sheet.rows.size()
                ? "<autoFilter ref='A" + (header + 1) + ":" + columnName(columns) + sheet.rows.size() + "'/>"
                : "";
        String views = sheet.table && header >= 0
                ? "<sheetViews><sheetView workbookViewId='0' showGridLines='0'><pane ySplit='"
                        + (header + 1) + "' topLeftCell='A" + (header + 2)
                        + "' activePane='bottomLeft' state='frozen'/></sheetView></sheetViews>"
                : "<sheetViews><sheetView workbookViewId='0' showGridLines='0'/></sheetViews>";
        String merges = mergeRefs.isEmpty() ? "" : mergeCellsXml(mergeRefs);

        return "<?xml version='1.0' encoding='UTF-8' standalone='yes'?>"
                + "<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'>"
                + "<dimension ref='A1:" + columnName(columns) + Math.max(1, sheet.rows.size()) + "'/>"
                + views + "<sheetFormatPr defaultRowHeight='18'/>" + cols
                + "<sheetData>" + rows + "</sheetData>" + autoFilter + merges
                + "<pageMargins left='0.3' right='0.3' top='0.5' bottom='0.5' header='0.2' footer='0.2'/>"
                + "</worksheet>";
    }

    private static boolean[] identifierColumns(Sheet sheet, int columns) {
        boolean[] result = new boolean[columns];
        if (!sheet.table || sheet.headerRowIndex < 0 || sheet.headerRowIndex >= sheet.rows.size()) {
            return result;
        }
        List<String> header = sheet.rows.get(sheet.headerRowIndex);
        for (int column = 0; column < columns; column++) {
            String value = column < header.size() ? header.get(column).toLowerCase(Locale.ROOT) : "";
            result[column] = value.contains(" id") || value.endsWith("id")
                    || value.contains("code") || value.contains("kode")
                    || value.equals("no") || value.startsWith("no.")
                    || value.contains("identifier");
        }
        return result;
    }

    private static CellData parseCell(
            String raw,
            boolean identifierColumn,
            TableModel.RowRole role,
            boolean table
    ) {
        String value = raw == null ? "" : raw.trim();
        if (!table) return CellData.text(value, 0);
        if (value.isEmpty()) return CellData.text("", baseTextStyle(role));
        if (role == TableModel.RowRole.TITLE
                || role == TableModel.RowRole.SUBTITLE
                || role == TableModel.RowRole.HEADER
                || identifierColumn
                || LEADING_ZERO.matcher(value).matches()) {
            return CellData.text(value, baseTextStyle(role));
        }

        LocalDate date = parseDate(value);
        if (date != null) {
            long serial = ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 30), date);
            return CellData.number(Long.toString(serial), dateStyle(role));
        }

        Matcher percentage = PERCENT.matcher(value);
        if (percentage.matches()) {
            ParsedNumber parsed = parseNumber(percentage.group(1), true);
            if (parsed != null) {
                BigDecimal fraction = parsed.value.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
                        .stripTrailingZeros();
                return CellData.number(fraction.toPlainString(), percentStyle(role, parsed.decimals));
            }
        }

        Matcher rupiah = RUPIAH.matcher(value);
        if (rupiah.matches()) {
            ParsedNumber parsed = parseNumber(rupiah.group(1), false);
            if (parsed != null) {
                return CellData.number(parsed.value.toPlainString(), currencyStyle(role, parsed.decimals));
            }
        }

        if (NUMBER.matcher(value).matches()) {
            ParsedNumber parsed = parseNumber(value, false);
            if (parsed != null) {
                return CellData.number(
                        parsed.value.toPlainString(),
                        parsed.decimals == 0 ? integerStyle(role) : decimalStyle(role, parsed.decimals)
                );
            }
        }
        return CellData.text(value, baseTextStyle(role));
    }

    private static ParsedNumber parseNumber(String source, boolean decimalPreferred) {
        String value = source.replace(" ", "").trim();
        boolean negative = value.startsWith("(") && value.endsWith(")");
        if (negative) value = value.substring(1, value.length() - 1);
        value = value.replace("+", "");
        if (value.startsWith("-")) {
            negative = true;
            value = value.substring(1);
        }
        if (value.isEmpty() || !value.matches("[0-9.,]+")) return null;

        int lastDot = value.lastIndexOf('.');
        int lastComma = value.lastIndexOf(',');
        char decimalSeparator = 0;
        if (lastDot >= 0 && lastComma >= 0) {
            decimalSeparator = lastDot > lastComma ? '.' : ',';
        } else {
            int position = Math.max(lastDot, lastComma);
            if (position >= 0) {
                char separator = value.charAt(position);
                int occurrences = count(value, separator);
                int trailing = value.length() - position - 1;
                if (occurrences > 1 && allGroupingSegments(value, separator)) {
                    decimalSeparator = 0;
                } else if (decimalPreferred || trailing > 0 && trailing <= 2) {
                    decimalSeparator = separator;
                } else if (occurrences > 1 && !allGroupingSegments(value, separator)) {
                    decimalSeparator = separator;
                }
            }
        }

        int decimals = 0;
        if (decimalSeparator != 0) {
            if (count(value, decimalSeparator) > 1) return null;
            decimals = value.length() - value.lastIndexOf(decimalSeparator) - 1;
        }
        StringBuilder normalized = new StringBuilder();
        boolean decimalWritten = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isDigit(character)) normalized.append(character);
            else if (character == decimalSeparator && !decimalWritten) {
                normalized.append('.');
                decimalWritten = true;
            }
        }
        try {
            BigDecimal number = new BigDecimal(normalized.toString());
            if (negative) number = number.negate();
            return new ParsedNumber(number, Math.min(4, Math.max(0, decimals)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean allGroupingSegments(String value, char separator) {
        String[] parts = value.split(Pattern.quote(String.valueOf(separator)));
        if (parts.length < 2) return false;
        for (int index = 1; index < parts.length; index++) if (parts[index].length() != 3) return false;
        return true;
    }

    private static int count(String value, char character) {
        int result = 0;
        for (int index = 0; index < value.length(); index++) if (value.charAt(index) == character) result++;
        return result;
    }

    private static LocalDate parseDate(String value) {
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next visible date format.
            }
        }
        return null;
    }

    private static int baseTextStyle(TableModel.RowRole role) {
        if (role == TableModel.RowRole.TITLE) return 1;
        if (role == TableModel.RowRole.SUBTITLE) return 2;
        if (role == TableModel.RowRole.HEADER) return 3;
        if (role == TableModel.RowRole.SUMMARY) return 4;
        return 5;
    }

    private static int integerStyle(TableModel.RowRole role) { return role == TableModel.RowRole.SUMMARY ? 20 : 6; }
    private static int decimalStyle(TableModel.RowRole role, int decimals) {
        int safe = Math.min(4, Math.max(1, decimals));
        return role == TableModel.RowRole.SUMMARY ? 20 + safe : 6 + safe;
    }
    private static int percentStyle(TableModel.RowRole role, int decimals) {
        int safe = Math.min(4, Math.max(0, decimals));
        return role == TableModel.RowRole.SUMMARY ? 25 + safe : 11 + safe;
    }
    private static int currencyStyle(TableModel.RowRole role, int decimals) {
        int safe = Math.min(2, Math.max(0, decimals));
        return role == TableModel.RowRole.SUMMARY ? 30 + safe : 16 + safe;
    }
    private static int dateStyle(TableModel.RowRole role) { return role == TableModel.RowRole.SUMMARY ? 33 : 19; }

    private static String cellXml(String reference, CellData cell) {
        if (cell.numeric) {
            return "<c r='" + reference + "' s='" + cell.style + "'><v>"
                    + escapeXml(cell.value) + "</v></c>";
        }
        return "<c r='" + reference + "' t='inlineStr' s='" + cell.style
                + "'><is><t xml:space='preserve'>" + escapeXml(cell.value) + "</t></is></c>";
    }

    private static int rowHeight(TableModel.RowRole role) {
        if (role == TableModel.RowRole.TITLE) return 30;
        if (role == TableModel.RowRole.SUBTITLE) return 27;
        if (role == TableModel.RowRole.HEADER) return 32;
        if (role == TableModel.RowRole.BLANK) return 12;
        return 24;
    }

    private static int nonEmpty(List<String> row) {
        int count = 0;
        for (String value : row) if (value != null && !value.trim().isEmpty()) count++;
        return count;
    }

    private static String mergeCellsXml(List<String> refs) {
        StringBuilder output = new StringBuilder("<mergeCells count='").append(refs.size()).append("'>");
        for (String ref : refs) output.append("<mergeCell ref='").append(ref).append("'/>");
        return output.append("</mergeCells>").toString();
    }

    private static String workbookXml(List<Sheet> sheets) {
        StringBuilder output = new StringBuilder(
                "<?xml version='1.0' encoding='UTF-8' standalone='yes'?>"
                        + "<workbook xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main' "
                        + "xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships'>"
                        + "<workbookPr date1904='0'/><sheets>"
        );
        for (int index = 0; index < sheets.size(); index++) {
            output.append("<sheet name='").append(escapeXml(sheets.get(index).name))
                    .append("' sheetId='").append(index + 1)
                    .append("' r:id='rId").append(index + 1).append("'/>");
        }
        return output.append("</sheets><calcPr calcId='0' fullCalcOnLoad='1'/></workbook>").toString();
    }

    private static String workbookRelationships(int sheetCount) {
        StringBuilder output = new StringBuilder(
                "<?xml version='1.0' encoding='UTF-8'?>"
                        + "<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>"
        );
        for (int index = 0; index < sheetCount; index++) {
            output.append("<Relationship Id='rId").append(index + 1)
                    .append("' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet' "
                            + "Target='worksheets/sheet").append(index + 1).append(".xml'/>");
        }
        output.append("<Relationship Id='rId").append(sheetCount + 1)
                .append("' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles' "
                        + "Target='styles.xml'/>");
        return output.append("</Relationships>").toString();
    }

    private static String contentTypes(int sheetCount) {
        StringBuilder output = new StringBuilder(
                "<?xml version='1.0' encoding='UTF-8'?>"
                        + "<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'>"
                        + "<Default Extension='rels' ContentType='application/vnd.openxmlformats-package.relationships+xml'/>"
                        + "<Default Extension='xml' ContentType='application/xml'/>"
                        + "<Override PartName='/docProps/app.xml' ContentType='application/vnd.openxmlformats-officedocument.extended-properties+xml'/>"
                        + "<Override PartName='/docProps/core.xml' ContentType='application/vnd.openxmlformats-package.core-properties+xml'/>"
                        + "<Override PartName='/xl/workbook.xml' ContentType='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml'/>"
                        + "<Override PartName='/xl/styles.xml' ContentType='application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml'/>"
        );
        for (int index = 0; index < sheetCount; index++) {
            output.append("<Override PartName='/xl/worksheets/sheet").append(index + 1)
                    .append(".xml' ContentType='application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml'/>");
        }
        return output.append("</Types>").toString();
    }

    private static String stylesXml() {
        StringBuilder xfs = new StringBuilder();
        xfs.append(xf(0, 0, 0, 0, "left"));
        xfs.append(xf(2, 2, 1, 0, "left"));
        xfs.append(xf(3, 3, 1, 0, "left"));
        xfs.append(xf(4, 4, 1, 0, "center"));
        xfs.append(xf(1, 5, 1, 0, "left"));
        xfs.append(xf(0, 0, 1, 0, "left"));
        for (int numFmt = 164; numFmt <= 177; numFmt++) xfs.append(xf(0, 0, 1, numFmt, "right"));
        for (int numFmt = 164; numFmt <= 177; numFmt++) xfs.append(xf(1, 5, 1, numFmt, "right"));

        return "<?xml version='1.0' encoding='UTF-8' standalone='yes'?>"
                + "<styleSheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'>"
                + "<numFmts count='14'>"
                + numFmt(164, "#,##0") + numFmt(165, "#,##0.0") + numFmt(166, "#,##0.00")
                + numFmt(167, "#,##0.000") + numFmt(168, "#,##0.0000")
                + numFmt(169, "0%") + numFmt(170, "0.0%") + numFmt(171, "0.00%")
                + numFmt(172, "0.000%") + numFmt(173, "0.0000%")
                + numFmt(174, "&quot;Rp&quot; #,##0") + numFmt(175, "&quot;Rp&quot; #,##0.0")
                + numFmt(176, "&quot;Rp&quot; #,##0.00") + numFmt(177, "dd mmm yyyy")
                + "</numFmts>"
                + "<fonts count='5'>"
                + "<font><sz val='11'/><color rgb='FF182036'/><name val='Aptos'/></font>"
                + "<font><b/><sz val='11'/><color rgb='FF182036'/><name val='Aptos'/></font>"
                + "<font><b/><sz val='16'/><color rgb='FFFFFFFF'/><name val='Aptos Display'/></font>"
                + "<font><i/><sz val='10'/><color rgb='FF334155'/><name val='Aptos'/></font>"
                + "<font><b/><sz val='11'/><color rgb='FFFFFFFF'/><name val='Aptos'/></font>"
                + "</fonts>"
                + "<fills count='6'><fill><patternFill patternType='none'/></fill>"
                + "<fill><patternFill patternType='gray125'/></fill>"
                + fill("FF1F4E78") + fill("FFDCE6F1") + fill("FF287B78") + fill("FFEAF2F8")
                + "</fills>"
                + "<borders count='2'><border/><border>"
                + "<left style='thin'><color rgb='FFB7C3D0'/></left>"
                + "<right style='thin'><color rgb='FFB7C3D0'/></right>"
                + "<top style='thin'><color rgb='FFB7C3D0'/></top>"
                + "<bottom style='thin'><color rgb='FFB7C3D0'/></bottom><diagonal/>"
                + "</border></borders>"
                + "<cellStyleXfs count='1'><xf numFmtId='0' fontId='0' fillId='0' borderId='0'/></cellStyleXfs>"
                + "<cellXfs count='34'>" + xfs + "</cellXfs>"
                + "<cellStyles count='1'><cellStyle name='Normal' xfId='0' builtinId='0'/></cellStyles>"
                + "</styleSheet>";
    }

    private static String xf(int font, int fill, int border, int numFmt, String horizontal) {
        return "<xf numFmtId='" + numFmt + "' fontId='" + font + "' fillId='" + fill
                + "' borderId='" + border + "' xfId='0' applyAlignment='1' applyNumberFormat='1'>"
                + "<alignment horizontal='" + horizontal + "' vertical='center' wrapText='1'/></xf>";
    }

    private static String numFmt(int id, String code) {
        return "<numFmt numFmtId='" + id + "' formatCode='" + code + "'/>";
    }

    private static String fill(String rgb) {
        return "<fill><patternFill patternType='solid'><fgColor rgb='" + rgb
                + "'/><bgColor indexed='64'/></patternFill></fill>";
    }

    private static String packageRelationships() {
        return "<?xml version='1.0' encoding='UTF-8'?>"
                + "<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>"
                + "<Relationship Id='rId1' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument' Target='xl/workbook.xml'/>"
                + "<Relationship Id='rId2' Type='http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties' Target='docProps/core.xml'/>"
                + "<Relationship Id='rId3' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties' Target='docProps/app.xml'/>"
                + "</Relationships>";
    }

    private static String appProperties(List<Sheet> sheets) {
        StringBuilder titles = new StringBuilder();
        for (Sheet sheet : sheets) titles.append("<vt:lpstr>").append(escapeXml(sheet.name)).append("</vt:lpstr>");
        return "<?xml version='1.0' encoding='UTF-8' standalone='yes'?>"
                + "<Properties xmlns='http://schemas.openxmlformats.org/officeDocument/2006/extended-properties' "
                + "xmlns:vt='http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes'>"
                + "<Application>BIMO Easy to Read</Application><HeadingPairs><vt:vector size='2' baseType='variant'>"
                + "<vt:variant><vt:lpstr>Worksheets</vt:lpstr></vt:variant><vt:variant><vt:i4>"
                + sheets.size() + "</vt:i4></vt:variant></vt:vector></HeadingPairs>"
                + "<TitlesOfParts><vt:vector size='" + sheets.size() + "' baseType='lpstr'>"
                + titles + "</vt:vector></TitlesOfParts></Properties>";
    }

    private static String coreProperties() {
        return "<?xml version='1.0' encoding='UTF-8' standalone='yes'?>"
                + "<cp:coreProperties xmlns:cp='http://schemas.openxmlformats.org/package/2006/metadata/core-properties' "
                + "xmlns:dc='http://purl.org/dc/elements/1.1/' xmlns:dcterms='http://purl.org/dc/terms/' "
                + "xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>"
                + "<dc:creator>BIMO Easy to Read</dc:creator><cp:lastModifiedBy>BIMO Easy to Read</cp:lastModifiedBy>"
                + "<dc:title>OCR reconstructed worksheet</dc:title></cp:coreProperties>";
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

    private static final class ParsedNumber {
        private final BigDecimal value;
        private final int decimals;

        ParsedNumber(BigDecimal value, int decimals) {
            this.value = value;
            this.decimals = decimals;
        }
    }

    private static final class CellData {
        private final String value;
        private final int style;
        private final boolean numeric;

        private CellData(String value, int style, boolean numeric) {
            this.value = value;
            this.style = style;
            this.numeric = numeric;
        }

        static CellData text(String value, int style) { return new CellData(value, style, false); }
        static CellData number(String value, int style) { return new CellData(value, style, true); }
    }

    private static final class Sheet {
        private final String name;
        private final List<List<String>> rows;
        private final List<TableModel.RowRole> roles;
        private final int headerRowIndex;
        private final boolean table;

        private Sheet(
                String name,
                List<List<String>> rows,
                List<TableModel.RowRole> roles,
                int headerRowIndex,
                boolean table
        ) {
            this.name = name;
            this.rows = rows;
            this.roles = roles;
            this.headerRowIndex = headerRowIndex;
            this.table = table;
        }

        static Sheet table(String name, TableModel model) {
            return new Sheet(name, model.getRows(), model.getRowRoles(), model.getPrimaryHeaderRowIndex(), true);
        }

        static Sheet text(String name, List<List<String>> rows) {
            return new Sheet(name, rows, Collections.emptyList(), -1, false);
        }

        TableModel.RowRole role(int row) {
            if (!table || row < 0 || row >= roles.size()) return TableModel.RowRole.DATA;
            return roles.get(row);
        }
    }
}

