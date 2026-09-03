package com.bimo.easytoread;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import io.legere.pdfiumandroid.PdfDocument;
import io.legere.pdfiumandroid.PdfiumCore;
import java.io.Closeable;
import java.io.IOException;

/**
 * Bundled PDFium-backed processing boundary for BIMO EasyDocs.
 *
 * All calls are serialized by the owning workspace worker. PDFium itself is
 * packaged in the APK and never downloads a runtime component.
 */
public final class BimoPdfEngine implements Closeable {
    private final ParcelFileDescriptor descriptor;
    private final PdfDocument document;
    private boolean closed;

    private BimoPdfEngine(ParcelFileDescriptor descriptor, PdfDocument document) {
        this.descriptor = descriptor;
        this.document = document;
    }

    public static BimoPdfEngine open(Context context, Uri uri, String password) throws IOException {
        ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(uri, "r");
        if (descriptor == null) throw new IOException("Document provider returned no file descriptor.");
        try {
            PdfiumCore core = new PdfiumCore();
            PdfDocument document = core.newDocument(descriptor, password);
            BimoPdfEngine engine = new BimoPdfEngine(descriptor, document);
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
        return document.getPageCount();
    }

    public synchronized Bitmap renderPage(int pageIndex, int maxWidth) throws IOException {
        ensurePage(pageIndex);
        io.legere.pdfiumandroid.PdfPage page = document.openPage(pageIndex);
        if (page == null) throw new IOException("PDFium returned no page.");
        Bitmap bitmap = null;
        try {
            int widthPoints = page.getPageWidthPoint();
            int heightPoints = page.getPageHeightPoint();
            if (widthPoints <= 0 || heightPoints <= 0) throw new IOException("Invalid PDF page dimensions.");
            int width = Math.max(1, Math.min(maxWidth, widthPoints * 3));
            float scale = width / (float) widthPoints;
            int height = Math.max(1, Math.round(heightPoints * scale));
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.renderPageBitmap(
                    bitmap, 0, 0, width, height, true, false, Color.WHITE, Color.WHITE
            );
            return bitmap;
        } catch (Throwable error) {
            if (bitmap != null) bitmap.recycle();
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("PDFium failed to render page " + (pageIndex + 1) + ".", error);
        } finally {
            page.close();
        }
    }

    public synchronized String extractText(int pageIndex) throws IOException {
        ensurePage(pageIndex);
        try {
            io.legere.pdfiumandroid.PdfPage page = document.openPage(pageIndex);
            if (page == null) return "";
            try (io.legere.pdfiumandroid.PdfTextPage textPage = page.openTextPage()) {
                if (textPage == null) return "";
                int count = textPage.textPageCountChars();
                if (count <= 0) return "";
                String value = textPage.textPageGetText(0, count);
                return value == null ? "" : value;
            } finally {
                page.close();
            }
        } catch (Throwable error) {
            throw new IOException("PDFium failed to read page text.", error);
        }
    }

    private void ensurePage(int pageIndex) {
        ensureOpen();
        int count = document.getPageCount();
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
            document.close();
        } finally {
            try {
                descriptor.close();
            } catch (IOException ignored) {
                // The native document is already closed.
            }
        }
    }
}
