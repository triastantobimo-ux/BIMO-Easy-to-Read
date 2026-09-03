package com.bimo.easytoread;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.ext.SdkExtensions;
import android.print.PrintManager;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.util.SparseArray;
import android.view.Menu;
import android.view.SubMenu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.pdf.ExperimentalPdfApi;
import androidx.pdf.PdfDocument;
import androidx.pdf.PdfWriteHandle;
import androidx.pdf.view.PdfView;
import androidx.pdf.viewer.fragment.PdfViewerFragment;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Focused PDF workspace. PDF reading and editing do not require OCR initialization. */
@OptIn(markerClass = ExperimentalPdfApi.class)
@SuppressLint("NewApi") // Every extension API path is protected by supportsPdfEditing().
public final class PdfViewerActivity extends AppCompatActivity implements PdfWorkspaceHost {
    public static final String EXTRA_SOURCE_URI = "source_uri";
    private static final String FRAGMENT_TAG = "bimo_pdf_workspace";
    private static final int REQUEST_SAVE_COPY = 2401;
    private static final int MENU_JUMP = 1;
    private static final int MENU_BOOKMARKS = 2;
    private static final int MENU_PRINT = 3;
    private static final int MENU_OCR = 4;
    private static final int MENU_SAVE = 5;
    private static final int MENU_SAVE_COPY = 6;
    private static final int MENU_SIGNATURE_INFO = 7;
    private static final int MENU_EDIT_CONTENT = 8;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Uri sourceUri;
    private String displayName;
    private Fragment pdfFragment;
    private PdfView pdfView;
    private PdfDocument pdfDocument;
    private PdfView.OnViewportChangedListener viewportListener;
    private TextView pageButton;
    private TextView zoomButton;
    private TextView meta;
    private TextView status;
    private ImageButton bookmarkButton;
    private Button annotateButton;
    private Button saveButton;
    private int currentPage;
    private int pageCount;
    private boolean documentLoaded;
    private boolean editorSupported;
    private boolean pendingOverwrite;
    private Uri pendingSaveUri;

    public static Intent createIntent(Context context, Uri uri) {
        return new Intent(context, PdfViewerActivity.class)
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
        setContentView(R.layout.activity_pdf_viewer);

        sourceUri = readUriExtra(getIntent(), EXTRA_SOURCE_URI);
        if (sourceUri == null) sourceUri = getIntent().getData();
        if (sourceUri == null) {
            Toast.makeText(this, R.string.pdf_open_failed, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        displayName = resolveDisplayName(sourceUri);
        editorSupported = supportsPdfEditing();
        currentPage = PdfSessionStore.getLastPage(this, sourceUri);
        pageButton = findViewById(R.id.buttonPdfPage);
        zoomButton = findViewById(R.id.buttonPdfZoom);
        meta = findViewById(R.id.pdfMeta);
        status = findViewById(R.id.pdfStatus);
        bookmarkButton = findViewById(R.id.buttonPdfBookmark);
        annotateButton = findViewById(R.id.buttonPdfAnnotate);
        saveButton = findViewById(R.id.buttonPdfSave);
        ((TextView) findViewById(R.id.pdfTitle)).setText(displayName);

        findViewById(R.id.buttonPdfBack).setOnClickListener(view -> handleClose());
        findViewById(R.id.buttonPdfSearch).setOnClickListener(view -> toggleSearch());
        bookmarkButton.setOnClickListener(view -> toggleBookmark());
        findViewById(R.id.buttonPdfMore).setOnClickListener(this::showReaderMenu);
        pageButton.setOnClickListener(view -> showPageJump());
        findViewById(R.id.buttonPdfZoomOut).setOnClickListener(view -> changeZoom(0.8f));
        zoomButton.setOnClickListener(view -> resetZoom());
        findViewById(R.id.buttonPdfZoomIn).setOnClickListener(view -> changeZoom(1.25f));
        findViewById(R.id.buttonPdfLayout).setOnClickListener(view -> togglePageLayout());
        findViewById(R.id.buttonPdfShare).setOnClickListener(view -> sharePdf());
        annotateButton.setOnClickListener(view -> toggleAnnotation());
        saveButton.setOnClickListener(view -> requestSaveCopy());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (pdfFragment instanceof PdfViewerFragment
                        && ((PdfViewerFragment) pdfFragment).isTextSearchActive()) {
                    ((PdfViewerFragment) pdfFragment).setTextSearchActive(false);
                    return;
                }
                handleClose();
            }
        });

