package com.bimo.easytoread.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class DetectedLine {
    private final String text;
    private final Box box;
    private final float confidence;
    private final List<DetectedToken> tokens;

    public DetectedLine(String text, Box box, float confidence) {
        this(text, box, confidence, Collections.emptyList());
    }

    public DetectedLine(
            String text,
            Box box,
            float confidence,
            List<DetectedToken> tokens
    ) {
        this.text = text == null ? "" : text.trim();
        this.box = Objects.requireNonNull(box, "box");
        this.confidence = confidence;
        this.tokens = Collections.unmodifiableList(new ArrayList<>(
                tokens == null ? Collections.emptyList() : tokens
        ));
    }

    public String getText() { return text; }
    public Box getBox() { return box; }
    public float getConfidence() { return confidence; }
    public List<DetectedToken> getTokens() { return tokens; }
}

