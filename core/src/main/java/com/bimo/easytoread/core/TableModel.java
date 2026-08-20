package com.bimo.easytoread.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TableModel {
    private final boolean detected;
    private final List<List<String>> rows;
    private final int columnCount;
    private final String detectionNote;

    TableModel(
            boolean detected,
            List<List<String>> rows,
            int columnCount,
            String detectionNote
    ) {
        this.detected = detected;
        List<List<String>> safeRows = new ArrayList<>();
        if (rows != null) {
            for (List<String> row : rows) {
                safeRows.add(Collections.unmodifiableList(new ArrayList<>(row)));
            }
        }
        this.rows = Collections.unmodifiableList(safeRows);
        this.columnCount = Math.max(1, columnCount);
        this.detectionNote = detectionNote == null ? "" : detectionNote;
    }

    public boolean isDetected() { return detected; }
    public List<List<String>> getRows() { return rows; }
    public int getColumnCount() { return columnCount; }
    public String getDetectionNote() { return detectionNote; }
}
