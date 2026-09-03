package com.bimo.easytoread.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class DocumentStructureEngine {
    private static final Pattern LIST_PATTERN = Pattern.compile(
            "^(?:[•◦▪‣*-]|\\d{1,3}[.)]|[A-Za-z][.)])\\s+.+"
    );
    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile(
            "^(?:\\d{1,3}[.)]|[A-Za-z][.)])\\s+.+"
    );
    private static final Pattern BULLET_LIST_PATTERN = Pattern.compile(
            "^[•◦▪‣*-]\\s+.+"
    );

    private final ReadingOrderResolver readingOrderResolver = new ReadingOrderResolver();

    public DocumentModel structure(String engineId, List<DetectedLine> source) {
        ReadingOrderResolver.Result resolved = readingOrderResolver.resolve(source);
        List<DetectedLine> lines = resolved.getLines();
        if (lines.isEmpty()) return new DocumentModel(engineId, new ArrayList<>());

        double medianHeight = medianHeight(lines);
        lines = restoreMissingNestedBullets(lines, medianHeight);

        List<DocumentModel.Block> blocks = new ArrayList<>();
        List<DetectedLine> current = new ArrayList<>();
        DocumentModel.BlockType currentType = null;
        DetectedLine previous = null;

        for (DetectedLine line : lines) {
            DocumentModel.BlockType lineType = classify(line, medianHeight);
            boolean startsNew = previous == null
                    || lineType == DocumentModel.BlockType.HEADING
                    || currentType == DocumentModel.BlockType.HEADING
                    || lineType != currentType
                    || isSeparated(previous, line, medianHeight);

            if (startsNew && !current.isEmpty()) {
                blocks.add(new DocumentModel.Block(currentType, current));
                current = new ArrayList<>();
            }
            current.add(line);
            currentType = lineType;
            previous = line;
        }
        if (!current.isEmpty()) blocks.add(new DocumentModel.Block(currentType, current));

        String tracedEngineId = engineId
                + "|layout="
                + resolved.getLayoutType().name().toLowerCase(Locale.ROOT);
        return new DocumentModel(tracedEngineId, blocks);
    }

    private static List<DetectedLine> restoreMissingNestedBullets(
            List<DetectedLine> lines,
            double medianHeight
    ) {
        List<DetectedLine> restored = new ArrayList<>();
        boolean expectsNestedList = false;
        int parentLeft = 0;
        int nestedLeft = -1;
        DetectedLine previous = null;
        int minimumIndent = Math.max(8, (int) Math.round(medianHeight * 0.55));

        for (DetectedLine original : lines) {
            DetectedLine line = original;
            String text = line.getText().trim();

            if (previous != null
                    && line.getBox().getTop()
                    < previous.getBox().getTop() - medianHeight * 0.75) {
                expectsNestedList = false;
                nestedLeft = -1;
            }

            if (isOrderedListText(text)) {
                parentLeft = line.getBox().getLeft();
                nestedLeft = -1;
                expectsNestedList = text.endsWith(":");
            } else if (isBulletListText(text)) {
                if (expectsNestedList && nestedLeft < 0) {
                    nestedLeft = line.getBox().getLeft();
                }
            } else if (expectsNestedList && previous != null) {
                int left = line.getBox().getLeft();
                int indent = left - parentLeft;
                int verticalGap = line.getBox().getTop() - previous.getBox().getBottom();
                boolean closeToPrevious = verticalGap <= medianHeight * 1.85;
                boolean alignedWithChildren = nestedLeft < 0
                        || Math.abs(left - nestedLeft) <= Math.max(12, medianHeight);

                if (indent >= minimumIndent && closeToPrevious && alignedWithChildren) {
                    line = new DetectedLine(
                            "• " + text,
                            line.getBox(),
                            line.getConfidence()
                    );
                    if (nestedLeft < 0) nestedLeft = left;
                } else if (left <= parentLeft + minimumIndent / 2
                        || verticalGap > medianHeight * 2.25) {
                    expectsNestedList = false;
                    nestedLeft = -1;
                }
            }

            restored.add(line);
            previous = line;
        }
        return restored;
    }

    private static DocumentModel.BlockType classify(DetectedLine line, double medianHeight) {
        if (isListText(line.getText())) return DocumentModel.BlockType.LIST;
        if (isHeading(line, medianHeight)) return DocumentModel.BlockType.HEADING;
        return DocumentModel.BlockType.PARAGRAPH;
    }

    private static boolean isSeparated(DetectedLine previous, DetectedLine current, double medianHeight) {
        if (current.getBox().getTop()
                < previous.getBox().getTop() - medianHeight * 0.75) {
            return true;
        }
        int verticalGap = current.getBox().getTop() - previous.getBox().getBottom();
        if (verticalGap > medianHeight * 1.45) return true;
        return verticalGap > medianHeight * 0.75
                && previous.getBox().horizontalOverlapRatio(current.getBox()) < 0.20;
    }

    private static boolean isHeading(DetectedLine line, double medianHeight) {
        String text = line.getText().trim();
        if (text.length() < 2 || text.length() > 100) return false;
        if (line.getBox().height() >= medianHeight * 1.28) return true;

        int letters = 0;
        int uppercase = 0;
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if (Character.isLetter(value)) {
                letters++;
                if (Character.isUpperCase(value)) uppercase++;
            }
        }
        return letters >= 3
                && text.equals(text.toUpperCase(Locale.ROOT))
                && (double) uppercase / letters >= 0.85
                && !text.endsWith(".");
    }

    static boolean isListText(String text) {
        return text != null && LIST_PATTERN.matcher(text.trim()).matches();
    }

    static boolean isOrderedListText(String text) {
        return text != null && ORDERED_LIST_PATTERN.matcher(text.trim()).matches();
    }

    static boolean isBulletListText(String text) {
        return text != null && BULLET_LIST_PATTERN.matcher(text.trim()).matches();
    }

    private static double medianHeight(List<DetectedLine> lines) {
        List<Integer> heights = new ArrayList<>();
        for (DetectedLine line : lines) heights.add(Math.max(1, line.getBox().height()));
        heights.sort(Integer::compareTo);
        int middle = heights.size() / 2;
        if (heights.size() % 2 == 1) return heights.get(middle);
        return (heights.get(middle - 1) + heights.get(middle)) / 2.0;
    }
}
