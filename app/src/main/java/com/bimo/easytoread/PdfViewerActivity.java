package com.bimo.easytoread;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PdfViewerActivity extends Activity {
    public static final String EXTRA_SOURCE_URI = "source_uri";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ZoomImageView image;
    private TextView pageLabel;
    private Button previous;
    private Button next;
    private Uri sourceUri;
    private ParcelFileDescriptor descriptor;
    private PdfRenderer renderer;
    private Bitmap renderedBitmap;
    private int pageIndex;

    public static Intent createIntent(Context context, Uri uri) {
        return new Intent(context, PdfViewerActivity.class)
                .setData(uri)
                .putExtra(EXTRA_SOURCE_URI, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppPreferences.wrapLanguage(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        AppPreferences.applyTheme(this);
        super.onCreate(state);
        setContentView(R.layout.activity_pdf_viewer);
        sourceUri = readUriExtra(getIntent(), EXTRA_SOURCE_URI);
        if (sourceUri == null) sourceUri = getIntent().getData();
        if (sourceUri == null) {
            Toast.makeText(this, R.string.pdf_open_failed, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        image = findViewById(R.id.pdfPageImage);
        pageLabel = findViewById(R.id.textPdfPage);
        previous = findViewById(R.id.buttonPdfPrevious);
        next = findViewById(R.id.buttonPdfNext);
        ((TextView) findViewById(R.id.pdfTitle)).setText(resolveDisplayName(sourceUri));
        findViewById(R.id.buttonPdfBack).setOnClickListener(view -> finish());
        findViewById(R.id.buttonPdfShare).setOnClickListener(view -> sharePdf());
        findViewById(R.id.buttonPdfOcr).setOnClickListener(view -> openCurrentPageAsOcr());
        previous.setOnClickListener(view -> showPage(pageIndex - 1));
        next.setOnClickListener(view -> showPage(pageIndex + 1));
        findViewById(R.id.buttonPdfResetZoom).setOnClickListener(view -> image.resetToFit());
        openPdf();
    }

    private void openPdf() {
        setBusy(true);
        worker.execute(() -> {
            try {
                descriptor = getContentResolver().openFileDescriptor(sourceUri, "r");
                if (descriptor == null) throw new IOException("PDF descriptor is unavailable.");
                renderer = new PdfRenderer(descriptor);
                runOnUiThread(() -> showPage(0));
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(
                            this,
                            getString(R.string.pdf_open_failed_detail, safeMessage(error)),
                            Toast.LENGTH_LONG
                    ).show();
                    finish();
                });
            }
        });
    }

    private void showPage(int requestedIndex) {
        if (renderer == null) return;
        int count = renderer.getPageCount();
        int target = Math.max(0, Math.min(count - 1, requestedIndex));
        pageIndex = target;
        setBusy(true);
        worker.execute(() -> {
            try (PdfRenderer.Page page = renderer.openPage(target)) {
                float scale = Math.min(3f, 2800f / Math.max(page.getWidth(), page.getHeight()));
                int width = Math.max(1, Math.round(page.getWidth() * scale));
                int height = Math.max(1, Math.round(page.getHeight() * scale));
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                runOnUiThread(() -> acceptRenderedPage(bitmap, target, count));
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(
                            this,
                            getString(R.string.pdf_open_failed_detail, safeMessage(error)),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void acceptRenderedPage(Bitmap bitmap, int index, int count) {
        Bitmap old = renderedBitmap;
        renderedBitmap = bitmap;
        image.setImageBitmap(bitmap);
        if (old != null && old != bitmap && !old.isRecycled()) old.recycle();
        pageLabel.setText(getString(R.string.pdf_page_count, index + 1, count));
        previous.setEnabled(index > 0);
        next.setEnabled(index + 1 < count);
        setBusy(false);
    }

    private void sharePdf() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, sourceUri);
        intent.setClipData(ClipData.newRawUri("BIMO EasyDocs PDF", sourceUri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, getString(R.string.share_document)));
    }

    private void openCurrentPageAsOcr() {
        Intent intent = MainActivity.createEntryIntent(this, MainActivity.ENTRY_OCR);
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, sourceUri);
        intent.putExtra(MainActivity.EXTRA_PDF_PAGE_INDEX, pageIndex);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private void setBusy(boolean busy) {
        findViewById(R.id.pdfProgress).setVisibility(busy ? View.VISIBLE : View.GONE);
        previous.setEnabled(!busy && pageIndex > 0);
        next.setEnabled(!busy && renderer != null && pageIndex + 1 < renderer.getPageCount());
    }

    private String resolveDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[] { OpenableColumns.DISPLAY_NAME },
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                String value = cursor.getString(0);
                if (value != null && !value.trim().isEmpty()) return value;
            }
        } catch (RuntimeException ignored) {
            // The URI itself remains a usable fallback title.
        }
        String segment = uri.getLastPathSegment();
        return segment == null ? getString(R.string.pdf_document) : segment;
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty() ? "Unknown error" : message;
    }

    @SuppressWarnings("deprecation")
    private static Uri readUriExtra(Intent intent, String key) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(key, Uri.class);
        return intent.getParcelableExtra(key);
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        if (renderedBitmap != null && !renderedBitmap.isRecycled()) renderedBitmap.recycle();
        try {
            if (renderer != null) renderer.close();
        } catch (RuntimeException ignored) {
            // Best-effort close during Activity teardown.
        }
        try {
            if (descriptor != null) descriptor.close();
        } catch (IOException ignored) {
            // Best-effort close during Activity teardown.
        }
        super.onDestroy();
    }
}
