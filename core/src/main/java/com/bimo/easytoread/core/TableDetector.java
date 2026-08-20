package com.bimo.easytoread.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TableDetector {
    private TableDetector() {}

    public static TableModel detect(DocumentModel document) {
        List<DetectedLine> lines = flatten(document);
        if (lines.size() < 4) return fallback(lines, "insufficient geometry");

        lines.sort(Comparator
                .comparingInt((DetectedLine line) -> line.getBox().getTop())
                .thenComparingInt(line -> line.getBox().getLeft()));

        double medianHeight = medianHeight(lines);
        int pageLeft = Integer.MAX_VALUE;
        int pageRight = Integer.MIN_VALUE;
        for (DetectedLine line : lines) {
            pageLeft = Math.min(pageLeft, line.getBox().getLeft());
            pageRight = Math.max(pageRight, line.getBox().getRight());
        }
        int pageWidth = Math.max(1, pageRight - pageLeft);
        List<RowCluster> rows = clusterRows(lines, medianHeight);

        List<List<RowCluster>> groups = new ArrayList<>();
        List<RowCluster> active = new ArrayList<>();
        RowCluster previous = null;
        for (RowCluster row : rows) {
            boolean qualifies = row.cells.size() >= 2
                    && row.horizontalSpread() >= pageWidth * 0.15;
            if (!qualifies) {
                if (!active.isEmpty()) {
                    groups.add(active);
                    active = new ArrayList<>();
                }
                previous = row;
                continue;
            }
            if (previous != null
                    && !active.isEmpty()
                    && row.top() - previous.bottom() > medianHeight * 3.5) {
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
            for (RowCluster row : group) score += row.cells.size();
            if (group.size() >= 2 && score > bestScore) {
                best = group;
                bestScore = score;
            }
        }
        if (best == null) return fallback(lines, "no repeated rows");

        int anchorTolerance = Math.max(
                8,
                Math.min((int) Math.round(pageWidth * 0.06), (int) Math.round(medianHeight * 2.0))
        );
        List<ColumnAnchor> anchors = new ArrayList<>();
        for (RowCluster row : best) {
            row.cells.sort(Comparator.comparingInt(line -> line.getBox().getLeft()));
            for (DetectedLine cell : row.cells) {
                ColumnAnchor nearest = null;
                int bestDistance = Integer.MAX_VALUE;
                for (ColumnAnchor anchor : anchors) {
                    int distance = Math.abs(cell.getBox().getLeft() - anchor.left);
                    if (distance <= anchorTolerance && distance < bestDistance) {
                        nearest = anchor;
                        bestDistance = distance;
                    }
                }
                if (nearest == null) {
                    nearest = new ColumnAnchor(cell.getBox().getLeft());
                    anchors.add(nearest);
                } else {
                    nearest.left = Math.round(
                            (nearest.left * nearest.count + cell.getBox().getLeft())
                                    / (float) (nearest.count + 1)
                    );
                }
                nearest.count++;
            }
        }

        anchors.removeIf(anchor -> anchor.count < 2);
        anchors.sort(Comparator.comparingInt(anchor -> anchor.left));
        if (anchors.size() < 2) return fallback(lines, "column anchors were not repeatable");

        List<List<String>> outputRows = new ArrayList<>();
        for (RowCluster row : best) {
            List<StringBuilder> values = new ArrayList<>();
            for (int column = 0; column < anchors.size(); column++) {
                values.add(new StringBuilder());
            }
            for (DetectedLine cell : row.cells) {
                int column = nearestAnchor(cell.getBox().getLeft(), anchors);
                StringBuilder value = values.get(column);
                if (value.length() > 0) value.append(' ');
                value.append(cell.getText());
            }
            List<String> finalRow = new ArrayList<>();
            for (StringBuilder value : values) finalRow.add(value.toString().trim());
            outputRows.add(finalRow);
        }

        return new TableModel(
                true,
                outputRows,
                anchors.size(),
                "geometry-based table reconstruction"
        );
    }

    private static List<DetectedLine> flatten(DocumentModel document) {
        List<DetectedLine> lines = new ArrayList<>();
        if (document == null) return lines;
        for (DocumentModel.Block block : document.getBlocks()) {
            lines.addAll(block.getLines());
        }
        return lines;
    }

    private static TableModel fallback(List<DetectedLine> lines, String note) {
        List<DetectedLine> ordered = new ArrayList<>(lines);
        ordered.sort(Comparator
                .comparingInt((DetectedLine line) -> line.getBox().getTop())
                .thenComparingInt(line -> line.getBox().getLeft()));
        List<List<String>> rows = new ArrayList<>();
        for (DetectedLine line : ordered) {
            List<String> row = new ArrayList<>();
            row.add(line.getText());
            rows.add(row);
        }
        return new TableModel(false, rows, 1, note);
    }

    private static List<RowCluster> clusterRows(
            List<DetectedLine> lines,
            double medianHeight
    ) {
        List<RowCluster> rows = new ArrayList<>();
        double tolerance = Math.max(2.0, medianHeight * 0.70);
        for (DetectedLine line : lines) {
            double center = (line.getBox().getTop() + line.getBox().getBottom()) / 2.0;
            RowCluster nearest = null;
            double bestDistance = Double.MAX_VALUE;
            for (RowCluster row : rows) {
                double distance = Math.abs(center - row.center);
                if (distance <= tolerance && distance < bestDistance) {
                    nearest = row;
                    bestDistance = distance;
                }
            }
            if (nearest == null) {
                nearest = new RowCluster();
                rows.add(nearest);
            }
            nearest.add(line, center);
        }
        rows.sort(Comparator.comparingDouble(row -> row.center));
        return rows;
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

    private static double medianHeight(List<DetectedLine> lines) {
        List<Integer> heights = new ArrayList<>();
        for (DetectedLine line : lines) heights.add(Math.max(1, line.getBox().height()));
        heights.sort(Integer::compareTo);
        int middle = heights.size() / 2;
        if (heights.size() % 2 == 1) return heights.get(middle);
        return (heights.get(middle - 1) + heights.get(middle)) / 2.0;
    }

    private static final class ColumnAnchor {
        private int left;
        private int count;

        ColumnAnchor(int left) {
            this.left = left;
        }
    }

    private static final class RowCluster {
        private final List<DetectedLine> cells = new ArrayList<>();
        private double center;

        void add(DetectedLine line, double lineCenter) {
            center = cells.isEmpty()
                    ? lineCenter
                    : (center * cells.size() + lineCenter) / (cells.size() + 1);
            cells.add(line);
        }

        int top() {
            int value = Integer.MAX_VALUE;
            for (DetectedLine cell : cells) value = Math.min(value, cell.getBox().getTop());
            return value;
        }

        int bottom() {
            int value = Integer.MIN_VALUE;
            for (DetectedLine cell : cells) value = Math.max(value, cell.getBox().getBottom());
            return value;
        }

        int horizontalSpread() {
            int left = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE;
            for (DetectedLine cell : cells) {
                left = Math.min(left, cell.getBox().getLeft());
                right = Math.max(right, cell.getBox().getRight());
            }
            return Math.max(0, right - left);
        }
    }
}
