package com.bimo.easytoread.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class DocumentRendererTest {
    @Test
    public void markdownAndHtmlEscapeAndStructureContent() {
        DocumentModel document = new DocumentStructureEngine().structure(
                "test",
                Arrays.asList(
                        new DetectedLine("TITLE & NOTES", new Box(0, 0, 100, 30), 0.9f),
                        new DetectedLine("Text <source>.", new Box(0, 40, 100, 50), 0.9f),
                        new DetectedLine("• Review item", new Box(0, 70, 100, 80), 0.9f)
                )
        );

        String markdown = DocumentRenderer.toMarkdown(document);
        String html = DocumentRenderer.toHtml(document);

        assertTrue(markdown.contains("## TITLE & NOTES"));
        assertTrue(markdown.contains("- Review item"));
        assertTrue(html.contains("TITLE &amp; NOTES"));
        assertTrue(html.contains("Text &lt;source&gt;."));
        assertTrue(html.contains("<li>Review item</li>"));
    }

    @Test
    public void editedPlainTextHasExactPlainTextParity() {
        String edited = "Edited heading\n\nEdited paragraph.";
        assertEquals(edited, DocumentModel.fromPlainText(edited).toPlainText());
    }
}
