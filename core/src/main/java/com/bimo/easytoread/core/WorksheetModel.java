package com.bimo.easytoread.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class WorksheetModel {
    public enum VerificationStatus {
        AUTOMATIC,
        REVIEW_REQUIRED,
        VERIFIED
    }

    public static final class Cell {
        private final int row;
        private final int column;
        private final int rowSpan;
        private final int columnSpan;
        private final String text;
        private final float confidence;

        public Cell(
                int row,
                int column,
                int rowSpan,
                int columnSpan,
                String text,
                float confidence
        ) {
            if (row < 0 || column < 0) throw new IllegalArgumentException("Negative cell index.");
            this.row = row;
            this.column = column;
            this.rowSpan = Math.max(1, rowSpan);
            this.columnSpan = Math.max(1, columnSpan);
            this.text = text == null ? "" : text.trim();
            this.confidence = confidence;
        }

        public int getRow() { return row; }
        public int getColumn() { return column; }
        public int getRowSpan() { return rowSpan; }
        public int getColumnSpan() { return columnSpan; }
        public String getText() { return text; }
        public float getConfidence() { return confidence; }
    }

    private final int rowCount;
    private final int columnCount;
    private final List<Cell> cells;
    private final int primaryHeaderRowIndex;
    private final float topologyConfidence;
    private final float textConfidence;
    private final VerificationStatus verificationStatus;
    private final String detectionNote;

    public WorksheetModel(
            int rowCount,
            int columnCount,
            List<Cell> cells,
            int primaryHeaderRowIndex,
            float topologyConfidence,
            float textConfidence,
            VerificationStatus verificationStatus,
            String detectionNote
    ) {
        this.rowCount = Math.max(1, rowCount);
        this.columnCount = Math.max(1, columnCount);
        List<Cell> safe = new ArrayList<>(cells == null ? Collections.emptyList() : cells);
        safe.sort(Comparator.comparingInt(Cell::getRow).thenComparingInt(Cell::getColumn));
        this.cells = Collections.unmodifiableList(safe);
        this.primaryHeaderRowIndex = primaryHeaderRowIndex >= 0
                && primaryHeaderRowIndex < this.rowCount ? primaryHeaderRowIndex : -1;
        this.topologyConfidence = clamp(topologyConfidence);
        this.textConfidence = clamp(textConfidence);
        this.verificationStatus = verificationStatus == null
                ? VerificationStatus.REVIEW_REQUIRED : verificationStatus;
        this.detectionNote = detectionNote == null ? "" : detectionNote;
    }

    public int getRowCount() { return rowCount; }
    public int getColumnCount() { return columnCount; }
    public List<Cell> getCells() { return cells; }
    public int getPrimaryHeaderRowIndex() { return primaryHeaderRowIndex; }
    public float getTopologyConfidence() { return topologyConfidence; }
    public float getTextConfidence() { return textConfidence; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public String getDetectionNote() { return detectionNote; }

    public List<List<String>> toRows() {
        List<List<String>> rows = new ArrayList<>();
        for (int row = 0; row < rowCount; row++) {
            List<String> values = new ArrayList<>(Collections.nCopies(columnCount, ""));
            rows.add(values);
        }
        for (Cell cell : cells) {
            if (cell.row < rowCount && cell.column < columnCount) {
                rows.get(cell.row).set(cell.column, cell.text);
            }
        }
        return rows;
    }

    public TableModel toTableModel() {
        List<List<String>> rows = toRows();
        List<TableModel.RowRole> roles = new ArrayList<>();
        for (int row = 0; row < rowCount; row++) {
            if (row == primaryHeaderRowIndex) roles.add(TableModel.RowRole.HEADER);
            else roles.add(TableModel.RowRole.DATA);
        }
        return new TableModel(
                true,
                rows,
                columnCount,
                detectionNote + "; status=" + verificationStatus.name().toLowerCase(),
                roles,
                primaryHeaderRowIndex
        );
    }

    private static float clamp(float value) {
        if (Float.isNaN(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}
