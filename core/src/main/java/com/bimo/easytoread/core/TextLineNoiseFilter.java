package com.bimo.easytoread.core;

import java.util.ArrayList;
import java.util.List;

public final class TextLineNoiseFilter {
    private TextLineNoiseFilter() {}

    public static List<DetectedLine> filter(List<DetectedLine> source) {
        List<DetectedLine> kept = new ArrayList<>();
        if (source == null) return kept;
        for (DetectedLine line : source) {
            if (line != null && isUseful(line)) kept.add(line);
        }
        return kept;
    }

    static boolean isUseful(DetectedLine line) {
        String text = line.getText() == null ? "" : line.getText().trim();
        if (text.isEmpty()) return false;

        float confidence = line.getConfidence();
        if (confidence < 0f) return true;

        int lettersOrDigits = 0;
        int suspicious = 0;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (Character.isLetterOrDigit(value)) {
                lettersOrDigits++;
            } else if (!Character.isWhitespace(value)
                    && ".,:;!?()[]{}'\"/\\-–—_+%#@&*=<>|•·".indexOf(value) < 0) {
                suspicious++;
            }
        }

        if (confidence < 0.20f && text.length() < 24) return false;
        if (confidence < 0.32f && lettersOrDigits <= 3) return false;
        return confidence >= 0.28f
                || suspicious == 0
                || (double) lettersOrDigits / Math.max(1, text.length()) >= 0.70;
    }
}
