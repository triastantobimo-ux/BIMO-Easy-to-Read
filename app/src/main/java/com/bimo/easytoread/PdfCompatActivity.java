package com.bimo.easytoread;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintManager;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Standalone PDF workspace powered by the PDFium binary bundled in this APK. */
public final class PdfCompatActivity extends AppCompatActivity {
    private static final String EXTRA_SOURCE_URI = "bimo_pdf_source_uri";
    private static final int REQUEST_SAVE_COPY = 2501;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Integer> searchMatches = new ArrayList<>();
    private Uri sourceUri;
    private String displayName;
    private BimoPdfEngine engine;
    private ZoomImageView pageImage;
    private TextView title;
    private TextView status;
    private Button pageButton;
    private Button previousButton;
    private Button nextButton;
    private Button bookmarkButton;
    private Bitmap displayedBitmap;
    private int currentPage;
    private int pageCount;
    private int renderGeneration;
    private int searchCursor = -1;
    private boolean destroyed;
    private boolean opening;

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
        bookmarkButton = findViewById(R.id.buttonPdfBookmark);

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
        bookmarkButton.setOnClickListener(view -> toggleBookmark());
        findViewById(R.id.buttonPdfCompatFit).setOnClickListener(view -> pageImage.resetToFit());
        findViewById(R.id.buttonPdfSearch).setOnClickListener(view -> showSearchDialog());
        findViewById(R.id.buttonPdfText).setOnClickListener(view -> showTextLayer());
        findViewById(R.id.buttonPdfCompatShare).setOnClickListener(view -> sharePdf());
        findViewById(R.id.buttonPdfCompatOcr).setOnClickListener(view -> openCurrentPageAsOcr());
        findViewById(R.id.buttonPdfPrint).setOnClickListener(view -> printPdf());
        findViewById(R.id.buttonPdfCompatSave).setOnClickListener(view -> requestSaveCopy());
        status.setText(R.string.pdf_engine_opening);
        openEngine(null, true);
    }

    private void openEngine(String password, boolean offerPassword) {
        if (opening) return;
        opening = true;
        setBusy(true);
        worker.execute(() -> {
            try {
                BimoPdfEngine opened = BimoPdfEngine.open(this, sourceUri, password);
                int count = opened.getPageCount();
                runOnUiThread(() -> {
                    if (destroyed) {
                        opened.close();
                        return;
                    }
                    closeEngine();
                    engine = opened;
                    pageCount = count;
                    currentPage = Math.max(0, Math.min(currentPage, pageCount - 1));
                    opening = false;
                    PdfSessionStore.remember(this, sourceUri, displayName, currentPage,
                            hasWritePermission(sourceUri));
                    updatePageUi();
                    renderPage(currentPage);
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    opening = false;
                    setBusy(false);
                    if (offerPassword) showPasswordDialog(error);
                    else showFatal(error);
                });
            }
        });
    }

    private void showPasswordDialog(Throwable originalError) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(R.string.pdf_password_hint);
        new AlertDialog.Builder(this)
                .setTitle(R.string.pdf_password_title)
                .setMessage(R.string.pdf_password_message)
                .setView(input)
                .setNeutralButton(R.string.pdf_open_error_details,
                        (dialog, which) -> showFatal(originalError))
                .setNegativeButton(R.string.cancel, (dialog, which) -> finish())
                .setPositiveButton(R.string.pdf_open_with_password, (dialog, which) ->
                        openEngine(input.getText().toString(), false))
                .show();
    }

    private void showPage(int page) {
        if (engine == null || pageCount <= 0) return;
        currentPage = Math.max(0, Math.min(pageCount - 1, page));
        PdfSessionStore.updatePage(this, sourceUri, currentPage);
        updatePageUi();
        renderPage(currentPage);
    }

    private void renderPage(int pageIndex) {
        final int generation = ++renderGeneration;
        setBusy(true);
        worker.execute(() -> {
            try {
                int viewportWidth = Math.max(1080, getResources().getDisplayMetrics().widthPixels * 2);
                Bitmap rendered = engine.renderPage(pageIndex, Math.min(3200, viewportWidth));
                runOnUiThread(() -> {
                    if (destroyed || generation != renderGeneration) {
                        rendered.recycle();
                        return;
                    }
                    Bitmap previous = displayedBitmap;
                    displayedBitmap = rendered;
                    pageImage.setImageBitmap(rendered);
                    if (previous != null && previous != rendered && !previous.isRecycled()) previous.recycle();
                    setBusy(false);
                });
            } catch (Throwable error) {
                runOnUiThread(() -> showFatal(error));
            }
        });
    }

    private void updatePageUi() {
        pageButton.setText(getString(R.string.pdf_page_count, currentPage + 1, pageCount));
        previousButton.setEnabled(currentPage > 0);
        nextButton.setEnabled(currentPage + 1 < pageCount);
        boolean bookmarked = PdfSessionStore.isBookmarked(this, sourceUri, currentPage);
        bookmarkButton.setText(bookmarked ? R.string.pdf_bookmarked : R.string.pdf_bookmark);
    }

    private void toggleBookmark() {
        boolean added = PdfSessionStore.toggleBookmark(this, sourceUri, currentPage);
        updatePageUi();
        Toast.makeText(this, getString(
                added ? R.string.pdf_bookmark_added : R.string.pdf_bookmark_removed,
                currentPage + 1
        ), Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(this, getString(R.string.pdf_invalid_page, pageCount),
                                Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void showSearchDialog() {
        if (engine == null) return;
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(R.string.pdf_search_hint);
        new AlertDialog.Builder(this)
                .setTitle(R.string.pdf_search)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.pdf_search, (dialog, which) ->
                        searchDocument(input.getText().toString().trim()))
                .show();
    }

    private void searchDocument(String query) {
        if (query.isEmpty()) return;
        setBusy(true);
        worker.execute(() -> {
            ArrayList<Integer> found = new ArrayList<>();
            try {
                String needle = query.toLowerCase(Locale.ROOT);
                for (int page = 0; page < pageCount; page++) {
                    if (Thread.currentThread().isInterrupted()) return;
                    if (engine.extractText(page).toLowerCase(Locale.ROOT).contains(needle)) found.add(page);
                }
                runOnUiThread(() -> {
                    searchMatches.clear();
                    searchMatches.addAll(found);
                    searchCursor = found.isEmpty() ? -1 : 0;
                    setBusy(false);
                    if (found.isEmpty()) {
                        Toast.makeText(this, R.string.pdf_search_not_found, Toast.LENGTH_LONG).show();
                    } else {
                        status.setText(getString(R.string.pdf_search_found, found.size()));
                        showPage(found.get(0));
                    }
                });
            } catch (Throwable error) {
                runOnUiThread(() -> showNonFatal(error));
            }
        });
    }

    private void showTextLayer() {
        if (engine == null) return;
        setBusy(true);
        worker.execute(() -> {
            try {
                String text = engine.extractText(currentPage).trim();
                runOnUiThread(() -> {
                    setBusy(false);
                    if (text.isEmpty()) {
                        Toast.makeText(this, R.string.pdf_no_text_layer, Toast.LENGTH_LONG).show();
                        return;
                    }
                    EditText content = new EditText(this);
                    content.setText(text);
                    content.setTextIsSelectable(true);
                    content.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.pdf_page_text)
                            .setView(content)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.copy, (dialog, which) -> copyText(text))
                            .show();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> showNonFatal(error));
            }
        });
    }

    private void copyText(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("BIMO PDF text", text));
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    private void sharePdf() {
        Intent intent = new Intent(Intent.ACTION_SEND).setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, sourceUri);
        intent.setClipData(ClipData.newRawUri("BIMO EasyDocs PDF", sourceUri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, getString(R.string.share_document)));
    }

    private void openCurrentPageAsOcr() {
        Intent intent = MainActivity.createEntryIntent(this, MainActivity.ENTRY_OCR);
        intent.setAction(Intent.ACTION_SEND).setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, sourceUri);
        intent.putExtra(MainActivity.EXTRA_PDF_PAGE_INDEX, currentPage);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private void printPdf() {
        if (pageCount <= 0) return;
        PrintManager manager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        manager.print(getString(R.string.pdf_print_job, displayName),
                new PdfPrintAdapter(this, sourceUri, displayName, pageCount), null);
    }

    private void requestSaveCopy() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf");
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
        bookmarkButton.setEnabled(!busy && pageCount > 0);
        if (busy) status.setText(R.string.pdf_loading);
        else status.setText(R.string.pdf_engine_ready);
    }

    private void showFatal(Throwable error) {
        if (destroyed) return;
        setBusy(false);
        new AlertDialog.Builder(this).setTitle(R.string.pdf_open_failed)
                .setMessage(getString(R.string.pdf_open_failed_detail, safeMessage(error)))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish()).show();
    }

    private void showNonFatal(Throwable error) {
        setBusy(false);
        Toast.makeText(this, getString(R.string.pdf_operation_failed, safeMessage(error)),
                Toast.LENGTH_LONG).show();
    }

    private String resolveDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String value = cursor.getString(0);
                if (value != null && !value.trim().isEmpty()) return value;
            }
        } catch (RuntimeException ignored) {
            // URI segment remains a safe local fallback.
        }
        String segment = uri.getLastPathSegment();
        return segment == null ? getString(R.string.pdf_document) : segment;
    }

    private boolean hasWritePermission(Uri uri) {
        return checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(),
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private static String copyFileName(String name) {
        String base = name == null ? "document" : name.trim();
        if (base.toLowerCase(Locale.ROOT).endsWith(".pdf")) base = base.substring(0, base.length() - 4);
        return base + "-copy.pdf";
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error == null ? "Unknown error" : error.getClass().getSimpleName() : message;
    }

    @SuppressWarnings("deprecation")
    private static Uri readUriExtra(Intent intent, String key) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(key, Uri.class);
        return intent.getParcelableExtra(key);
    }

    private void closeEngine() {
        if (engine != null) {
            engine.close();
            engine = null;
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        renderGeneration++;
        worker.shutdownNow();
        closeEngine();
        if (displayedBitmap != null && !displayedBitmap.isRecycled()) displayedBitmap.recycle();
        super.onDestroy();
    }
}
