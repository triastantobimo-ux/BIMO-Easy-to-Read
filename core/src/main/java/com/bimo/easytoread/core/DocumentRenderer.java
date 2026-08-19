package com.bimo.easytoread.core;

public final class DocumentRenderer {
    private DocumentRenderer() {}

    public static String toMarkdown(DocumentModel document) {
        StringBuilder output = new StringBuilder();
        for (DocumentModel.Block block : document.getBlocks()) {
            if (output.length() > 0) output.append("\n\n");
            switch (block.getType()) {
                case HEADING:
                    output.append("## ").append(block.joinedText());
                    break;
                case LIST:
                    appendMarkdownList(output, block);
                    break;
                default:
                    output.append(block.joinedText());
                    break;
            }
        }
        return output.toString().trim();
    }

    private static void appendMarkdownList(StringBuilder output, DocumentModel.Block block) {
        boolean orderedContext = false;
        for (int index = 0; index < block.getLines().size(); index++) {
            if (index > 0) output.append('\n');
            String text = block.getLines().get(index).getText();
            if (DocumentStructureEngine.isOrderedListText(text)) {
                output.append(text);
                orderedContext = true;
            } else {
                if (orderedContext) output.append("   ");
                output.append("- ").append(stripListMarker(text));
            }
        }
    }

    public static String toHtml(DocumentModel document) {
        StringBuilder output = new StringBuilder();
        for (DocumentModel.Block block : document.getBlocks()) {
            switch (block.getType()) {
                case HEADING:
                    output.append("<h2>").append(escapeHtml(block.joinedText())).append("</h2>");
                    break;
                case LIST:
                    appendExactListHtml(output, block);
                    break;
                default:
                    output.append("<p>").append(escapeHtml(block.joinedText())).append("</p>");
                    break;
            }
        }
        return output.toString();
    }

    private static void appendExactListHtml(StringBuilder output, DocumentModel.Block block) {
        output.append("<div>");
        boolean orderedContext = false;
        for (DetectedLine line : block.getLines()) {
            String text = line.getText();
            boolean ordered = DocumentStructureEngine.isOrderedListText(text);
            boolean nestedBullet = !ordered && orderedContext;
            output.append(nestedBullet ? "<div style=\"margin-left:2em\">" : "<div>")
                    .append(escapeHtml(text))
                    .append("</div>");
            if (ordered) orderedContext = true;
        }
        output.append("</div>");
    }

    private static String stripListMarker(String text) {
        return text.replaceFirst("^(?:[•◦▪‣*-]|\\d{1,3}[.)]|[A-Za-z][.)])\\s+", "").trim();
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
