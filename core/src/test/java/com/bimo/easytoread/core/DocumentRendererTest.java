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
        assertTrue(html.contains("• Review item"));
    }

    @Test
    public void preservesOrderedParentAndNestedBulletsInMarkdown() {
        DocumentModel document = new DocumentStructureEngine().structure(
                "test",
                Arrays.asList(
                        new DetectedLine("5. Secrets:", new Box(0, 0, 100, 10), 0.9f),
                        new DetectedLine("ANDROID_KEY_ALIAS", new Box(24, 12, 100, 22), 0.9f),
                        new DetectedLine("6. Continue.", new Box(0, 24, 100, 34), 0.9f)
                )
        );

        String markdown = DocumentRenderer.toMarkdown(document);
        assertTrue(markdown.contains("5. Secrets:"));
        assertTrue(markdown.contains("   - ANDROID_KEY_ALIAS"));
        assertTrue(markdown.contains("6. Continue."));
    }

    @Test
    public void editedPlainTextHasExactPlainTextParity() {
        String edited = "Edited heading\n\nEdited paragraph.";
        assertEquals(edited, DocumentModel.fromPlainText(edited).toPlainText());
    }
}