        installFragment(state);
        updateBookmarkUi();
        setBusy(true);
    }

    private void installFragment(Bundle state) {
        Fragment restored = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
        if (restored != null) {
            pdfFragment = restored;
        } else {
            pdfFragment = editorSupported ? new EditablePdfFragment() : new ReadOnlyPdfFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.pdfFragmentContainer, pdfFragment, FRAGMENT_TAG)
                    .commitNow();
        }
        if (pdfFragment instanceof PdfViewerFragment) {
            ((PdfViewerFragment) pdfFragment).setDocumentUri(sourceUri);
        }
    }

    @Override
    public void onPdfViewCreated(PdfView view) {
        pdfView = view;
        pdfView.setMinZoom(0.5f);
        pdfView.setMaxZoom(25f);
        pdfView.setVerticalPageSpacing(dp(12));
        pdfView.setFastScrollVerticalThumbDrawable(getDrawable(R.drawable.scrollbar_thumb_vertical));
        viewportListener = new PdfView.OnViewportChangedListener() {
            @Override
            public void onViewportChanged(
                    int firstVisiblePage,
                    int visiblePagesCount,
                    @NonNull SparseArray<RectF> pageLocations,
                    float zoomLevel
            ) {
                currentPage = Math.max(0, firstVisiblePage);
                PdfSessionStore.updatePage(PdfViewerActivity.this, sourceUri, currentPage);
                updateReaderMetrics(zoomLevel);
                updateBookmarkUi();
            }
        };
        pdfView.addOnViewportChangedListener(viewportListener);
        restorePositionWhenReady();
    }

    @Override
    public void onPdfDocumentLoaded(PdfDocument document) {
        pdfDocument = document;
        pageCount = Math.max(1, document.getPageCount());
        currentPage = Math.min(currentPage, pageCount - 1);
        documentLoaded = true;
        boolean writable = hasWritePermission(sourceUri) || PdfSessionStore.isWritable(this, sourceUri);
        PdfSessionStore.remember(this, sourceUri, displayName, currentPage, writable);
        meta.setText(getString(R.string.pdf_ready_meta, pageCount));
        status.setText(editorSupported
                ? R.string.pdf_edit_capability
                : R.string.pdf_read_capability);
        setBusy(false);
        restorePositionWhenReady();
    }

    @Override
    public void onPdfDocumentError(Throwable error) {
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

    @Override
    public void onPdfWriteReady(PdfWriteHandle handle) {
        final Uri destination = pendingSaveUri;
        final boolean overwrite = pendingOverwrite;
        pendingSaveUri = null;
        pendingOverwrite = false;
        if (destination == null && !overwrite) {
            closeQuietly(handle);
            setBusy(false);
            return;
        }
        worker.execute(() -> {
            File temporary = null;
            try {
                if (overwrite) {
                    temporary = File.createTempFile("pdf-save-", ".pdf", getCacheDir());
                    try (ParcelFileDescriptor target = ParcelFileDescriptor.open(
                            temporary,
                            ParcelFileDescriptor.MODE_CREATE
                                    | ParcelFileDescriptor.MODE_TRUNCATE
                                    | ParcelFileDescriptor.MODE_READ_WRITE
                    )) {
                        PdfWriteBridge.writeBlocking(handle, target);
                    }
                    try (InputStream input = new FileInputStream(temporary);
                         OutputStream output = requireOutput(sourceUri)) {
                        copy(input, output);
                    }
                } else {
                    try (ParcelFileDescriptor target = getContentResolver()
                            .openFileDescriptor(destination, "w")) {
                        if (target == null) throw new IOException("Destination is unavailable.");
                        PdfWriteBridge.writeBlocking(handle, target);
                    }
                }
                closeQuietly(handle);
                File cleanup = temporary;
                runOnUiThread(() -> {
                    if (cleanup != null) cleanup.delete();
                    setBusy(false);
                    Toast.makeText(
                            this,
                            overwrite ? R.string.pdf_saved : R.string.pdf_saved_copy,
                            Toast.LENGTH_LONG
                    ).show();
                });
            } catch (Throwable error) {
                closeQuietly(handle);
                if (temporary != null) temporary.delete();
                runOnUiThread(() -> showSaveError(error));
            }
        });
    }

    @Override
    public void onPdfWriteFailed(Throwable error) {
        pendingSaveUri = null;
        pendingOverwrite = false;
        showSaveError(error);
    }

    @Override
    public void onPdfEditModeChanged(boolean enabled) {
        annotateButton.setSelected(enabled);
        annotateButton.setText(enabled ? R.string.pdf_annotation_off : R.string.pdf_annotate);
        status.setText(enabled ? R.string.pdf_annotation_on : R.string.pdf_annotation_off);
    }

    private void restorePositionWhenReady() {
        if (!documentLoaded || pdfView == null || pageCount <= 0) return;
        int target = Math.min(currentPage, pageCount - 1);
        pdfView.postDelayed(() -> pdfView.scrollToPage(target), 180L);
        updateReaderMetrics(pdfView.getZoom());
    }

    private void toggleSearch() {
        if (!documentLoaded || !(pdfFragment instanceof PdfViewerFragment)) return;
        PdfViewerFragment viewer = (PdfViewerFragment) pdfFragment;
        viewer.setTextSearchActive(!viewer.isTextSearchActive());
    }

    private void toggleBookmark() {
        if (!documentLoaded) return;
        boolean added = PdfSessionStore.toggleBookmark(this, sourceUri, currentPage);
        Toast.makeText(
                this,
                getString(added ? R.string.pdf_bookmark_added : R.string.pdf_bookmark_removed,
                        currentPage + 1),
                Toast.LENGTH_SHORT
        ).show();
        updateBookmarkUi();
    }

    private void updateBookmarkUi() {
        if (bookmarkButton == null || sourceUri == null) return;
        boolean selected = PdfSessionStore.isBookmarked(this, sourceUri, currentPage);
        bookmarkButton.setSelected(selected);
        bookmarkButton.setColorFilter(getColor(selected
                ? R.color.accent_primary
                : R.color.text_primary));
    }

    private void showReaderMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu menu = popup.getMenu();
        menu.add(Menu.NONE, MENU_JUMP, 0, R.string.pdf_go_to_page);
        SubMenu bookmarks = menu.addSubMenu(Menu.NONE, MENU_BOOKMARKS, 1, R.string.pdf_bookmarks);
        List<Integer> savedPages = PdfSessionStore.getBookmarks(this, sourceUri);
        if (savedPages.isEmpty()) {
            bookmarks.add(R.string.pdf_no_bookmarks).setEnabled(false);
        } else {
            for (int page : savedPages) {
                bookmarks.add(getString(R.string.pdf_recent_page, page + 1))
                        .setOnMenuItemClickListener(item -> {
                            goToPage(page);
                            return true;
                        });
            }
        }
        menu.add(Menu.NONE, MENU_SAVE, 2, R.string.pdf_save);
        menu.add(Menu.NONE, MENU_SAVE_COPY, 3, R.string.pdf_save_copy);
        menu.add(Menu.NONE, MENU_PRINT, 4, R.string.pdf_print);
        menu.add(Menu.NONE, MENU_OCR, 5, R.string.pdf_ocr_page);
        menu.add(Menu.NONE, MENU_EDIT_CONTENT, 6, R.string.pdf_object_editor);
        menu.add(Menu.NONE, MENU_SIGNATURE_INFO, 7, R.string.pdf_visual_signature_note);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case MENU_JUMP:
                    showPageJump();
                    return true;
                case MENU_SAVE:
                    saveOriginal();
                    return true;
                case MENU_SAVE_COPY:
                    requestSaveCopy();
                    return true;
                case MENU_PRINT:
                    printPdf();
                    return true;
                case MENU_OCR:
                    openCurrentPageAsOcr();
                    return true;
                case MENU_EDIT_CONTENT:
                    openObjectEditor();
                    return true;
                case MENU_SIGNATURE_INFO:
                    new AlertDialog.Builder(this)
                            .setMessage(R.string.pdf_visual_signature_note)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    private void showPageJump() {
        if (!documentLoaded) return;
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(getString(R.string.pdf_page_hint, pageCount));
        input.setText(Integer.toString(currentPage + 1));
        input.setSelectAllOnFocus(true);
        int padding = dp(20);
        input.setPadding(padding, padding / 2, padding, padding / 2);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.pdf_go_to_page)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    try {
                        int requested = Integer.parseInt(input.getText().toString().trim());
                        if (requested < 1 || requested > pageCount) throw new NumberFormatException();
                        goToPage(requested - 1);
                        dialog.dismiss();
                    } catch (NumberFormatException error) {
                        input.setError(getString(R.string.pdf_invalid_page, pageCount));
                    }
                }));
        dialog.show();
    }

    private void goToPage(int page) {
        if (pdfView == null || pageCount <= 0) return;
        currentPage = Math.max(0, Math.min(pageCount - 1, page));
        pdfView.scrollToPage(currentPage);
        updateReaderMetrics(pdfView.getZoom());
    }

    private void changeZoom(float multiplier) {
        if (pdfView == null) return;
        pdfView.setZoom(pdfView.getZoom() * multiplier);
        updateReaderMetrics(pdfView.getZoom());
    }

    private void resetZoom() {
        if (pdfView == null) return;
        pdfView.setZoom(1f);
        updateReaderMetrics(pdfView.getZoom());
    }

    private void togglePageLayout() {
        if (pdfView == null) return;
        int next = pdfView.getPagesPerRow() == 1 ? 2 : 1;
        pdfView.setPagesPerRow(next);
        ((Button) findViewById(R.id.buttonPdfLayout)).setText(next == 1
                ? R.string.pdf_single_page_layout
                : R.string.pdf_two_page_layout);
    }

    private void updateReaderMetrics(float zoom) {
        if (pageButton != null && pageCount > 0) {
            pageButton.setText(getString(R.string.pdf_page_count, currentPage + 1, pageCount));
        }
        if (zoomButton != null) {
            zoomButton.setText(getString(R.string.pdf_zoom_value, Math.round(zoom * 100f)));
        }
    }

    private void toggleAnnotation() {
        if (!editorSupported || !(pdfFragment instanceof EditablePdfFragment)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.pdf_edit_unavailable_title)
                    .setMessage(R.string.pdf_edit_unavailable_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        EditablePdfFragment editor = (EditablePdfFragment) pdfFragment;
        editor.setEditModeEnabled(!editor.isEditModeEnabled());
    }

    private void saveOriginal() {
        if (!hasWritePermission(sourceUri) && !PdfSessionStore.isWritable(this, sourceUri)) {
            Toast.makeText(this, R.string.pdf_save_original_unavailable, Toast.LENGTH_LONG).show();
            requestSaveCopy();
            return;
        }
        if (!hasUnsavedChanges()) {
            Toast.makeText(this, R.string.pdf_saved, Toast.LENGTH_SHORT).show();
            return;
        }
        pendingOverwrite = true;
        pendingSaveUri = null;
        setBusy(true);
        ((EditablePdfFragment) pdfFragment).applyDraftEdits();
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
        if (hasUnsavedChanges()) {
            pendingSaveUri = target;
            pendingOverwrite = false;
            setBusy(true);
            ((EditablePdfFragment) pdfFragment).applyDraftEdits();
        } else {
            copyOriginalTo(target);
        }
    }

    private boolean hasUnsavedChanges() {
        return editorSupported
                && pdfFragment instanceof EditablePdfFragment
                && ((EditablePdfFragment) pdfFragment).hasUnsavedChanges();
    }

    private void copyOriginalTo(Uri target) {
        setBusy(true);
        worker.execute(() -> {
            try (InputStream input = requireInput(sourceUri);
                 OutputStream output = requireOutput(target)) {
                copy(input, output);
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, R.string.pdf_saved_copy, Toast.LENGTH_LONG).show();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> showSaveError(error));
            }
        });
    }

    private void sharePdf() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, sourceUri);
        intent.setClipData(ClipData.newRawUri("BIMO EasyDocs PDF", sourceUri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, getString(R.string.share_document)));
    }

    private void printPdf() {
        if (!documentLoaded) return;
        PrintManager manager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        manager.print(
                getString(R.string.pdf_print_job, displayName),
                new PdfPrintAdapter(this, sourceUri, displayName, pageCount),
                null
        );
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

    private void openObjectEditor() {
        if (!PdfObjectEditorActivity.supportsObjectEditing()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.pdf_object_editor)
                    .setMessage(R.string.pdf_object_editor_unavailable)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        startActivity(PdfObjectEditorActivity.createIntent(
                this,
                sourceUri,
                currentPage,
                displayName
        ));
    }

    private void handleClose() {
        if (!hasUnsavedChanges()) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.pdf_unsaved_title)
                .setMessage(R.string.pdf_unsaved_message)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.pdf_discard, (dialog, which) -> finish())
                .setPositiveButton(R.string.pdf_save_copy, (dialog, which) -> requestSaveCopy())
                .show();
    }

    private void setBusy(boolean busy) {
        saveButton.setEnabled(!busy);
        annotateButton.setEnabled(!busy);
        if (busy || !documentLoaded) {
            meta.setText(R.string.pdf_loading);
        } else {
            meta.setText(getString(R.string.pdf_ready_meta, pageCount));
        }
    }

    private void showSaveError(Throwable error) {
        setBusy(false);
        Toast.makeText(
                this,
                getString(R.string.pdf_save_failed, safeMessage(error)),
                Toast.LENGTH_LONG
        ).show();
    }

    private boolean hasWritePermission(Uri uri) {
        return checkUriPermission(
                uri,
                android.os.Process.myPid(),
                android.os.Process.myUid(),
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private InputStream requireInput(Uri uri) throws IOException {
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) throw new IOException("Source is unavailable.");
        return input;
    }

    private OutputStream requireOutput(Uri uri) throws IOException {
        OutputStream output = getContentResolver().openOutputStream(uri, "wt");
        if (output == null) throw new IOException("Destination is unavailable.");
        return output;
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
        output.flush();
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
            // URI segment remains an evidence-preserving title fallback.
        }
        String segment = uri.getLastPathSegment();
        return segment == null ? getString(R.string.pdf_document) : segment;
    }

    private static boolean supportsPdfEditing() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 18;
    }

    private static String copyFileName(String name) {
        String base = name == null ? "document" : name.trim();
        if (base.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            base = base.substring(0, base.length() - 4);
        }
        return base + "-edited.pdf";
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty() ? "Unknown error" : message;
    }

    private static void closeQuietly(PdfWriteHandle handle) {
        if (handle == null) return;
        try {
            handle.close();
        } catch (IOException ignored) {
            // The write result has already been reported.
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @SuppressWarnings("deprecation")
    private static Uri readUriExtra(Intent intent, String key) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(key, Uri.class);
        return intent.getParcelableExtra(key);
    }

    @Override
    protected void onDestroy() {
        if (pdfView != null && viewportListener != null) {
            pdfView.removeOnViewportChangedListener(viewportListener);
        }
        worker.shutdownNow();
        super.onDestroy();
    }
}

