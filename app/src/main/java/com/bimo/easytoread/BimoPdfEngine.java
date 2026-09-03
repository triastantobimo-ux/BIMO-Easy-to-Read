package com.bimo.easytoread;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import io.legere.pdfiumandroid.PdfDocument;
import io.legere.pdfiumandroid.PdfPage;
import io.legere.pdfiumandroid.PdfTextPage;
import io.legere.pdfiumandroid.PdfiumCore;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Bundled PDFium-backed processing boundary for BIMO EasyDocs.
 *
 * All calls are serialized by the owning workspace worker. PDFium itself is
 * packaged in the APK and never downloads a runtime component.
 */
public final class BimoPdfEngine implements Closeable {
    private final ParcelFileDescriptor descriptor;
    private final PdfiumCore core;
    private final PdfDocument document;
    private boolean closed;

    private BimoPdfEngine(ParcelFileDescriptor descriptor, PdfiumCore core, PdfDocument document) {
        this.descriptor = descriptor;
        this.core = core;
        this.document = document;
    }

    public static BimoPdfEngine open(Context context, Uri uri, String password) throws IOException {
        ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(uri, "r");
        if (descriptor == null) throw new IOException("Document provider returned no file descriptor.");
        try {
            PdfiumCore core = new PdfiumCore();
            PdfDocument document = core.newDocument(descriptor, password);
            BimoPdfEngine engine = new BimoPdfEngine(descriptor, core, document);
            if (engine.getPageCount() <= 0) {
                engine.close();
                throw new IOException("PDF contains no pages.");
            }
            return engine;
        } catch (Throwable error) {
            try {
                descriptor.close();
            } catch (IOException ignored) {
                // Preserve the original open failure.
            }
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("PDFium could not open this document.", error);
        }
    }

    public synchronized int getPageCount() {
        ensureOpen();
        return core.getPageCount(document);
    }

    public synchronized Bitmap renderPage(int pageIndex, int maxWidth) throws IOException {
        ensurePage(pageIndex);
        Bitmap bitmap = null;
        try {
            core.openPage(document, pageIndex);
            int widthPoints = core.getPageWidthPoint(document, pageIndex);
            int heightPoints = core.getPageHeightPoint(document, pageIndex);
            if (widthPoints <= 0 || heightPoints <= 0) throw new IOException("Invalid PDF page dimensions.");
            int width = Math.max(1, Math.min(maxWidth, widthPoints * 3));
            float scale = width / (float) widthPoints;
            int height = Math.max(1, Math.round(heightPoints * scale));
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            core.renderPageBitmap(document, bitmap, pageIndex, 0, 0, width, height, true);
            return bitmap;
        } catch (Throwable error) {
            if (bitmap != null) bitmap.recycle();
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("PDFium failed to render page " + (pageIndex + 1) + ".", error);
        } finally {
            core.closePage(document, pageIndex);
        }
    }

    public synchronized String extractText(int pageIndex) throws IOException {
        ensurePage(pageIndex);
        try {
            core.openTextPage(document, pageIndex);
            try {
                int count = core.textPageCountChars(document, pageIndex);
                if (count <= 0) return "";
                String value = core.textPageGetText(document, pageIndex, 0, count);
                return value == null ? "" : value;
            } finally {
                core.closeTextPage(document, pageIndex);
            }
        } catch (Throwable error) {
            throw new IOException("PDFium failed to read page text.", error);
        }
    }

    public synchronized int countTextMatches(int pageIndex, String query) throws IOException {
        if (query == null || query.isEmpty()) return 0;
        return findOccurrenceStarts(extractText(pageIndex), query).size();
    }

    /** Draws every match in yellow and the active match with an orange outline. */
    public synchronized void drawSearchHighlights(
            Bitmap bitmap,
            int pageIndex,
            String query,
            int activeMatchOnPage
    ) throws IOException {
        ensurePage(pageIndex);
        if (bitmap == null || bitmap.isRecycled() || query == null || query.isEmpty()) return;
        try (PdfPage page = document.openPage(pageIndex)) {
            if (page == null) throw new IOException("PDFium could not open the search page.");
            int pageWidth = page.getPageWidthPoint();
            int pageHeight = page.getPageHeightPoint();
            if (pageWidth <= 0 || pageHeight <= 0) return;
            try (PdfTextPage textPage = page.openTextPage()) {
                int characterCount = textPage.textPageCountChars();
                if (characterCount <= 0) return;
                String text = textPage.textPageGetText(0, characterCount);
                List<Integer> starts = findOccurrenceStarts(text, query);
                if (starts.isEmpty()) return;

                Canvas canvas = new Canvas(bitmap);
                Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(Color.argb(105, 255, 214, 10));
                Paint active = new Paint(Paint.ANTI_ALIAS_FLAG);
                active.setStyle(Paint.Style.STROKE);
                active.setStrokeWidth(Math.max(3f, bitmap.getWidth() / 450f));
                active.setColor(Color.rgb(255, 128, 0));
                float scaleX = bitmap.getWidth() / (float) pageWidth;
                float scaleY = bitmap.getHeight() / (float) pageHeight;

                for (int matchIndex = 0; matchIndex < starts.size(); matchIndex++) {
                    int rectCount = textPage.textPageCountRects(
                            starts.get(matchIndex), query.length());
                    for (int rectIndex = 0; rectIndex < rectCount; rectIndex++) {
                        RectF pdfRect = textPage.textPageGetRect(rectIndex);
                        if (pdfRect == null) continue;
                        RectF bitmapRect = mapPdfRect(pdfRect, pageHeight, scaleX, scaleY);
                        canvas.drawRoundRect(bitmapRect, 3f, 3f, fill);
                        if (matchIndex == activeMatchOnPage) {
                            canvas.drawRoundRect(bitmapRect, 3f, 3f, active);
                        }
                    }
                }
            }
        } catch (Throwable error) {
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("PDFium failed to locate search highlights.", error);
        }
    }

    private static List<Integer> findOccurrenceStarts(String text, String query) {
        ArrayList<Integer> starts = new ArrayList<>();
        if (text == null || query == null || query.isEmpty() || text.length() < query.length()) {
            return starts;
        }
        int limit = text.length() - query.length();
        for (int index = 0; index <= limit; ) {
            if (text.regionMatches(true, index, query, 0, query.length())) {
                starts.add(index);
                index += Math.max(1, query.length());
            } else {
                index++;
            }
        }
        return starts;
    }

    private static RectF mapPdfRect(
            RectF source,
            int pageHeight,
            float scaleX,
            float scaleY
    ) {
        float left = Math.min(source.left, source.right) * scaleX;
        float right = Math.max(source.left, source.right) * scaleX;
        float pdfBottom = Math.min(source.top, source.bottom);
        float pdfTop = Math.max(source.top, source.bottom);
        float top = (pageHeight - pdfTop) * scaleY;
        float bottom = (pageHeight - pdfBottom) * scaleY;
        return new RectF(left, top, right, bottom);
    }

    private void ensurePage(int pageIndex) {
        ensureOpen();
        int count = core.getPageCount(document);
        if (pageIndex < 0 || pageIndex >= count) {
            throw new IllegalArgumentException("Page index outside document range.");
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("PDF engine is closed.");
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        try {
            core.closeDocument(document);
        } finally {
            try {
                descriptor.close();
            } catch (IOException ignored) {
                // The native document is already closed.
            }
        }
    }
}
