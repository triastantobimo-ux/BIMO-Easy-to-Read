package com.bimo.easytoread.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class OcrBenchmarkMetrics {
    private OcrBenchmarkMetrics() {}

    public static double characterErrorRate(String expected, String actual) {
        String reference = normalizeText(expected);
        String candidate = normalizeText(actual);
        return rate(levenshtein(characters(reference), characters(candidate)), reference.length());
    }

    public static double wordErrorRate(String expected, String actual) {
        List<String> reference = words(expected);
        List<String> candidate = words(actual);
        return rate(levenshtein(reference, candidate), reference.size());
    }

    public static double cellExactMatch(
            List<List<String>> expected,
            List<List<String>> actual
    ) {
        int rows = Math.max(expected == null ? 0 : expected.size(), actual == null ? 0 : actual.size());
        int columns = Math.max(maxColumns(expected), maxColumns(actual));
        if (rows == 0 || columns == 0) return rows == 0 && columns == 0 ? 1.0 : 0.0;

        int exact = 0;
        int total = rows * columns;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (normalizeCell(cell(expected, row, column))
                        .equals(normalizeCell(cell(actual, row, column)))) {
                    exact++;
                }
            }
        }
        return (double) exact / total;
    }

    public static boolean strictTableExact(
            List<List<String>> expected,
            List<List<String>> actual
    ) {
        if (expected == null || actual == null || expected.size() != actual.size()) return false;
        if (maxColumns(expected) != maxColumns(actual)) return false;
        return cellExactMatch(expected, actual) == 1.0;
    }

    private static String normalizeText(String value) {
        if (value == null) return "";
        return value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private static String normalizeCell(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ");
    }

    private static List<String> words(String value) {
        String normalized = normalizeText(value).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return Collections.emptyList();
        return Arrays.asList(normalized.split("\\s+"));
    }

    private static List<Character> characters(String value) {
        List<Character> output = new ArrayList<>(value.length());
        for (int index = 0; index < value.length(); index++) output.add(value.charAt(index));
        return output;
    }

    private static <T> int levenshtein(List<T> expected, List<T> actual) {
        int[] previous = new int[actual.size() + 1];
        int[] current = new int[actual.size() + 1];
        for (int column = 0; column <= actual.size(); column++) previous[column] = column;

        for (int row = 1; row <= expected.size(); row++) {
            current[0] = row;
            for (int column = 1; column <= actual.size(); column++) {
                int substitution = expected.get(row - 1).equals(actual.get(column - 1)) ? 0 : 1;
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + substitution
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[actual.size()];
    }

    private static double rate(int distance, int referenceLength) {
        if (referenceLength == 0) return distance == 0 ? 0.0 : 1.0;
        return (double) distance / referenceLength;
    }

    private static int maxColumns(List<List<String>> rows) {
        int maximum = 0;
        if (rows != null) {
            for (List<String> row : rows) maximum = Math.max(maximum, row == null ? 0 : row.size());
        }
        return maximum;
    }

    private static String cell(List<List<String>> rows, int row, int column) {
        if (rows == null || row >= rows.size() || rows.get(row) == null
                || column >= rows.get(row).size()) return "";
        return rows.get(row).get(column);
    }
}
