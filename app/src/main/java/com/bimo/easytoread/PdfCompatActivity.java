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
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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
    private final List<SearchHit> searchMatches = new ArrayList<>();
    private Uri sourceUri;
    private String displayName;
    private BimoPdfEngine engine;
    private ZoomImageView pageImage;
    private TextView title;
    private TextView status;
    private LinearLayout searchBar;
    private EditText searchInput;
    private TextView searchCounter;
    private ImageButton searchPreviousButton;
    private ImageButton searchNextButton;
    private Button pageButton;
    private ImageButton previousButton;
    private ImageButton nextButton;
    private ImageButton bookmarkButton;
    private Bitmap displayedBitmap;
    private int currentPage;
    private int pageCount;
    private int renderGeneration;
    private int searchGeneration;
    private int searchCursor = -1;
    private String activeSearchQuery = "";
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
        searchBar = findViewById(R.id.pdfSearchBar);
        searchInput = findViewById(R.id.pdfSearchInput);
        searchCounter = findViewById(R.id.pdfSearchCounter);
        searchPreviousButton = findViewById(R.id.buttonPdfSearchPrevious);
        searchNextButton = findViewById(R.id.buttonPdfSearchNext);
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
        findViewById(R.id.buttonPdfSearch).setOnClickListener(view -> {
            if (searchBar.getVisibility() == View.VISIBLE) {
                searchDocument(searchInput.getText().toString().trim());
            } else {
                openSearchBar();
            }
        });
        findViewById(R.id.buttonPdfSearchClose).setOnClickListener(view -> closeSearchBar());
        searchPreviousButton.setOnClickListener(view -> moveSearchResult(-1));
        searchNextButton.setOnClickListener(view -> moveSearchResult(1));
        searchInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchDocument(searchInput.getText().toString().trim());
                return true;
            }
            return false;
        });
        pageImage.setOnPageSwipeListener(new ZoomImageView.OnPageSwipeListener() {
            @Override
            public void onNextPage() {
                showPage(currentPage + 1);
            }

            @Override
            public void onPreviousPage() {
                showPage(currentPage - 1);
            }
        });
        findViewById(R.id.buttonPdfText).setOnClickListener(view -> showTextLayer());
        findViewById(R.id.buttonPdfCompatShare).setOnClickListener(view -> sharePdf());
        findViewById(R.id.buttonPdfCompatOcr).setOnClickListener(view -> openCurrentPageAsOcr());
        findViewById(R.id.buttonPdfCompatSave).setOnClickListener(view -> requestSaveCopy());
        findViewById(R.id.buttonPdfMore).setOnClickListener(this::showMoreMenu);
        status.setText(R.string.pdf_loading);
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
                int activeOnPage = activeMatchOnPage(pageIndex);
                if (!activeSearchQuery.isEmpty()) {
                    engine.drawSearchHighlights(rendered, pageIndex,
                            activeSearchQuery, activeOnPage);
                }
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
        bookmarkButton.setSelected(bookmarked);
        bookmarkButton.setAlpha(bookmarked ? 1f : 0.72f);
        bookmarkButton.setContentDescription(getString(
                bookmarked ? R.string.pdf_bookmarked : R.string.pdf_bookmark));
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

    private void openSearchBar() {
        if (engine == null) return;
        searchBar.setVisibility(View.VISIBLE);
        searchInput.requestFocus();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        keyboard.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void closeSearchBar() {
        searchGeneration++;
        activeSearchQuery = "";
        searchMatches.clear();
        searchCursor = -1;
        searchCounter.setText(R.string.pdf_search_counter_empty);
        searchPreviousButton.setEnabled(false);
        searchNextButton.setEnabled(false);
        searchBar.setVisibility(View.GONE);
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        if (engine != null) renderPage(currentPage);
    }

    private void searchDocument(String query) {
        if (query.isEmpty()) return;
        final int generation = ++searchGeneration;
        activeSearchQuery = query;
        setBusy(true);
        worker.execute(() -> {
            ArrayList<SearchHit> found = new ArrayList<>();
            try {
                for (int page = 0; page < pageCount; page++) {
                    if (Thread.currentThread().isInterrupted()) return;
                    int count = engine.countTextMatches(page, query);
                    for (int localIndex = 0; localIndex < count; localIndex++) {
                        found.add(new SearchHit(page, localIndex));
                    }
                }
                runOnUiThread(() -> {
                    if (destroyed || generation != searchGeneration) return;
                    searchMatches.clear();
                    searchMatches.addAll(found);
                    searchCursor = found.isEmpty() ? -1 : 0;
                    setBusy(false);
                    updateSearchCounter();
                    if (found.isEmpty()) {
                        activeSearchQuery = query;
                        Toast.makeText(this, R.string.pdf_search_scanned_hint, Toast.LENGTH_LONG).show();
                        renderPage(currentPage);
                    } else {
                        Toast.makeText(this, getString(R.string.pdf_search_found, found.size()),
                                Toast.LENGTH_SHORT).show();
                        showSearchResult();
                    }
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    if (!destroyed && generation == searchGeneration) showNonFatal(error);
                });
            }
        });
    }

    private void moveSearchResult(int direction) {
        if (searchMatches.isEmpty()) return;
        searchCursor = (searchCursor + direction + searchMatches.size()) % searchMatches.size();
        updateSearchCounter();
        showSearchResult();
    }

    private void showSearchResult() {
        if (searchCursor < 0 || searchCursor >= searchMatches.size()) return;
        SearchHit hit = searchMatches.get(searchCursor);
        currentPage = hit.pageIndex;
        PdfSessionStore.updatePage(this, sourceUri, currentPage);
        updatePageUi();
        renderPage(currentPage);
    }

    private int activeMatchOnPage(int pageIndex) {
        if (searchCursor < 0 || searchCursor >= searchMatches.size()) return -1;
        SearchHit hit = searchMatches.get(searchCursor);
        return hit.pageIndex == pageIndex ? hit.localIndex : -1;
    }

    private void updateSearchCounter() {
        boolean hasResults = !searchMatches.isEmpty();
        searchCounter.setText(hasResults
                ? getString(R.string.pdf_search_counter, searchCursor + 1, searchMatches.size())
                : getString(R.string.pdf_search_counter_empty));
        searchPreviousButton.setEnabled(hasResults);
        searchNextButton.setEnabled(hasResults);
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.pdf_more_print)
                .setIcon(R.drawable.ic_export);
        menu.getMenu().add(0, 2, 1, R.string.pdf_more_save_copy)
                .setIcon(R.drawable.ic_export);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                printPdf();
                return true;
            }
            if (item.getItemId() == 2) {
                requestSaveCopy();
                return true;
            }
            return false;
        });
        menu.show();
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
        previousButton.setEnabled(!busy && currentPage > 0);
        nextButton.setEnabled(!busy && currentPage + 1 < pageCount);
        status.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) status.setText(R.string.pdf_loading);
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

    private static final class SearchHit {
        final int pageIndex;
        final int localIndex;

        SearchHit(int pageIndex, int localIndex) {
            this.pageIndex = pageIndex;
            this.localIndex = localIndex;
        }
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
