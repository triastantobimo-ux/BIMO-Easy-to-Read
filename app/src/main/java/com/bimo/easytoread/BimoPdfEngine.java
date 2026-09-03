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
            PdfiumCore core = new PdfiumCore(context.getApplicationContext());
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
