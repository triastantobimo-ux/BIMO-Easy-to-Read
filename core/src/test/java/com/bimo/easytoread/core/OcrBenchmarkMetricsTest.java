package com.bimo.easytoread.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class OcrBenchmarkMetricsTest {
    @Test
    public void computesTraceableTextAndCellMetrics() {
        assertEquals(0.25, OcrBenchmarkMetrics.characterErrorRate("test", "tent"), 0.0001);
        assertEquals(0.5, OcrBenchmarkMetrics.wordErrorRate("one two", "one too"), 0.0001);

        List<List<String>> expected = Arrays.asList(
                Arrays.asList("ID", "Amount"),
                Arrays.asList("001", "12,5%")
        );
        List<List<String>> actual = Arrays.asList(
                Arrays.asList("ID", "Amount"),
                Arrays.asList("001", "12.5%")
        );
        assertEquals(0.75, OcrBenchmarkMetrics.cellExactMatch(expected, actual), 0.0001);
        assertFalse(OcrBenchmarkMetrics.strictTableExact(expected, actual));
        assertTrue(OcrBenchmarkMetrics.strictTableExact(expected, expected));
    }

    @Test
    public void topologyMismatchCannotPassStrictTableExact() {
        List<List<String>> expected = Arrays.asList(
                Arrays.asList("A", "B"),
                Arrays.asList("1", "")
        );
        List<List<String>> shifted = Arrays.asList(
                Arrays.asList("A", "B"),
                Arrays.asList("", "1")
        );
        assertEquals(0.5, OcrBenchmarkMetrics.cellExactMatch(expected, shifted), 0.0001);
        assertFalse(OcrBenchmarkMetrics.strictTableExact(expected, shifted));
    }
}
