package com.bimo.easytoread.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ReadingOrderResolver {
    public enum LayoutType {
        SINGLE_COLUMN,
        MULTI_COLUMN,
        POSTER,
        GRID
    }

    public static final class Result {
        private final LayoutType layoutType;
        private final List<DetectedLine> lines;

        Result(LayoutType layoutType, List<DetectedLine> lines) {
            this.layoutType = layoutType;
            this.lines = lines;
        }

        public LayoutType getLayoutType() { return layoutType; }
        public List<DetectedLine> getLines() { return new ArrayList<>(lines); }
    }

    private static final Comparator<DetectedLine> NATURAL_ORDER = Comparator
            .comparingInt((DetectedLine line) -> line.getBox().getTop())
            .thenComparingInt(line -> line.getBox().getLeft());

    public Result resolve(List<DetectedLine> source) {
        List<DetectedLine> natural = cleanAndSort(source);
        if (natural.size() < 2) return new Result(LayoutType.SINGLE_COLUMN, natural);

        PageMetrics metrics = PageMetrics.from(natural);
        ColumnDetection columns = detectColumns(natural, metrics);
        if (columns != null) {
            return new Result(LayoutType.MULTI_COLUMN, orderColumns(columns, metrics));
        }
        if (looksLikeGrid(natural, metrics)) {
            return new Result(LayoutType.GRID, natural);
        }
        if (looksLikePoster(natural, metrics)) {
            return new Result(LayoutType.POSTER, orderPoster(natural, metrics));
        }
        return new Result(LayoutType.SINGLE_COLUMN, natural);
    }

    private static List<DetectedLine> cleanAndSort(List<DetectedLine> source) {
        List<DetectedLine> lines = new ArrayList<>();
        if (source != null) {
            for (DetectedLine line : source) {
                if (line != null && !line.getText().trim().isEmpty()) lines.add(line);
            }
        }
        lines.sort(NATURAL_ORDER);
        return lines;
    }

    private static ColumnDetection detectColumns(
            List<DetectedLine> lines,
            PageMetrics metrics
    ) {
        if (lines.size() < 4 || metrics.pageWidth < 1) return null;

        int maximumAnchorWidth = Math.max(
                1,
                (int) Math.round(metrics.pageWidth * 0.72)
        );
        List<DetectedLine> anchors = new ArrayList<>();
        for (DetectedLine line : lines) {
            if (line.getBox().width() <= maximumAnchorWidth) anchors.add(line);
        }
        if (anchors.size() < 4) return null;

        anchors.sort(Comparator
                .comparingInt((DetectedLine line) -> line.getBox().getLeft())
                .thenComparingInt(line -> line.getBox().getTop()));

        int gapThreshold = Math.max(
                (int) Math.round(metrics.pageWidth * 0.10),
                (int) Math.round(metrics.medianHeight * 2.5)
        );

        List<ColumnSeed> seeds = new ArrayList<>();
        ColumnSeed current = null;
        int previousLeft = Integer.MIN_VALUE;
        for (DetectedLine line : anchors) {
            int left = line.getBox().getLeft();
            if (current == null || left - previousLeft > gapThreshold) {
                current = new ColumnSeed();
                seeds.add(current);
            }
            current.anchorLines.add(line);
            previousLeft = left;
        }

        List<ColumnSeed> valid = new ArrayList<>();
        for (ColumnSeed seed : seeds) {
            if (seed.anchorLines.size() >= 2) {
                seed.anchorLeft = medianLeft(seed.anchorLines);
                valid.add(seed);
            }
        }
        if (valid.size() < 2 || valid.size() > 4) return null;

        valid.sort(Comparator.comparingInt(seed -> seed.anchorLeft));
        for (int index = 1; index < valid.size(); index++) {
            if (valid.get(index).anchorLeft - valid.get(index - 1).anchorLeft < gapThreshold) {
                return null;
            }
        }

        List<DetectedLine> spanning = new ArrayList<>();
        for (DetectedLine line : lines) {
            if (line.getBox().width() >= metrics.pageWidth * 0.72) {
                spanning.add(line);
                continue;
            }

            ColumnSeed best = null;
            int bestDistance = Integer.MAX_VALUE;
            for (ColumnSeed seed : valid) {
                int distance = Math.abs(line.getBox().getLeft() - seed.anchorLeft);
                if (distance < bestDistance) {
                    best = seed;
                    bestDistance = distance;
                }
            }
            if (best == null || bestDistance > metrics.pageWidth * 0.34) {
                spanning.add(line);
            } else {
                best.assignedLines.add(line);
            }
        }

        for (ColumnSeed seed : valid) {
            if (seed.assignedLines.size() < 2) return null;
            seed.assignedLines.sort(NATURAL_ORDER);
        }
        spanning.sort(NATURAL_ORDER);
        return new ColumnDetection(valid, spanning);
    }

    private static List<DetectedLine> orderColumns(
            ColumnDetection detection,
            PageMetrics metrics
    ) {
        List<DetectedLine> ordered = new ArrayList<>();
        int firstColumnTop = Integer.MAX_VALUE;
        for (ColumnSeed seed : detection.columns) {
            if (!seed.assignedLines.isEmpty()) {
                firstColumnTop = Math.min(
                        firstColumnTop,
                        seed.assignedLines.get(0).getBox().getTop()
                );
            }
        }

        List<DetectedLine> trailingSpanning = new ArrayList<>();
        for (DetectedLine line : detection.spanning) {
            if (line.getBox().getBottom() <= firstColumnTop + metrics.medianHeight) {
                ordered.add(line);
            } else {
                trailingSpanning.add(line);
            }
        }

        for (ColumnSeed seed : detection.columns) {
            ordered.addAll(seed.assignedLines);
        }
        ordered.addAll(trailingSpanning);
        return ordered;
    }

    private static boolean looksLikeGrid(List<DetectedLine> lines, PageMetrics metrics) {
        int qualifyingRows = 0;
        int index = 0;
        while (index < lines.size()) {
            List<DetectedLine> row = new ArrayList<>();
            DetectedLine first = lines.get(index);
            row.add(first);
            int next = index + 1;
            while (next < lines.size()
                    && Math.abs(lines.get(next).getBox().getTop() - first.getBox().getTop())
                    <= metrics.medianHeight * 0.65) {
                row.add(lines.get(next));
                next++;
            }

            if (row.size() >= 2) {
                row.sort(Comparator.comparingInt(line -> line.getBox().getLeft()));
                int widestGap = 0;
                for (int item = 1; item < row.size(); item++) {
                    widestGap = Math.max(
                            widestGap,
                            row.get(item).getBox().getLeft()
                                    - row.get(item - 1).getBox().getRight()
                    );
                }
                if (widestGap >= metrics.pageWidth * 0.10) qualifyingRows++;
            }
            index = next;
        }
        return qualifyingRows >= 2;
    }

    private static boolean looksLikePoster(List<DetectedLine> lines, PageMetrics metrics) {
        int prominent = 0;
        int largeGaps = 0;
        DetectedLine previous = null;
        for (DetectedLine line : lines) {
            if (line.getBox().height() >= metrics.medianHeight * 1.55) prominent++;
            if (previous != null
                    && line.getBox().getTop() - previous.getBox().getBottom()
                    > metrics.medianHeight * 1.8) {
                largeGaps++;
            }
            previous = line;
        }
        return prominent > 0 && lines.size() <= 45
                || largeGaps >= 2 && lines.size() <= 24;
    }

    private static List<DetectedLine> orderPoster(
            List<DetectedLine> natural,
            PageMetrics metrics
    ) {
        List<DetectedLine> headlines = new ArrayList<>();
        List<DetectedLine> remaining = new ArrayList<>();
        int hierarchyZoneBottom = metrics.pageTop
                + (int) Math.round(metrics.pageHeight * 0.45);

        for (DetectedLine line : natural) {
            if (line.getBox().getTop() <= hierarchyZoneBottom
                    && isProminentHeading(line, metrics.medianHeight)) {
                headlines.add(line);
            } else {
                remaining.add(line);
            }
        }
        if (headlines.isEmpty()) return natural;

        headlines.sort(Comparator
                .comparingInt((DetectedLine line) -> -line.getBox().height())
                .thenComparingInt(line -> line.getBox().getTop())
                .thenComparingInt(line -> line.getBox().getLeft()));
        remaining.sort(NATURAL_ORDER);

        List<DetectedLine> ordered = new ArrayList<>(headlines);
        ordered.addAll(remaining);
        return ordered;
    }

    private static boolean isProminentHeading(DetectedLine line, double medianHeight) {
        String text = line.getText().trim();
        if (text.length() < 2 || text.length() > 120) return false;
        if (line.getBox().height() >= medianHeight * 1.55) return true;

        int letters = 0;
        int uppercase = 0;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (Character.isLetter(value)) {
                letters++;
                if (Character.isUpperCase(value)) uppercase++;
            }
        }
        return letters >= 4
                && line.getBox().height() >= medianHeight * 1.15
                && text.equals(text.toUpperCase(Locale.ROOT))
                && (double) uppercase / letters >= 0.85;
    }

    private static int medianLeft(List<DetectedLine> lines) {
        List<Integer> values = new ArrayList<>();
        for (DetectedLine line : lines) values.add(line.getBox().getLeft());
        values.sort(Integer::compareTo);
        return values.get(values.size() / 2);
    }

    private static double median(List<Integer> source) {
        List<Integer> values = new ArrayList<>(source);
        values.sort(Integer::compareTo);
        int middle = values.size() / 2;
        if (values.size() % 2 == 1) return values.get(middle);
        return (values.get(middle - 1) + values.get(middle)) / 2.0;
    }

    private static final class ColumnSeed {
        private final List<DetectedLine> anchorLines = new ArrayList<>();
        private final List<DetectedLine> assignedLines = new ArrayList<>();
        private int anchorLeft;
    }

    private static final class ColumnDetection {
        private final List<ColumnSeed> columns;
        private final List<DetectedLine> spanning;

        ColumnDetection(List<ColumnSeed> columns, List<DetectedLine> spanning) {
            this.columns = columns;
            this.spanning = spanning;
        }
    }

    private static final class PageMetrics {
        private final int pageTop;
        private final int pageWidth;
        private final int pageHeight;
        private final double medianHeight;

        private PageMetrics(int pageTop, int pageWidth, int pageHeight, double medianHeight) {
            this.pageTop = pageTop;
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
            this.medianHeight = medianHeight;
        }

        static PageMetrics from(List<DetectedLine> lines) {
            int left = Integer.MAX_VALUE;
            int top = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE;
            int bottom = Integer.MIN_VALUE;
            List<Integer> heights = new ArrayList<>();
            for (DetectedLine line : lines) {
                Box box = line.getBox();
                left = Math.min(left, box.getLeft());
                top = Math.min(top, box.getTop());
                right = Math.max(right, box.getRight());
                bottom = Math.max(bottom, box.getBottom());
                heights.add(Math.max(1, box.height()));
            }
            return new PageMetrics(
                    top,
                    Math.max(1, right - left),
                    Math.max(1, bottom - top),
                    median(heights)
            );
        }
    }
}
