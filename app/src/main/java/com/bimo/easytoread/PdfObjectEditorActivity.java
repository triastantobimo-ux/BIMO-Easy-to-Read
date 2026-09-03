package com.bimo.easytoread;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.component.PdfPageImageObject;
import android.graphics.pdf.component.PdfPageObject;
import android.graphics.pdf.component.PdfPageObjectType;
import android.graphics.pdf.component.PdfPageTextObject;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.ext.SdkExtensions;
import android.text.InputType;
import android.util.Pair;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Advanced, capability-gated editor for supported text and image page objects. */
@SuppressLint("NewApi") // Class is launched only when supportsObjectEditing() is true.
public final class PdfObjectEditorActivity extends AppCompatActivity {
    public static final String EXTRA_URI = "pdf_object_uri";
    public static final String EXTRA_PAGE = "pdf_object_page";
    public static final String EXTRA_NAME = "pdf_object_name";
    private static final int REQUEST_IMAGE = 2501;
    private static final int REQUEST_SAVE = 2502;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Uri sourceUri;
    private int pageIndex;
    private String displayName;
    private File workingFile;
    private PdfRenderer renderer;
    private PdfRenderer.Page page;
    private PdfObjectCanvasView preview;
    private TextView status;
    private Button deleteButton;
    private Pair<Integer, PdfPageObject> selectedObject;
    private PointF insertionPoint;
    private boolean changed;

