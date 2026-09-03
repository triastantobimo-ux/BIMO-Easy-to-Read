package com.bimo.easytoread.core;

import java.util.Objects;

public final class DetectedToken {
    private final String text;
    private final Box box;
    private final float confidence;

    public DetectedToken(String text, Box box, float confidence) {
        this.text = text == null ? "" : text.trim();
        this.box = Objects.requireNonNull(box, "box");
        this.confidence = confidence;
    }

    public String getText() { return text; }
    public Box getBox() { return box; }
    public float getConfidence() { return confidence; }
}

