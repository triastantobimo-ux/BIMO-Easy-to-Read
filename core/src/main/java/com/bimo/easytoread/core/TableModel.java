package com.bimo.easytoread.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TableModel {
    public enum RowRole {
        TITLE,
        SUBTITLE,
        HEADER,
        SUMMARY,
        DATA,
        BLANK
    }

    private final boolean detected;
    private final List<List<String>> rows;
    private final List<RowRole> rowRoles;
    private final int columnCount;
    private final int primaryHeaderRowIndex;
    private final String detectionNote;

    TableModel(
            boolean detected,
            List<List<String>> rows,
            int columnCount,
            String detectionNote
    ) {
        this(detected, rows, columnCount, detectionNote, null, -1);
    }

    TableModel(
            boolean detected,
            List<List<String>> rows,
            int columnCount,
            String detectionNote,
            List<RowRole> rowRoles,
            int primaryHeaderRowIndex
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

        List<RowRole> safeRoles = new ArrayList<>();
        for (int index = 0; index < safeRows.size(); index++) {
            RowRole role = rowRoles != null && index < rowRoles.size()
                    ? rowRoles.get(index)
                    : RowRole.DATA;
            safeRoles.add(role == null ? RowRole.DATA : role);
        }
        this.rowRoles = Collections.unmodifiableList(safeRoles);
        this.primaryHeaderRowIndex = primaryHeaderRowIndex >= 0
                && primaryHeaderRowIndex < safeRows.size()
                ? primaryHeaderRowIndex
                : -1;
    }

    public boolean isDetected() { return detected; }
    public List<List<String>> getRows() { return rows; }
    public List<RowRole> getRowRoles() { return rowRoles; }
    public RowRole getRowRole(int index) { return rowRoles.get(index); }
    public int getColumnCount() { return columnCount; }
    public int getPrimaryHeaderRowIndex() { return primaryHeaderRowIndex; }
    public String getDetectionNote() { return detectionNote; }
}

