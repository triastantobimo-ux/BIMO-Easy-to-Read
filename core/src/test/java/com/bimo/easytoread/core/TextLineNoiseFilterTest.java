package com.bimo.easytoread.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TextLineNoiseFilterTest {
    @Test
    public void removesShortLowConfidenceSceneFragments() {
        assertFalse(TextLineNoiseFilter.isUseful(line("@@", 0.14f)));
        assertFalse(TextLineNoiseFilter.isUseful(line("x", 0.29f)));
    }

    @Test
    public void keepsReadableTextAndUnknownConfidence() {
        assertTrue(TextLineNoiseFilter.isUseful(line("Universitas Bhayangkara", 0.84f)));
        assertTrue(TextLineNoiseFilter.isUseful(line("2026", 0.72f)));
        assertTrue(TextLineNoiseFilter.isUseful(line("Valid text", -1f)));
    }

    private static DetectedLine line(String text, float confidence) {
        return new DetectedLine(text, new Box(0, 0, 100, 20), confidence);
    }
}
