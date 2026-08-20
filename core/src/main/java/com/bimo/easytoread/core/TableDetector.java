package com.bimo.easytoread.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TableDetector {
    private static final Pattern ROW_PREFIX = Pattern.compile("^(\\d{1,4})\\s+(.+)$");
    private static final Pattern ROW_ONLY = Pattern.compile("^\\d{1,4}$");
    private static final Pattern INTEGER = Pattern.compile("^-?\\d{1,12}$");
    private static final Pattern PIPE_IDENTIFIER = Pattern.compile("^([A-Z]{2,12})\\|(\\d{2,12})$");
    private static final Pattern JOINED_IDENTIFIER = Pattern.compile("^([A-Z]{2,12})(\\d{2,12})$");
    private static final Pattern NUMBER_AND_STATUS = Pattern.compile("^([0O])\\s+(PASS|FAIL)$", Pattern.CASE_INSENSITIVE);

    private TableDetector() {}

    public static TableModel detect(DocumentModel document) {
        List<DetectedLine> source = flatten(document);
        if (source.size() < 4) return fallback(source, "insufficient geometry");

        source.sort(Comparator
                .comparingInt((DetectedLine line) -> line.getBox().getTop())
                .thenComparingInt(line -> line.getBox().getLeft()));
        double medianHeight = medianHeight(source);
        List<WorkingLine> working = new ArrayList<>();
        for (DetectedLine line : source) working.add(new WorkingLine(line));

        SpreadsheetFrame frame = detectSpreadsheetFrame(working, medianHeight);
        List<RowCluster> allRows = frame == null
                ? clusterRows(working, medianHeight)
                : buildSpreadsheetRows(working, frame, medianHeight);
        if (allRows.size() < 2) return fallback(source, "insufficient row structure");

        List<RowCluster> regularRows = selectBestRegularGroup(allRows, medianHeight);
        if (regularRows == null) return fallback(source, "no repeated rows");

        int pageLeft = Integer.MAX_VALUE;
        int pageRight = Integer.MIN_VALUE;
        for (RowCluster row : regularRows) {
            for (WorkingLine cell : row.cells) {
                if (cell.text.isEmpty()) continue;
                pageLeft = Math.min(pageLeft, cell.box.getLeft());
                pageRight = Math.max(pageRight, cell.box.getRight());
            }
        }
        int pageWidth = Math.max(1, pageRight - pageLeft);
        List<ColumnAnchor> anchors = buildAnchors(regularRows, medianHeight, pageWidth);
        if (anchors.size() < 2) return fallback(source, "column anchors were not repeatable");

        List<RowCluster> rowsToExport = frame == null ? regularRows : allRows;
        int typicalGap = typicalAnchorGap(anchors);
        int rightLimit = anchors.get(anchors.size() - 1).left
                + Math.max((int) Math.round(typicalGap * 0.60),
                        (int) Math.round(medianHeight * 4.0));

        List<List<String>> outputRows = new ArrayList<>();
        for (RowCluster row : rowsToExport) {
            outputRows.add(mapRow(row, anchors, medianHeight, pageWidth, rightLimit));
        }
        normalizeColumns(outputRows);
        trimEmptyEdges(outputRows);

        List<TableModel.RowRole> roles = classifyRows(outputRows);
        int headerRow = selectPrimaryHeader(roles, outputRows);
        if (headerRow > 0) {
            for (int index = 0; index < headerRow; index++) {
                if (roles.get(index) == TableModel.RowRole.DATA) {
                    roles.set(index, TableModel.RowRole.SUMMARY);
                }
            }
        }
        if (headerRow >= 0) {
            for (int index = headerRow + 1; index < roles.size(); index++) {
                if (roles.get(index) == TableModel.RowRole.SUMMARY) {
                    roles.set(index, TableModel.RowRole.DATA);
                }
            }
        }
        String note = frame == null
                ? "geometry-based table reconstruction"
                : "spreadsheet frame, row index, multi-section, and column reconstruction";
        return new TableModel(
                true,
                outputRows,
                anchors.size(),
                note,
                roles,
                headerRow
        );
    }

    private static List<DetectedLine> flatten(DocumentModel document) {
        List<DetectedLine> lines = new ArrayList<>();
        if (document == null) return lines;
        for (DocumentModel.Block block : document.getBlocks()) lines.addAll(block.getLines());
        return lines;
    }

    private static SpreadsheetFrame detectSpreadsheetFrame(
            List<WorkingLine> lines,
            double medianHeight
    ) {
        if (lines.size() < 8) return null;
        List<RowMarker> candidates = new ArrayList<>();
        for (WorkingLine line : lines) {
            ParsedMarker parsed = parseMarker(line.text);
            if (parsed == null) continue;
            candidates.add(new RowMarker(line, parsed.number, parsed.remainder));
        }
        if (candidates.size() < 5) return null;

        candidates.sort(Comparator.comparingInt(marker -> marker.line.box.getTop()));
        int count = candidates.size();
        int[] score = new int[count];
        int[] length = new int[count];
        int[] parent = new int[count];
        int xTolerance = Math.max(12, (int) Math.round(medianHeight * 2.4));
        int selected = -1;
        for (int index = 0; index < count; index++) {
            RowMarker current = candidates.get(index);
            score[index] = 10 + (current.remainder.isEmpty() ? 0 : 3);
            length[index] = 1;
            parent[index] = -1;
            for (int previous = 0; previous < index; previous++) {
                RowMarker before = candidates.get(previous);
                int rowDifference = current.rowNumber - before.rowNumber;
                double yDifference = current.line.centerY() - before.line.centerY();
                if (rowDifference < 1 || rowDifference > 6 || yDifference <= 0) continue;
                if (Math.abs(current.line.box.getLeft() - before.line.box.getLeft()) > xTolerance) {
                    continue;
                }
                double pitch = yDifference / rowDifference;
                if (pitch < medianHeight * 0.35 || pitch > medianHeight * 5.5) continue;

                int nextScore = score[previous] + 10
                        + (current.remainder.isEmpty() ? 0 : 3)
                        - Math.max(0, rowDifference - 1);
                if (nextScore > score[index]) {
                    score[index] = nextScore;
                    length[index] = length[previous] + 1;
                    parent[index] = previous;
                }
            }
            if (selected < 0 || score[index] > score[selected]) selected = index;
        }
        if (selected < 0 || length[selected] < 5) return null;

        List<RowMarker> sequence = new ArrayList<>();
        for (int index = selected; index >= 0; index = parent[index]) {
            sequence.add(candidates.get(index));
            if (parent[index] < 0) break;
        }
        Collections.reverse(sequence);

        int merged = 0;
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (RowMarker marker : sequence) {
            minimum = Math.min(minimum, marker.rowNumber);
            maximum = Math.max(maximum, marker.rowNumber);
            if (!marker.remainder.isEmpty()) merged++;
        }
        if ((merged < 3 && sequence.size() < 8)
                || maximum - minimum < 4
                || maximum - minimum > 300) {
            return null;
        }

        Map<DetectedLine, RowMarker> bySource = new HashMap<>();
        int top = Integer.MAX_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (RowMarker marker : sequence) {
            bySource.put(marker.line.source, marker);
            top = Math.min(top, marker.line.box.getTop());
            bottom = Math.max(bottom, marker.line.box.getBottom());
        }
        return new SpreadsheetFrame(sequence, bySource, top, bottom, minimum, maximum);
    }

    private static ParsedMarker parseMarker(String text) {
        Matcher prefix = ROW_PREFIX.matcher(text.trim());
        if (prefix.matches()) {
            return new ParsedMarker(Integer.parseInt(prefix.group(1)), prefix.group(2).trim());
        }
        if (ROW_ONLY.matcher(text.trim()).matches()) {
            return new ParsedMarker(Integer.parseInt(text.trim()), "");
        }
        return null;
    }

    private static List<RowCluster> buildSpreadsheetRows(
            List<WorkingLine> lines,
            SpreadsheetFrame frame,
            double medianHeight
    ) {
        Map<Integer, List<Double>> knownCenters = new HashMap<>();
        for (RowMarker marker : frame.markers) {
            knownCenters.computeIfAbsent(marker.rowNumber, ignored -> new ArrayList<>())
                    .add(marker.line.centerY());
        }

        List<RowCluster> rows = new ArrayList<>();
        Map<Integer, Double> centers = new HashMap<>();
        for (Map.Entry<Integer, List<Double>> entry : knownCenters.entrySet()) {
            centers.put(entry.getKey(), medianDouble(entry.getValue()));
        }
        for (int number = frame.minimumRow; number <= frame.maximumRow; number++) {
            if (!centers.containsKey(number)) centers.put(number, interpolateCenter(number, centers));
            RowCluster row = new RowCluster();
            row.sourceRowNumber = number;
            row.center = centers.get(number);
            rows.add(row);
        }

        double topLimit = frame.top - medianHeight * 0.8;
        double bottomLimit = frame.bottom + medianHeight * 0.9;
        for (WorkingLine original : lines) {
            if (original.centerY() < topLimit || original.centerY() > bottomLimit) continue;
            RowMarker marker = frame.bySource.get(original.source);
            WorkingLine line = marker == null ? original : stripMarker(original, marker, medianHeight);
            int rowIndex = nearestRow(line.centerY(), rows);
            if (rowIndex < 0) continue;
            if (!line.text.isEmpty()) rows.get(rowIndex).cells.add(line);
        }
        for (RowCluster row : rows) {
            row.cells.sort(Comparator.comparingInt(cell -> cell.box.getLeft()));
        }
        return rows;
    }

    private static WorkingLine stripMarker(
            WorkingLine original,
            RowMarker marker,
            double medianHeight
    ) {
        if (marker.remainder.isEmpty()) return new WorkingLine(original.source, "", original.box, Collections.emptyList());

        List<DetectedToken> remaining = new ArrayList<>();
        boolean markerRemoved = false;
        for (DetectedToken token : original.tokens) {
            if (!markerRemoved && token.getText().matches("0*" + marker.rowNumber)) {
                markerRemoved = true;
                continue;
            }
            remaining.add(token);
        }
        if (!remaining.isEmpty()) {
            Box box = remaining.get(0).getBox();
            for (int index = 1; index < remaining.size(); index++) box = box.union(remaining.get(index).getBox());
            return new WorkingLine(original.source, marker.remainder, box, remaining);
        }

        int digits = Integer.toString(marker.rowNumber).length();
        int shift = Math.max(4, (int) Math.round(medianHeight * (0.55 * digits + 0.45)));
        int left = Math.min(original.box.getRight() - 1, original.box.getLeft() + shift);
        return new WorkingLine(
                original.source,
                marker.remainder,
                new Box(left, original.box.getTop(), original.box.getRight(), original.box.getBottom()),
                Collections.emptyList()
        );
    }

    private static double interpolateCenter(int number, Map<Integer, Double> known) {
        Integer lower = null;
        Integer upper = null;
        for (Integer value : known.keySet()) {
            if (value < number && (lower == null || value > lower)) lower = value;
            if (value > number && (upper == null || value < upper)) upper = value;
        }
        if (lower != null && upper != null) {
            double ratio = (double) (number - lower) / (upper - lower);
            return known.get(lower) + (known.get(upper) - known.get(lower)) * ratio;
        }
        double pitch = typicalRowPitch(known);
        if (lower != null) return known.get(lower) + pitch * (number - lower);
        if (upper != null) return known.get(upper) - pitch * (upper - number);
        return number;
    }

    private static double typicalRowPitch(Map<Integer, Double> known) {
        List<Integer> keys = new ArrayList<>(known.keySet());
        Collections.sort(keys);
        List<Double> values = new ArrayList<>();
        for (int index = 1; index < keys.size(); index++) {
            int difference = keys.get(index) - keys.get(index - 1);
            if (difference > 0) {
                values.add((known.get(keys.get(index)) - known.get(keys.get(index - 1))) / difference);
            }
        }
        return values.isEmpty() ? 1.0 : Math.max(1.0, medianDouble(values));
    }

    private static int nearestRow(double center, List<RowCluster> rows) {
        int selected = -1;
        double distance = Double.MAX_VALUE;
        for (int index = 0; index < rows.size(); index++) {
            double next = Math.abs(center - rows.get(index).center);
            if (next < distance) {
                selected = index;
                distance = next;
            }
        }
        return selected;
    }

    private static List<RowCluster> clusterRows(List<WorkingLine> lines, double medianHeight) {
        List<RowCluster> rows = new ArrayList<>();
        double tolerance = Math.max(2.0, medianHeight * 0.70);
        for (WorkingLine line : lines) {
            RowCluster nearest = null;
            double bestDistance = Double.MAX_VALUE;
            for (RowCluster row : rows) {
                double distance = Math.abs(line.centerY() - row.center);
                if (distance <= tolerance && distance < bestDistance) {
                    nearest = row;
                    bestDistance = distance;
                }
            }
            if (nearest == null) {
                nearest = new RowCluster();
                rows.add(nearest);
            }
            nearest.add(line);
        }
        rows.sort(Comparator.comparingDouble(row -> row.center));
        return rows;
    }

    private static List<RowCluster> selectBestRegularGroup(
            List<RowCluster> rows,
            double medianHeight
    ) {
        List<List<RowCluster>> groups = new ArrayList<>();
        List<RowCluster> active = new ArrayList<>();
        RowCluster previous = null;
        for (RowCluster row : rows) {
            List<WorkingLine> candidates = candidateCells(row, medianHeight);
            boolean qualifies = candidates.size() >= 2
                    && row.horizontalSpread() >= medianHeight * 3.0;
            if (!qualifies) {
                if (!active.isEmpty()) {
                    groups.add(active);
                    active = new ArrayList<>();
                }
                previous = row;
                continue;
            }
            if (previous != null && !active.isEmpty()
                    && row.top() - previous.bottom() > medianHeight * 4.0) {
                groups.add(active);
                active = new ArrayList<>();
            }
            active.add(row);
            previous = row;
        }
        if (!active.isEmpty()) groups.add(active);

        List<RowCluster> best = null;
        int bestScore = 0;
        for (List<RowCluster> group : groups) {
            int score = group.size() * 10;
            for (RowCluster row : group) {
                score += Math.min(12, candidateCells(row, medianHeight).size());
            }
            if (group.size() >= 2 && score > bestScore) {
                best = group;
                bestScore = score;
            }
        }
        return best;
    }

    private static List<ColumnAnchor> buildAnchors(
            List<RowCluster> rows,
            double medianHeight,
            int pageWidth
    ) {
        int tolerance = Math.max(7, (int) Math.round(medianHeight * 1.35));
        List<ColumnAnchor> anchors = new ArrayList<>();
        for (RowCluster row : rows) {
            for (WorkingLine cell : candidateCells(row, medianHeight)) {
                if (cell.text.isEmpty() || cell.box.width() > pageWidth * 0.60) continue;
                ColumnAnchor nearest = null;
                int bestDistance = Integer.MAX_VALUE;
                for (ColumnAnchor anchor : anchors) {
                    int distance = Math.abs(cell.box.getLeft() - anchor.left);
                    if (distance <= tolerance && distance < bestDistance) {
                        nearest = anchor;
                        bestDistance = distance;
                    }
                }
                if (nearest == null) {
                    nearest = new ColumnAnchor(cell.box.getLeft());
                    anchors.add(nearest);
                } else {
                    nearest.left = Math.round((nearest.left * nearest.count + cell.box.getLeft())
                            / (float) (nearest.count + 1));
                }
                nearest.count++;
            }
        }
        int minimumRepeat = Math.max(2, (int) Math.ceil(rows.size() * 0.18));
        anchors.removeIf(anchor -> anchor.count < minimumRepeat);
        anchors.sort(Comparator.comparingInt(anchor -> anchor.left));

        if (!anchors.isEmpty()) {
            int currentRight = anchors.get(anchors.size() - 1).left;
            for (RowCluster row : rows) {
                StringBuilder rowText = new StringBuilder();
                for (WorkingLine line : row.cells) append(rowText, line.text);
                if (headerKeywordCount(rowText.toString()) < 2) continue;
                for (WorkingLine cell : candidateCells(row, medianHeight)) {
                    if (cell.box.getLeft() <= currentRight + tolerance) continue;
                    if (cell.box.width() > pageWidth * 0.35) continue;
                    boolean nearExisting = false;
                    for (ColumnAnchor anchor : anchors) {
                        if (Math.abs(cell.box.getLeft() - anchor.left) <= tolerance) {
                            nearExisting = true;
                            break;
                        }
                    }
                    if (!nearExisting) {
                        anchors.add(new ColumnAnchor(cell.box.getLeft()));
                        currentRight = Math.max(currentRight, cell.box.getLeft());
                    }
                }
            }
            anchors.sort(Comparator.comparingInt(anchor -> anchor.left));
        }
        if (anchors.size() > 12) {
            List<ColumnAnchor> strongest = new ArrayList<>(anchors);
            strongest.sort(Comparator.comparingInt((ColumnAnchor anchor) -> -anchor.count));
            strongest = new ArrayList<>(strongest.subList(0, 12));
            strongest.sort(Comparator.comparingInt(anchor -> anchor.left));
            anchors = strongest;
        }
        return anchors;
    }

    private static List<String> mapRow(
            RowCluster row,
            List<ColumnAnchor> anchors,
            double medianHeight,
            int pageWidth,
            int rightLimit
    ) {
        List<StringBuilder> values = new ArrayList<>();
        for (int index = 0; index < anchors.size(); index++) values.add(new StringBuilder());

        List<WorkingLine> visible = new ArrayList<>();
        int leftLimit = anchors.get(0).left - Math.max(12, typicalAnchorGap(anchors) / 2);
        for (WorkingLine cell : candidateCells(row, medianHeight)) {
            if (!cell.text.isEmpty() && cell.box.getRight() >= leftLimit && cell.box.getLeft() <= rightLimit) {
                visible.add(cell);
            }
        }
        visible.sort(Comparator.comparingInt(cell -> cell.box.getLeft()));

        int maximumWidth = 0;
        for (WorkingLine cell : visible) maximumWidth = Math.max(maximumWidth, cell.box.width());
        boolean spanning = visible.size() <= 2 && maximumWidth >= pageWidth * 0.55;
        if (spanning) {
            StringBuilder joined = values.get(0);
            for (WorkingLine cell : visible) append(joined, cell.text);
            return finalizeRow(values);
        }

        for (WorkingLine cell : visible) {
            Map<Integer, StringBuilder> split = splitByTokens(cell, anchors, medianHeight);
            if (split.size() >= 2) {
                for (Map.Entry<Integer, StringBuilder> entry : split.entrySet()) {
                    append(values.get(entry.getKey()), entry.getValue().toString());
                }
                continue;
            }

            int column = nearestAnchor(cell.box.getLeft(), anchors);
            Matcher status = NUMBER_AND_STATUS.matcher(cell.text.trim());
            if (status.matches() && column + 1 < values.size()) {
                append(values.get(column), "0");
                append(values.get(column + 1), status.group(2).toUpperCase(Locale.ROOT));
            } else {
                append(values.get(column), cell.text);
            }
        }
        return finalizeRow(values);
    }

    private static List<WorkingLine> candidateCells(RowCluster row, double medianHeight) {
        List<WorkingLine> cells = new ArrayList<>();
        for (WorkingLine line : row.cells) cells.addAll(segmentLine(line, medianHeight));
        return cells;
    }

    private static List<WorkingLine> segmentLine(WorkingLine line, double medianHeight) {
        if (line.tokens.size() < 2) return Collections.singletonList(line);

        List<DetectedToken> tokens = new ArrayList<>();
        for (DetectedToken token : line.tokens) {
            if (token != null && !token.getText().trim().isEmpty()) tokens.add(token);
        }
        if (tokens.size() < 2) return Collections.singletonList(line);
        tokens.sort(Comparator.comparingInt(token -> token.getBox().getLeft()));

        double splitGap = Math.max(6.0, medianHeight * 0.75);
        List<List<DetectedToken>> groups = new ArrayList<>();
        List<DetectedToken> active = new ArrayList<>();
        active.add(tokens.get(0));
        for (int index = 1; index < tokens.size(); index++) {
            DetectedToken previous = tokens.get(index - 1);
            DetectedToken token = tokens.get(index);
            int gap = token.getBox().getLeft() - previous.getBox().getRight();
            if (gap > splitGap) {
                groups.add(active);
                active = new ArrayList<>();
            }
            active.add(token);
        }
        groups.add(active);
        if (groups.size() < 2) return Collections.singletonList(line);

        List<WorkingLine> segments = new ArrayList<>();
        for (List<DetectedToken> group : groups) {
            StringBuilder text = new StringBuilder();
            Box box = group.get(0).getBox();
            for (int index = 0; index < group.size(); index++) {
                DetectedToken token = group.get(index);
                append(text, token.getText());
                if (index > 0) box = box.union(token.getBox());
            }
            segments.add(new WorkingLine(line.source, text.toString(), box, group));
        }
        return segments;
    }

    private static Map<Integer, StringBuilder> splitByTokens(
            WorkingLine line,
            List<ColumnAnchor> anchors,
            double medianHeight
    ) {
        Map<Integer, StringBuilder> output = new HashMap<>();
        if (line.tokens.size() < 2) return output;

        List<DetectedToken> tokens = new ArrayList<>(line.tokens);
        tokens.sort(Comparator.comparingInt(token -> token.getBox().getLeft()));
        int changes = 0;
        int previousColumn = nearestAnchor(tokens.get(0).getBox().getLeft(), anchors);
        DetectedToken previousToken = tokens.get(0);
        for (int index = 1; index < tokens.size(); index++) {
            DetectedToken token = tokens.get(index);
            int column = nearestAnchor(token.getBox().getLeft(), anchors);
            int gap = token.getBox().getLeft() - previousToken.getBox().getRight();
            if (column != previousColumn && gap >= medianHeight * 0.35) changes++;
            previousColumn = column;
            previousToken = token;
        }
        if (changes == 0) return output;

        for (DetectedToken token : tokens) {
            int column = nearestAnchor(token.getBox().getLeft(), anchors);
            StringBuilder value = output.computeIfAbsent(column, ignored -> new StringBuilder());
            append(value, token.getText());
        }
        return output;
    }

    private static List<String> finalizeRow(List<StringBuilder> values) {
        List<String> row = new ArrayList<>();
        for (StringBuilder value : values) row.add(value.toString().trim());
        return row;
    }

    private static void append(StringBuilder target, String text) {
        String safe = text == null ? "" : text.trim();
        if (safe.isEmpty()) return;
        if (target.length() > 0) target.append(' ');
        target.append(safe);
    }

    private static void normalizeColumns(List<List<String>> rows) {
        if (rows.isEmpty()) return;
        int columns = rows.get(0).size();
        for (int column = 0; column < columns; column++) {
            int pipeIdentifiers = 0;
            int integers = 0;
            for (List<String> row : rows) {
                String value = row.get(column).trim();
                if (PIPE_IDENTIFIER.matcher(value).matches()) pipeIdentifiers++;
                if (INTEGER.matcher(value).matches()) integers++;
            }
            for (List<String> row : rows) {
                String value = row.get(column).trim();
                if (pipeIdentifiers >= 3) {
                    Matcher joined = JOINED_IDENTIFIER.matcher(value);
                    if (joined.matches()) row.set(column, joined.group(1) + "|" + joined.group(2));
                }
                if (integers >= 3 && (value.equalsIgnoreCase("O") || value.equalsIgnoreCase("o"))) {
                    row.set(column, "0");
                }
            }
        }
    }

    private static void trimEmptyEdges(List<List<String>> rows) {
        while (!rows.isEmpty() && nonEmpty(rows.get(rows.size() - 1)) == 0) {
            rows.remove(rows.size() - 1);
        }
        while (!rows.isEmpty() && nonEmpty(rows.get(0)) == 0) rows.remove(0);
    }

    private static List<TableModel.RowRole> classifyRows(List<List<String>> rows) {
        List<TableModel.RowRole> roles = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            int nonEmpty = 0;
            StringBuilder joined = new StringBuilder();
            for (String value : row) {
                if (!value.trim().isEmpty()) {
                    nonEmpty++;
                    append(joined, value);
                }
            }
            String text = joined.toString();
            if (nonEmpty == 0) roles.add(TableModel.RowRole.BLANK);
            else if (index == 0 && nonEmpty <= 2) roles.add(TableModel.RowRole.TITLE);
            else if (index <= 2 && nonEmpty <= 2 && text.length() >= 24) roles.add(TableModel.RowRole.SUBTITLE);
            else if (nonEmpty >= 2 && headerKeywordCount(text) >= 2) roles.add(TableModel.RowRole.HEADER);
            else if (index <= 4 && nonEmpty >= 2) roles.add(TableModel.RowRole.SUMMARY);
            else roles.add(TableModel.RowRole.DATA);
        }
        return roles;
    }

    private static int headerKeywordCount(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        String[] keywords = {
                "control", "actual", "expected", "status", "analysis id", "domain",
                "code", "name", "count", "period", "date", "amount", "quantity",
                "description", "category", "total", "latest"
        };
        int count = 0;
        for (String keyword : keywords) if (normalized.contains(keyword)) count++;
        return count;
    }

    private static int selectPrimaryHeader(
            List<TableModel.RowRole> roles,
            List<List<String>> rows
    ) {
        int selected = -1;
        int bestFollowingData = -1;
        for (int index = 0; index < roles.size(); index++) {
            if (roles.get(index) != TableModel.RowRole.HEADER) continue;
            int following = 0;
            for (int next = index + 1; next < roles.size(); next++) {
                if (roles.get(next) == TableModel.RowRole.DATA && nonEmpty(rows.get(next)) >= 2) following++;
                else if (roles.get(next) == TableModel.RowRole.HEADER) break;
            }
            if (following >= bestFollowingData) {
                selected = index;
                bestFollowingData = following;
            }
        }
        return selected;
    }

    private static int nonEmpty(List<String> row) {
        int count = 0;
        for (String value : row) if (!value.trim().isEmpty()) count++;
        return count;
    }

    private static int nearestAnchor(int left, List<ColumnAnchor> anchors) {
        int selected = 0;
        int distance = Integer.MAX_VALUE;
        for (int index = 0; index < anchors.size(); index++) {
            int next = Math.abs(left - anchors.get(index).left);
            if (next < distance) {
                selected = index;
                distance = next;
            }
        }
        return selected;
    }

    private static int typicalAnchorGap(List<ColumnAnchor> anchors) {
        if (anchors.size() < 2) return 40;
        List<Integer> gaps = new ArrayList<>();
        for (int index = 1; index < anchors.size(); index++) {
            gaps.add(anchors.get(index).left - anchors.get(index - 1).left);
        }
        Collections.sort(gaps);
        return Math.max(1, gaps.get(gaps.size() / 2));
    }

    private static TableModel fallback(List<DetectedLine> lines, String note) {
        List<DetectedLine> ordered = new ArrayList<>(lines);
        ordered.sort(Comparator
                .comparingInt((DetectedLine line) -> line.getBox().getTop())
                .thenComparingInt(line -> line.getBox().getLeft()));
        List<List<String>> rows = new ArrayList<>();
        for (DetectedLine line : ordered) rows.add(Collections.singletonList(line.getText()));
        return new TableModel(false, rows, 1, note);
    }

    private static double medianHeight(List<DetectedLine> lines) {
        List<Integer> heights = new ArrayList<>();
        for (DetectedLine line : lines) heights.add(Math.max(1, line.getBox().height()));
        Collections.sort(heights);
        int middle = heights.size() / 2;
        if (heights.size() % 2 == 1) return heights.get(middle);
        return (heights.get(middle - 1) + heights.get(middle)) / 2.0;
    }

    private static double medianDouble(List<Double> source) {
        List<Double> values = new ArrayList<>(source);
        Collections.sort(values);
        int middle = values.size() / 2;
        if (values.size() % 2 == 1) return values.get(middle);
        return (values.get(middle - 1) + values.get(middle)) / 2.0;
    }

    private static final class ParsedMarker {
        private final int number;
        private final String remainder;

        ParsedMarker(int number, String remainder) {
            this.number = number;
            this.remainder = remainder;
        }
    }

    private static final class RowMarker {
        private final WorkingLine line;
        private final int rowNumber;
        private final String remainder;

        RowMarker(WorkingLine line, int rowNumber, String remainder) {
            this.line = line;
            this.rowNumber = rowNumber;
            this.remainder = remainder;
        }
    }

    private static final class SpreadsheetFrame {
        private final List<RowMarker> markers;
        private final Map<DetectedLine, RowMarker> bySource;
        private final int top;
        private final int bottom;
        private final int minimumRow;
        private final int maximumRow;

        SpreadsheetFrame(
                List<RowMarker> markers,
                Map<DetectedLine, RowMarker> bySource,
                int top,
                int bottom,
                int minimumRow,
                int maximumRow
        ) {
            this.markers = markers;
            this.bySource = bySource;
            this.top = top;
            this.bottom = bottom;
            this.minimumRow = minimumRow;
            this.maximumRow = maximumRow;
        }
    }

    private static final class WorkingLine {
        private final DetectedLine source;
        private final String text;
        private final Box box;
        private final List<DetectedToken> tokens;

        WorkingLine(DetectedLine source) {
            this(source, source.getText(), source.getBox(), source.getTokens());
        }

        WorkingLine(DetectedLine source, String text, Box box, List<DetectedToken> tokens) {
            this.source = source;
            this.text = text == null ? "" : text.trim();
            this.box = box;
            this.tokens = tokens == null ? Collections.emptyList() : tokens;
        }

        double centerY() { return (box.getTop() + box.getBottom()) / 2.0; }
    }

    private static final class ColumnAnchor {
        private int left;
        private int count;

        ColumnAnchor(int left) { this.left = left; }
    }

    private static final class RowCluster {
        private final List<WorkingLine> cells = new ArrayList<>();
        private double center;
        private int sourceRowNumber = -1;

        void add(WorkingLine line) {
            center = cells.isEmpty()
                    ? line.centerY()
                    : (center * cells.size() + line.centerY()) / (cells.size() + 1);
            cells.add(line);
        }

        int top() {
            int value = Integer.MAX_VALUE;
            for (WorkingLine cell : cells) value = Math.min(value, cell.box.getTop());
            return value == Integer.MAX_VALUE ? (int) Math.round(center) : value;
        }

        int bottom() {
            int value = Integer.MIN_VALUE;
            for (WorkingLine cell : cells) value = Math.max(value, cell.box.getBottom());
            return value == Integer.MIN_VALUE ? (int) Math.round(center) : value;
        }

        int horizontalSpread() {
            int left = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE;
            for (WorkingLine cell : cells) {
                left = Math.min(left, cell.box.getLeft());
                right = Math.max(right, cell.box.getRight());
            }
            return left == Integer.MAX_VALUE ? 0 : Math.max(0, right - left);
        }
    }
}

