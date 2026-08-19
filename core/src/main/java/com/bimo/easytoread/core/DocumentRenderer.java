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
                    for (int index = 0; index < block.getLines().size(); index++) {
                        if (index > 0) output.append('\n');
                        output.append(normalizeListItem(block.getLines().get(index).getText()));
                    }
                    break;
                default:
                    output.append(block.joinedText());
                    break;
            }
        }
        return output.toString().trim();
    }

    public static String toHtml(DocumentModel document) {
        StringBuilder output = new StringBuilder();
        for (DocumentModel.Block block : document.getBlocks()) {
            switch (block.getType()) {
                case HEADING:
                    output.append("<h2>").append(escapeHtml(block.joinedText())).append("</h2>");
                    break;
                case LIST:
                    output.append("<ul>");
                    for (DetectedLine line : block.getLines()) {
                        output.append("<li>")
                                .append(escapeHtml(stripListMarker(line.getText())))
                                .append("</li>");
                    }
                    output.append("</ul>");
                    break;
                default:
                    output.append("<p>").append(escapeHtml(block.joinedText())).append("</p>");
                    break;
            }
        }
        return output.toString();
    }

    private static String normalizeListItem(String text) {
        return "- " + stripListMarker(text);
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
