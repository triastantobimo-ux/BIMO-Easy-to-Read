package com.bimo.easytoread;

import android.content.Context;
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
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Sends the original vector PDF to Android's user-selected print service. */
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
    public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
            CancellationSignal cancellationSignal, LayoutResultCallback callback, Bundle extras) {
        if (cancellationSignal.isCanceled()) {
            callback.onLayoutCancelled();
            return;
        }
        callback.onLayoutFinished(new PrintDocumentInfo.Builder(displayName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(pageCount).build(), !newAttributes.equals(oldAttributes));
    }

    @Override
    public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
            CancellationSignal cancellationSignal, WriteResultCallback callback) {
        worker.execute(() -> {
            try (InputStream input = context.getContentResolver().openInputStream(source);
                 FileOutputStream output = new FileOutputStream(destination.getFileDescriptor())) {
                if (input == null) throw new IOException("PDF source is unavailable.");
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (cancellationSignal.isCanceled()) {
                        callback.onWriteCancelled();
                        return;
                    }
                    output.write(buffer, 0, read);
                }
                output.flush();
                callback.onWriteFinished(new PageRange[] { PageRange.ALL_PAGES });
            } catch (Throwable error) {
                callback.onWriteFailed(error.getMessage());
            }
        });
    }

    @Override
    public void onFinish() {
        worker.shutdownNow();
    }
}
