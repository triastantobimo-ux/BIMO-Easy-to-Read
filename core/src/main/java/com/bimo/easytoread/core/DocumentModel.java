package com.bimo.easytoread.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DocumentModel {
    public enum BlockType {
        HEADING,
        PARAGRAPH,
        LIST,
        TABLE,
        UNKNOWN
    }

    public static final class Block {
        private final BlockType type;
        private final List<DetectedLine> lines;
        private final Box box;
        private final float confidence;

        public Block(BlockType type, List<DetectedLine> lines) {
            if (lines == null || lines.isEmpty()) {
                throw new IllegalArgumentException("A block must contain at least one line.");
            }
            this.type = type == null ? BlockType.UNKNOWN : type;
            this.lines = Collections.unmodifiableList(new ArrayList<>(lines));

            Box aggregate = lines.get(0).getBox();
            float confidenceTotal = 0f;
            int confidenceCount = 0;
            for (DetectedLine line : lines) {
                aggregate = aggregate.union(line.getBox());
                if (line.getConfidence() >= 0f) {
                    confidenceTotal += line.getConfidence();
                    confidenceCount++;
                }
            }
            this.box = aggregate;
            this.confidence = confidenceCount == 0 ? -1f : confidenceTotal / confidenceCount;
        }

        public BlockType getType() { return type; }
        public List<DetectedLine> getLines() { return lines; }
        public Box getBox() { return box; }
        public float getConfidence() { return confidence; }

        public String joinedText() {
            StringBuilder output = new StringBuilder();
            for (DetectedLine line : lines) {
                if (output.length() > 0) output.append(' ');
                output.append(line.getText());
            }
            return output.toString().trim();
        }
    }

    private final String engineId;
    private final List<Block> blocks;
    private final WorksheetModel worksheet;

    public DocumentModel(String engineId, List<Block> blocks) {
        this(engineId, blocks, null);
    }

    public DocumentModel(String engineId, List<Block> blocks, WorksheetModel worksheet) {
        this.engineId = engineId == null ? "unknown" : engineId;
        this.blocks = Collections.unmodifiableList(new ArrayList<>(
                blocks == null ? Collections.emptyList() : blocks
        ));
        this.worksheet = worksheet;
    }

    public String getEngineId() { return engineId; }
    public List<Block> getBlocks() { return blocks; }
    public WorksheetModel getWorksheet() { return worksheet; }

    public int countLines() {
        int count = 0;
        for (Block block : blocks) count += block.getLines().size();
        return count;
    }

    public String toPlainText() {
        StringBuilder output = new StringBuilder();
        for (Block block : blocks) {
            if (output.length() > 0) output.append("\n\n");
            if (block.getType() == BlockType.LIST) {
                for (int i = 0; i < block.getLines().size(); i++) {
                    if (i > 0) output.append('\n');
                    output.append(block.getLines().get(i).getText());
                }
            } else {
                output.append(block.joinedText());
            }
        }
        return output.toString().trim();
    }

    public static DocumentModel fromPlainText(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.isEmpty()) return new DocumentModel("manual-edit", Collections.emptyList());

        List<Block> blocks = new ArrayList<>();
        String[] paragraphs = safe.split("\\R\\s*\\R");
        int y = 0;
        for (String paragraph : paragraphs) {
            List<DetectedLine> lines = new ArrayList<>();
            for (String rawLine : paragraph.split("\\R")) {
                String line = rawLine.trim();
                if (!line.isEmpty()) {
                    lines.add(new DetectedLine(line, new Box(0, y, Math.max(1, line.length()), y + 1), -1f));
                    y += 2;
                }
            }
            if (!lines.isEmpty()) {
                BlockType type = DocumentStructureEngine.isListText(lines.get(0).getText())
                        ? BlockType.LIST : BlockType.PARAGRAPH;
                blocks.add(new Block(type, lines));
            }
        }
        return new DocumentModel("manual-edit", blocks);
    }
}
