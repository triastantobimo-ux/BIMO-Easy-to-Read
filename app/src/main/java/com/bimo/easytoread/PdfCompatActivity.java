package com.bimo.easytoread;

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
import android.print.PrintManager;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Crash-safe PDF reader backed by the platform PdfRenderer.
 *
 * This is the mandatory entry workspace. AndroidX PDF remains available as an
 * explicit advanced mode only when the device exposes S SDK Extension 13+.
 */
public final class PdfCompatActivity extends AppCompatActivity {
    private static final String EXTRA_SOURCE_URI = "compat_source_uri";
    private static final int REQUEST_SAVE_COPY = 2501;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Uri sourceUri;
    private String displayName;
    private ParcelFileDescriptor descriptor;
    private PdfRenderer renderer;
    private ZoomImageView pageImage;
    private TextView title;
    private TextView status;
    private Button pageButton;
    private Button previousButton;
    private Button nextButton;
    private Button advancedButton;
    private Bitmap displayedBitmap;
    private int currentPage;
    private int pageCount;
    private int renderGeneration;
    private boolean destroyed;

    static Intent createIntent(Context context, Uri uri) {
        return new Intent(context, PdfCompatActivity.class)
                .setData(uri)
                .putExtra(EXTRA_SOURCE_URI, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppPreferences.wrapLanguage(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        AppPreferences.applyTheme(this);
        super.onCreate(state);
        setContentView(R.layout.activity_pdf_compat);

        pageImage = findViewById(R.id.pdfCompatImage);
        title = findViewById(R.id.pdfCompatTitle);
        status = findViewById(R.id.pdfCompatStatus);
        pageButton = findViewById(R.id.buttonPdfCompatPage);
        previousButton = findViewById(R.id.buttonPdfPrevious);
        nextButton = findViewById(R.id.buttonPdfNext);
        advancedButton = findViewById(R.id.buttonPdfAdvanced);
        sourceUri = readUriExtra(getIntent(), EXTRA_SOURCE_URI);
        if (sourceUri == null) sourceUri = getIntent().getData();
        if (sourceUri == null) {
            showFatal(new IOException("Document URI is unavailable."));
            return;
        }

        displayName = resolveDisplayName(sourceUri);
        currentPage = PdfSessionStore.getLastPage(this, sourceUri);
        title.setText(displayName);

        findViewById(R.id.buttonPdfCompatBack).setOnClickListener(view -> finish());
        previousButton.setOnClickListener(view -> showPage(currentPage - 1));
        nextButton.setOnClickListener(view -> showPage(currentPage + 1));
        pageButton.setOnClickListener(view -> showPageJump());
        findViewById(R.id.buttonPdfCompatFit).setOnClickListener(view -> pageImage.resetToFit());
        findViewById(R.id.buttonPdfCompatShare).setOnClickListener(view -> sharePdf());
        findViewById(R.id.buttonPdfCompatOcr).setOnClickListener(view -> openCurrentPageAsOcr());
        findViewById(R.id.buttonPdfCompatSave).setOnClickListener(view -> requestSaveCopy());

        boolean advancedSupported = PdfViewerActivity.supportsAndroidxPdfViewer();
        advancedButton.setVisibility(advancedSupported ? View.VISIBLE : View.GONE);
        advancedButton.setOnClickListener(view -> startActivity(
                PdfViewerActivity.createAdvancedIntent(this, sourceUri)
        ));
        status.setText(advancedSupported
                ? R.string.pdf_compat_ready_advanced
                : R.string.pdf_compat_ready_basic);
        openRenderer();
    }

    private void openRenderer() {
        setBusy(true);
        worker.execute(() -> {
            try {
                ParcelFileDescriptor opened = getContentResolver().openFileDescriptor(sourceUri, "r");
                if (opened == null) throw new IOException("Document provider returned no file descriptor.");
                PdfRenderer openedRenderer = new PdfRenderer(opened);
                int count = openedRenderer.getPageCount();
                if (count <= 0) throw new IOException("PDF contains no pages.");
                descriptor = opened;
                renderer = openedRenderer;
                pageCount = count;
                currentPage = Math.max(0, Math.min(currentPage, pageCount - 1));
                runOnUiThread(() -> {
                    if (destroyed) return;
                    PdfSessionStore.remember(
                            this,
                            sourceUri,
                            displayName,
                            currentPage,
                            hasWritePermission(sourceUri)
                    );
                    updatePageUi();
                    setBusy(false);
                    renderPage(currentPage);
                });
            } catch (Throwable error) {
                runOnUiThread(() -> showFatal(error));
            }
        });
    }

    private void showPage(int page) {
        if (renderer == null || pageCount <= 0) return;
        currentPage = Math.max(0, Math.min(pageCount - 1, page));
        PdfSessionStore.updatePage(this, sourceUri, currentPage);
        updatePageUi();
        renderPage(currentPage);
    }

    private void renderPage(int pageIndex) {
        final int generation = ++renderGeneration;
        setBusy(true);
        worker.execute(() -> {
            Bitmap bitmap = null;
            try (PdfRenderer.Page page = renderer.openPage(pageIndex)) {
                int viewportWidth = Math.max(720, getResources().getDisplayMetrics().widthPixels);
                int targetWidth = Math.min(2200, viewportWidth * 2);
                float scale = targetWidth / (float) Math.max(1, page.getWidth());
                int targetHeight = Math.max(1, Math.round(page.getHeight() * scale));
                bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                Bitmap rendered = bitmap;
                runOnUiThread(() -> {
                    if (destroyed || generation != renderGeneration) {
                        rendered.recycle();
                        return;
                    }
                    Bitmap previous = displayedBitmap;
                    displayedBitmap = rendered;
                    pageImage.setImageBitmap(rendered);
                    if (previous != null && previous != rendered && !previous.isRecycled()) {
                        previous.recycle();
                    }
                    setBusy(false);
                });
            } catch (Throwable error) {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                runOnUiThread(() -> showFatal(error));
            }
        });
    }

    private void updatePageUi() {
        pageButton.setText(getString(R.string.pdf_page_count, currentPage + 1, pageCount));
        previousButton.setEnabled(currentPage > 0);
        nextButton.setEnabled(currentPage + 1 < pageCount);
    }

    private void showPageJump() {
        if (pageCount <= 0) return;
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(getString(R.string.pdf_page_hint, pageCount));
        input.setText(Integer.toString(currentPage + 1));
        input.selectAll();
        new AlertDialog.Builder(this)
                .setTitle(R.string.pdf_go_to_page)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    try {
                        int requested = Integer.parseInt(input.getText().toString().trim());
                        if (requested < 1 || requested > pageCount) throw new NumberFormatException();
                        showPage(requested - 1);
                    } catch (NumberFormatException error) {
                        Toast.makeText(
                                this,
                                getString(R.string.pdf_invalid_page, pageCount),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                })
                .show();
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
        intent.putExtra(MainActivity.EXTRA_PDF_PAGE_INDEX, currentPage);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private void printPdf() {
        if (pageCount <= 0) return;
        PrintManager manager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        manager.print(
                getString(R.string.pdf_print_job, displayName),
                new PdfPrintAdapter(this, sourceUri, displayName, pageCount),
                null
        );
    }

    private void requestSaveCopy() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, copyFileName(displayName));
        startActivityForResult(intent, REQUEST_SAVE_COPY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SAVE_COPY || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        Uri target = data.getData();
        setBusy(true);
        worker.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(sourceUri);
                 OutputStream output = getContentResolver().openOutputStream(target, "wt")) {
                if (input == null || output == null) throw new IOException("Storage provider is unavailable.");
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
                output.flush();
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, R.string.pdf_saved_copy, Toast.LENGTH_LONG).show();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> showNonFatal(error));
            }
        });
    }

    private void setBusy(boolean busy) {
        pageButton.setEnabled(!busy && pageCount > 0);
        advancedButton.setEnabled(!busy);
        if (busy) status.setText(R.string.pdf_loading);
        else status.setText(PdfViewerActivity.supportsAndroidxPdfViewer()
                ? R.string.pdf_compat_ready_advanced
                : R.string.pdf_compat_ready_basic);
    }

    private void showFatal(Throwable error) {
        if (destroyed) return;
        setBusy(false);
        new AlertDialog.Builder(this)
                .setTitle(R.string.pdf_open_failed)
                .setMessage(getString(
                        R.string.pdf_open_failed_detail,
                        safeMessage(error) + "\n\n" + getString(R.string.pdf_password_or_corrupt)
                ))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .show();
    }

    private void showNonFatal(Throwable error) {
        setBusy(false);
        Toast.makeText(
                this,
                getString(R.string.pdf_save_failed, safeMessage(error)),
                Toast.LENGTH_LONG
        ).show();
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
            // URI segment remains a safe fallback title.
        }
        String segment = uri.getLastPathSegment();
        return segment == null ? getString(R.string.pdf_document) : segment;
    }

    private boolean hasWritePermission(Uri uri) {
        return checkUriPermission(
                uri,
                android.os.Process.myPid(),
                android.os.Process.myUid(),
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private static String copyFileName(String name) {
        String base = name == null ? "document" : name.trim();
        if (base.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            base = base.substring(0, base.length() - 4);
        }
        return base + "-copy.pdf";
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error == null ? "Unknown error" : error.getClass().getSimpleName()
                : message;
    }

    @SuppressWarnings("deprecation")
    private static Uri readUriExtra(Intent intent, String key) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(key, Uri.class);
        return intent.getParcelableExtra(key);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        renderGeneration++;
        worker.shutdownNow();
        try {
            if (renderer != null) renderer.close();
        } catch (RuntimeException ignored) {
            // Resource is already closing.
        }
        try {
            if (descriptor != null) descriptor.close();
        } catch (IOException ignored) {
            // Resource is already closing.
        }
        if (displayedBitmap != null && !displayedBitmap.isRecycled()) {
            displayedBitmap.recycle();
        }
        super.onDestroy();
    }
}