    public static Intent createIntent(Context context, Uri uri, int page, String name) {
        return new Intent(context, PdfObjectEditorActivity.class)
                .putExtra(EXTRA_URI, uri)
                .putExtra(EXTRA_PAGE, page)
                .putExtra(EXTRA_NAME, name)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    public static boolean supportsObjectEditing() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 19;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppPreferences.wrapLanguage(base));
    }

    @Override
    protected void onCreate(@Nullable Bundle state) {
        AppPreferences.applyTheme(this);
        super.onCreate(state);
        if (!supportsObjectEditing()) {
            Toast.makeText(this, R.string.pdf_object_editor_unavailable, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_pdf_object_editor);
        sourceUri = readUriExtra(getIntent(), EXTRA_URI);
        pageIndex = Math.max(0, getIntent().getIntExtra(EXTRA_PAGE, 0));
        displayName = getIntent().getStringExtra(EXTRA_NAME);
        if (sourceUri == null) {
            finish();
            return;
        }

        preview = findViewById(R.id.pdfObjectPreview);
        status = findViewById(R.id.pdfObjectStatus);
        deleteButton = findViewById(R.id.buttonPdfObjectDelete);
        ((TextView) findViewById(R.id.pdfObjectTitle)).setText(displayName);
        findViewById(R.id.buttonPdfObjectBack).setOnClickListener(view -> requestClose());
        findViewById(R.id.buttonPdfObjectAddImage).setOnClickListener(view -> chooseImage());
        deleteButton.setOnClickListener(view -> deleteSelected());
        findViewById(R.id.buttonPdfObjectSave).setOnClickListener(view -> requestSave());
        preview.setOnPdfPointTapListener(this::selectAt);
        deleteButton.setEnabled(false);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                requestClose();
            }
        });
        loadDocument();
    }

    private void loadDocument() {
        setBusy(true, getString(R.string.pdf_object_loading));
        worker.execute(() -> {
            try {
                workingFile = File.createTempFile("pdf-object-", ".pdf", getCacheDir());
                try (InputStream input = requireInput(sourceUri);
                     FileOutputStream output = new FileOutputStream(workingFile)) {
                    copy(input, output);
                }
                ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                        workingFile, ParcelFileDescriptor.MODE_READ_WRITE);
                renderer = new PdfRenderer(descriptor);
                pageIndex = Math.min(pageIndex, Math.max(0, renderer.getPageCount() - 1));
                page = renderer.openPage(pageIndex);
                insertionPoint = new PointF(page.getWidth() / 2f, page.getHeight() / 2f);
                renderPreview();
            } catch (Throwable error) {
                runOnUiThread(() -> showFatal(error));
            }
        });
    }

    private void renderPreview() {
        int width = Math.min(1800, Math.max(720, page.getWidth() * 2));
        int height = Math.max(1, Math.round((float) width * page.getHeight() / page.getWidth()));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(android.graphics.Color.WHITE);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        int pageWidth = page.getWidth();
        int pageHeight = page.getHeight();
        runOnUiThread(() -> {
            preview.setPdfPageSize(pageWidth, pageHeight);
            preview.setImageBitmap(bitmap);
            setBusy(false, getString(R.string.pdf_object_tap_instruction));
        });
    }

    private void selectAt(PointF point) {
        insertionPoint = point;
        setBusy(true, getString(R.string.pdf_object_inspecting));
        worker.execute(() -> {
            try {
                Pair<Integer, PdfPageObject> found = page.getTopPageObjectAtPosition(
                        point, new int[] {PdfPageObjectType.TEXT, PdfPageObjectType.IMAGE});
                selectedObject = found;
                runOnUiThread(() -> presentSelection(found));
            } catch (Throwable error) {
                runOnUiThread(() -> showError(error));
            }
        });
    }

    private void presentSelection(Pair<Integer, PdfPageObject> found) {
        deleteButton.setEnabled(found != null);
        if (found == null) {
            setBusy(false, getString(R.string.pdf_object_none));
        } else if (found.second instanceof PdfPageTextObject) {
            editTextObject(found.first, (PdfPageTextObject) found.second);
        } else {
            setBusy(false, getString(R.string.pdf_object_image_selected));
        }
    }

    private void editTextObject(int objectId, PdfPageTextObject object) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setText(object.getText());
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle(R.string.pdf_object_edit_text)
                .setMessage(R.string.pdf_object_font_preserved_note)
                .setView(input)
                .setNegativeButton(R.string.cancel, (dialog, which) ->
                        setBusy(false, getString(R.string.pdf_object_text_selected)))
                .setPositiveButton(R.string.pdf_object_apply, (dialog, which) -> {
                    String value = input.getText().toString();
                    setBusy(true, getString(R.string.pdf_object_applying));
                    worker.execute(() -> {
                        try {
                            object.setText(value);
                            if (!page.updatePageObject(objectId, object)) {
                                throw new IOException("PDF object update was rejected.");
                            }
                            changed = true;
                            selectedObject = new Pair<>(objectId, object);
                            renderPreview();
                        } catch (Throwable error) {
                            runOnUiThread(() -> showError(error));
                        }
                    });
                })
                .show();
    }

    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQUEST_IMAGE) insertImage(data.getData());
        if (requestCode == REQUEST_SAVE) writeCopy(data.getData());
    }

    private void insertImage(Uri imageUri) {
        setBusy(true, getString(R.string.pdf_object_adding_image));
        worker.execute(() -> {
            try (InputStream input = requireInput(imageUri)) {
                Bitmap decoded = BitmapFactory.decodeStream(input);
                if (decoded == null) throw new IOException("Image cannot be decoded.");
                Bitmap bitmap = decoded.getConfig() == Bitmap.Config.ARGB_8888
                        ? decoded : decoded.copy(Bitmap.Config.ARGB_8888, false);
                float targetWidth = Math.min(page.getWidth() * 0.35f, bitmap.getWidth());
                float scale = targetWidth / Math.max(1f, bitmap.getWidth());
                float targetHeight = bitmap.getHeight() * scale;
                PointF point = insertionPoint == null
                        ? new PointF(page.getWidth() / 2f, page.getHeight() / 2f)
                        : insertionPoint;
                Matrix matrix = new Matrix();
                matrix.setScale(scale, scale);
                matrix.postTranslate(
                        clamp(point.x - targetWidth / 2f, 0f, page.getWidth() - targetWidth),
                        clamp(point.y - targetHeight / 2f, 0f, page.getHeight() - targetHeight));
                PdfPageImageObject imageObject = new PdfPageImageObject(bitmap);
                imageObject.setMatrix(matrix);
                int objectId = page.addPageObject(imageObject);
                if (objectId < 0) throw new IOException("PDF image insertion was rejected.");
                selectedObject = new Pair<>(objectId, imageObject);
                changed = true;
                renderPreview();
            } catch (Throwable error) {
                runOnUiThread(() -> showError(error));
            }
        });
    }

    private void deleteSelected() {
        Pair<Integer, PdfPageObject> selection = selectedObject;
        if (selection == null) return;
        setBusy(true, getString(R.string.pdf_object_deleting));
        worker.execute(() -> {
            try {
                page.removePageObject(selection.first);
                selectedObject = null;
                changed = true;
                runOnUiThread(() -> deleteButton.setEnabled(false));
                renderPreview();
            } catch (Throwable error) {
                runOnUiThread(() -> showError(error));
            }
        });
    }

    private void requestSave() {
        if (!changed) {
            Toast.makeText(this, R.string.pdf_object_no_changes, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, copyFileName(displayName));
        startActivityForResult(intent, REQUEST_SAVE);
    }

    private void writeCopy(Uri target) {
        setBusy(true, getString(R.string.pdf_object_saving));
        worker.execute(() -> {
            try (ParcelFileDescriptor output = getContentResolver().openFileDescriptor(target, "w")) {
                if (output == null) throw new IOException("Destination is unavailable.");
                closePage();
                renderer.write(output, false);
                page = renderer.openPage(pageIndex);
                changed = false;
                runOnUiThread(() -> {
                    setBusy(false, getString(R.string.pdf_object_saved));
                    Toast.makeText(this, R.string.pdf_saved_copy, Toast.LENGTH_LONG).show();
                });
            } catch (Throwable error) {
                tryReopenPage();
                runOnUiThread(() -> showError(error));
            }
        });
    }

    private void requestClose() {
        if (!changed) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.pdf_unsaved_title)
                .setMessage(R.string.pdf_unsaved_message)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.pdf_discard, (dialog, which) -> finish())
                .setPositiveButton(R.string.pdf_save_copy, (dialog, which) -> requestSave())
                .show();
    }

    private void setBusy(boolean busy, String message) {
        if (status != null) status.setText(message);
        if (preview != null) preview.setEnabled(!busy);
    }

    private void showFatal(Throwable error) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.pdf_object_editor)
                .setMessage(getString(R.string.pdf_object_failed, safeMessage(error)))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .show();
    }

    private void showError(Throwable error) {
        setBusy(false, getString(R.string.pdf_object_failed, safeMessage(error)));
        Toast.makeText(this, status.getText(), Toast.LENGTH_LONG).show();
    }

    private InputStream requireInput(Uri uri) throws IOException {
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) throw new IOException("Source is unavailable.");
        return input;
    }

    private static void copy(InputStream input, FileOutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
        output.flush();
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(Math.max(minimum, maximum), value));
    }

    private static String copyFileName(String name) {
        String base = name == null || name.trim().isEmpty() ? "document" : name.trim();
        if (base.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            base = base.substring(0, base.length() - 4);
        }
        return base + "-content-edited.pdf";
    }

    private static String safeMessage(Throwable error) {
        String value = error == null ? null : error.getMessage();
        return value == null || value.trim().isEmpty() ? "Unknown error" : value;
    }

    private void closePage() {
        if (page != null) {
            page.close();
            page = null;
        }
    }

    private void tryReopenPage() {
        if (page == null && renderer != null) {
            try {
                page = renderer.openPage(pageIndex);
            } catch (RuntimeException ignored) {
                // Visible error remains authoritative.
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static Uri readUriExtra(Intent intent, String key) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(key, Uri.class);
        return intent.getParcelableExtra(key);
    }

    @Override
    protected void onDestroy() {
        worker.execute(() -> {
            closePage();
            if (renderer != null) renderer.close();
            if (workingFile != null) workingFile.delete();
        });
        worker.shutdown();
        super.onDestroy();
    }
}

