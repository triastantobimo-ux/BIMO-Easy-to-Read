package com.bimo.easytoread.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class DocumentStructureEngine {
    private static final Pattern LIST_PATTERN = Pattern.compile(
            "^(?:[•◦▪‣*-]|\\d{1,3}[.)]|[A-Za-z][.)])\\s+.+"
    );

    public DocumentModel structure(String engineId, List<DetectedLine> source) {
        List<DetectedLine> lines = new ArrayList<>();
        if (source != null) {
            for (DetectedLine line : source) {
                if (line != null && !line.getText().trim().isEmpty()) lines.add(line);
            }
        }
        if (lines.isEmpty()) return new DocumentModel(engineId, new ArrayList<>());

        lines.sort(Comparator
                .comparingInt((DetectedLine line) -> line.getBox().getTop())
                .thenComparingInt(line -> line.getBox().getLeft()));

        double medianHeight = medianHeight(lines);
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
        return new DocumentModel(engineId, blocks);
    }

    private static DocumentModel.BlockType classify(DetectedLine line, double medianHeight) {
        if (isListText(line.getText())) return DocumentModel.BlockType.LIST;
        if (isHeading(line, medianHeight)) return DocumentModel.BlockType.HEADING;
        return DocumentModel.BlockType.PARAGRAPH;
    }

    private static boolean isSeparated(DetectedLine previous, DetectedLine current, double medianHeight) {
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

    private static double medianHeight(List<DetectedLine> lines) {
        List<Integer> heights = new ArrayList<>();
        for (DetectedLine line : lines) heights.add(Math.max(1, line.getBox().height()));
        heights.sort(Integer::compareTo);
        int middle = heights.size() / 2;
        if (heights.size() % 2 == 1) return heights.get(middle);
        return (heights.get(middle - 1) + heights.get(middle)) / 2.0;
    }
}
