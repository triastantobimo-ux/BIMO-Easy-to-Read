package com.bimo.easytoread;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Print adapter that honors selected page ranges without modifying the source PDF. */
final class PdfPrintAdapter extends PrintDocumentAdapter {
    private final Context context;
    private final Uri source;
    private final String displayName;
    private final int pageCount;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    PdfPrintAdapter(Context context, Uri source, String displayName, int pageCount) {
        this.context = context.getApplicationContext();
        this.source = source;
        this.displayName = displayName;
        this.pageCount = pageCount;
    }

    @Override
    public void onLayout(
            PrintAttributes oldAttributes,
            PrintAttributes newAttributes,
            CancellationSignal cancellationSignal,
            LayoutResultCallback callback,
            Bundle extras
    ) {
        if (cancellationSignal.isCanceled()) {
            callback.onLayoutCancelled();
            return;
        }
        callback.onLayoutFinished(new PrintDocumentInfo.Builder(displayName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(pageCount)
                .build(), !newAttributes.equals(oldAttributes));
    }

    @Override
    public void onWrite(
            PageRange[] pages,
            ParcelFileDescriptor destination,
            CancellationSignal cancellationSignal,
            WriteResultCallback callback
    ) {
        worker.execute(() -> {
            PdfDocument output = new PdfDocument();
            try (ParcelFileDescriptor input = context.getContentResolver()
                         .openFileDescriptor(source, "r");
                 PdfRenderer renderer = input == null ? null : new PdfRenderer(input);
                 FileOutputStream stream = new FileOutputStream(destination.getFileDescriptor())) {
                if (renderer == null) throw new IOException("PDF source is unavailable.");
                for (int index = 0; index < renderer.getPageCount(); index++) {
                    if (cancellationSignal.isCanceled()) {
                        callback.onWriteCancelled();
                        return;
                    }
                    if (!containsPage(pages, index)) continue;
                    try (PdfRenderer.Page sourcePage = renderer.openPage(index)) {
                        int width = Math.max(1, sourcePage.getWidth());
                        int height = Math.max(1, sourcePage.getHeight());
                        Bitmap bitmap = Bitmap.createBitmap(width * 2, height * 2,
                                Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);
                        sourcePage.render(bitmap, null, null,
                                PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                        PdfDocument.Page page = output.startPage(
                                new PdfDocument.PageInfo.Builder(width, height, index + 1).create()
                        );
                        page.getCanvas().drawBitmap(bitmap, null,
                                new android.graphics.Rect(0, 0, width, height), null);
                        output.finishPage(page);
                        bitmap.recycle();
                    }
                }
                output.writeTo(stream);
                callback.onWriteFinished(pages);
            } catch (Throwable error) {
                callback.onWriteFailed(error.getMessage());
            } finally {
                output.close();
            }
        });
    }

    @Override
    public void onFinish() {
        worker.shutdownNow();
    }

    private static boolean containsPage(PageRange[] ranges, int page) {
        if (ranges == null || ranges.length == 0) return true;
        for (PageRange range : ranges) {
            if (range == PageRange.ALL_PAGES
                    || (page >= range.getStart() && page <= range.getEnd())) return true;
        }
        return false;
    }
}

