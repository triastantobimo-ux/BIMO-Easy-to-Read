package com.bimo.easytoread;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.bimo.easytoread.core.DocumentModel;
import com.bimo.easytoread.core.DocumentRenderer;
import com.bimo.easytoread.core.DocxExporter;
import com.bimo.easytoread.core.XlsxExporter;
import com.bimo.easytoread.ocr.MlKitOcrEngine;
import com.bimo.easytoread.ocr.OcrEngine;
import com.bimo.easytoread.platform.CaptureContentProvider;
import com.bimo.easytoread.platform.ClipboardWriter;
import com.bimo.easytoread.platform.ImageLoader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_IMAGE = 1001;
    private static final int REQUEST_CAMERA = 1002;
    private static final int REQUEST_SETTINGS = 1003;
    private static final int REQUEST_EXPORT = 1004;
    private static final String STATE_RESULT = "result_text";
    private static final String STATE_OUTPUT_TAB = "output_tab";
    private static final int MIN_TEXT_SCALE = 50;
    private static final int MAX_TEXT_SCALE = 150;
    private static final int TEXT_SCALE_STEP = 10;
    private static final float BASE_TEXT_SIZE_SP = 18f;

    private enum ExportType { MARKDOWN, DOCX, XLSX }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final OcrEngine ocrEngine = new MlKitOcrEngine();

    private View inputPanel;
    private View outputPanel;
    private View settingsButton;
    private Button tabInput;
    private Button tabOutput;
    private ImageView imagePreview;
    private TextView status;
    private TextView resultMeta;
    private TextView textScaleValue;
    private ProgressBar progress;
    private EditText resultEditor;
    private Button galleryButton;
    private Button cameraButton;
    private Button textSmallerButton;
    private Button textLargerButton;
    private View copyButton;
    private View shareButton;
    private View markdownButton;
    private View docxButton;
    private View xlsxButton;

    private DocumentModel currentDocument;
    private Bitmap currentBitmap;
    private Uri pendingCaptureUri;
    private ExportType pendingExport;
    private int textScalePercent;
    private boolean showingOutput;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppPreferences.wrapLanguage(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        AppPreferences.applyTheme(this);
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        bindViews();
        bindActions();

        textScalePercent = clampTextScale(AppPreferences.getTextScale(this));
        applyTextScale();

        if (state != null) {
            String restored = state.getString(STATE_RESULT, "");
            if (!restored.isEmpty()) {
                currentDocument = DocumentModel.fromPlainText(restored);
                resultEditor.setText(restored);
                resultEditor.setSelection(0);
                enableResultActions(true);
                updateResultMeta(currentDocument, false);
            }
            showTab(state.getBoolean(STATE_OUTPUT_TAB, !restored.isEmpty()));
        } else {
            showTab(false);
        }
        handleIncomingShare(getIntent());
    }

    private void bindViews() {
        inputPanel = findViewById(R.id.inputPanel);
        outputPanel = findViewById(R.id.outputPanel);
        tabInput = findViewById(R.id.tabInput);
        tabOutput = findViewById(R.id.tabOutput);
        imagePreview = findViewById(R.id.imagePreview);
        status = findViewById(R.id.textStatus);
        resultMeta = findViewById(R.id.textResultMeta);
        textScaleValue = findViewById(R.id.textScaleValue);
        progress = findViewById(R.id.progress);
        resultEditor = findViewById(R.id.editResult);
        galleryButton = findViewById(R.id.buttonGallery);
        cameraButton = findViewById(R.id.buttonCamera);
        settingsButton = findViewById(R.id.buttonSettings);
        textSmallerButton = findViewById(R.id.buttonTextSmaller);
        textLargerButton = findViewById(R.id.buttonTextLarger);
        copyButton = findViewById(R.id.buttonCopy);
        shareButton = findViewById(R.id.buttonShare);
        markdownButton = findViewById(R.id.buttonMarkdown);
        docxButton = findViewById(R.id.buttonDocx);
        xlsxButton = findViewById(R.id.buttonXlsx);
    }

    private void bindActions() {
        tabInput.setOnClickListener(view -> showTab(false));
        tabOutput.setOnClickListener(view -> showTab(true));
        galleryButton.setOnClickListener(view -> chooseImage());
        cameraButton.setOnClickListener(view -> takePhoto());
        settingsButton.setOnClickListener(
                view -> startActivityForResult(new Intent(this, SettingsActivity.class), REQUEST_SETTINGS)
        );
        textSmallerButton.setOnClickListener(view -> adjustTextScale(-TEXT_SCALE_STEP));
        textLargerButton.setOnClickListener(view -> adjustTextScale(TEXT_SCALE_STEP));
        copyButton.setOnClickListener(view -> copyResult(false));
        shareButton.setOnClickListener(view -> shareResult());
        markdownButton.setOnClickListener(view -> requestExport(ExportType.MARKDOWN));
        docxButton.setOnClickListener(view -> requestExport(ExportType.DOCX));
        xlsxButton.setOnClickListener(view -> requestExport(ExportType.XLSX));

        resultEditor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable value) {
                enableResultActions(!value.toString().trim().isEmpty());
            }
        });
    }

    private void showTab(boolean output) {
        showingOutput = output;
        inputPanel.setVisibility(output ? View.GONE : View.VISIBLE);
        outputPanel.setVisibility(output ? View.VISIBLE : View.GONE);

        tabInput.setBackgroundResource(output
                ? R.drawable.bg_tab_unselected
                : R.drawable.bg_tab_selected);
        tabOutput.setBackgroundResource(output
                ? R.drawable.bg_tab_selected
                : R.drawable.bg_tab_unselected);
        tabInput.setSelected(!output);
        tabOutput.setSelected(output);

        tabInput.setTextColor(getColor(output
                ? R.color.text_primary
                : R.color.accent_secondary));
        tabOutput.setTextColor(getColor(output
                ? R.color.accent_secondary
                : R.color.text_primary));

        tabInput.setTypeface(Typeface.create(
                "sans-serif-condensed",
                output ? Typeface.NORMAL : Typeface.BOLD
        ));
        tabOutput.setTypeface(Typeface.create(
                "sans-serif-condensed",
                output ? Typeface.BOLD : Typeface.NORMAL
        ));
    }

    private void adjustTextScale(int delta) {
        int next = clampTextScale(textScalePercent + delta);
        if (next == textScalePercent) return;
        textScalePercent = next;
        AppPreferences.setTextScale(this, textScalePercent);
        applyTextScale();
    }

    private void applyTextScale() {
        float scaledSize = BASE_TEXT_SIZE_SP * textScalePercent / 100f;
        resultEditor.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize);
        textScaleValue.setText(getString(R.string.text_scale_value, textScalePercent));
        textSmallerButton.setEnabled(textScalePercent > MIN_TEXT_SCALE);
        textLargerButton.setEnabled(textScalePercent < MAX_TEXT_SCALE);

        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        resultEditor.setTypeface(Typeface.create(
                dark ? "monospace" : "sans-serif-condensed",
                Typeface.NORMAL
        ));
    }

    private static int clampTextScale(int value) {
        return Math.max(MIN_TEXT_SCALE, Math.min(MAX_TEXT_SCALE, value));
    }

    private void chooseImage() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= 33) {
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            intent.setType("image/*");
        } else {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
        }
        startActivityForResult(intent, REQUEST_IMAGE);
    }

    private void takePhoto() {
        try {
            pendingCaptureUri = CaptureContentProvider.createCaptureUri(this);
        } catch (IOException error) {
            showFailure(getString(R.string.image_failed, safeMessage(error)));
            return;
        }

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCaptureUri);
        intent.setClipData(ClipData.newRawUri("OCR capture", pendingCaptureUri));
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);

        List<ResolveInfo> targets = getPackageManager().queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
        );
        if (targets.isEmpty()) {
            status.setText(R.string.camera_unavailable);
            return;
        }
        for (ResolveInfo target : targets) {
            grantUriPermission(
                    target.activityInfo.packageName,
                    pendingCaptureUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        }
        startActivityForResult(intent, REQUEST_CAMERA);
    }

    @SuppressWarnings("deprecation")
    private void handleIncomingShare(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        if (intent.getType() == null || !intent.getType().startsWith("image/")) return;

        Parcelable value;
        if (Build.VERSION.SDK_INT >= 33) {
            value = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
        } else {
            value = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (value instanceof Uri) processUri((Uri) value);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SETTINGS && resultCode == RESULT_OK) {
            recreate();
            return;
        }
        if (requestCode == REQUEST_EXPORT && resultCode == RESULT_OK && data != null) {
            Uri target = data.getData();
            if (target != null && pendingExport != null) writeExport(target, pendingExport);
            return;
        }
        if (resultCode != RESULT_OK) return;

        if (requestCode == REQUEST_IMAGE && data != null && data.getData() != null) {
            Uri uri = data.getData();
            if (Build.VERSION.SDK_INT < 33) {
                try {
                    getContentResolver().takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                } catch (SecurityException ignored) {
                    // Some providers grant only transient access, enough for immediate OCR.
                }
            }
            processUri(uri);
        } else if (requestCode == REQUEST_CAMERA && pendingCaptureUri != null) {
            revokeUriPermission(pendingCaptureUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            processUri(pendingCaptureUri);
        }
    }

    private void processUri(Uri uri) {
        showTab(false);
        setBusy(true);
        status.setText(R.string.processing);

        worker.execute(() -> {
            try {
                ImageLoader.Result loaded = ImageLoader.load(this, uri, 2800);
                runOnUiThread(() -> {
                    replacePreview(loaded.getBitmap());
                    if (loaded.isLowResolution()) {
                        status.setText(R.string.low_resolution_warning);
                    }
                    ocrEngine.recognize(loaded.getBitmap(), new OcrEngine.Callback() {
                        @Override
                        public void onSuccess(DocumentModel document) {
                            runOnUiThread(() -> acceptDocument(document));
                        }

                        @Override
                        public void onFailure(Throwable error) {
                            runOnUiThread(() -> {
                                setBusy(false);
                                showFailure(getString(R.string.ocr_failed, safeMessage(error)));
                            });
                        }
                    });
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showFailure(getString(R.string.image_failed, safeMessage(error)));
                });
            }
        });
    }

    private void replacePreview(Bitmap bitmap) {
        Bitmap old = currentBitmap;
        currentBitmap = bitmap;
        imagePreview.setImageBitmap(bitmap);
        if (old != null && old != bitmap && !old.isRecycled()) old.recycle();
    }

    private void acceptDocument(DocumentModel document) {
        setBusy(false);
        currentDocument = document;
        String plain = document.toPlainText();
        resultEditor.setText(plain);
        if (!plain.isEmpty()) resultEditor.setSelection(0);

        if (plain.isEmpty()) {
            enableResultActions(false);
            resultMeta.setText(R.string.no_result_meta);
            status.setText(R.string.empty_result);
            showTab(true);
            return;
        }

        enableResultActions(true);
        status.setText(getString(
                R.string.document_ready,
                document.getBlocks().size(),
                document.countLines()
        ));
        updateResultMeta(document, false);
        showTab(true);
        if (AppPreferences.isAutoCopy(this)) copyResult(true);
    }

    private void updateResultMeta(DocumentModel document, boolean copied) {
        if (document == null || document.toPlainText().isEmpty()) {
            resultMeta.setText(R.string.no_result_meta);
            return;
        }
        resultMeta.setText(getString(
                copied ? R.string.result_meta_copied : R.string.result_meta,
                document.getBlocks().size(),
                document.countLines()
        ));
    }

    private DocumentModel effectiveDocument() {
        String edited = resultEditor.getText().toString().trim();
        if (currentDocument == null || !edited.equals(currentDocument.toPlainText())) {
            return DocumentModel.fromPlainText(edited);
        }
        return currentDocument;
    }

    private void copyResult(boolean automatic) {
        DocumentModel document = effectiveDocument();
        if (document.toPlainText().isEmpty()) return;
        ClipboardWriter.copy(
                this,
                document,
                AppPreferences.isSensitiveClipboard(this)
        );
        updateResultMeta(document, true);
        Toast.makeText(
                this,
                automatic ? R.string.copied : R.string.copy_updated,
                Toast.LENGTH_SHORT
        ).show();
    }

    private void shareResult() {
        DocumentModel document = effectiveDocument();
        if (document.toPlainText().isEmpty()) return;

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, document.toPlainText());
        startActivity(Intent.createChooser(intent, getString(R.string.share_chooser)));
    }

    private void requestExport(ExportType type) {
        DocumentModel document = effectiveDocument();
        if (document.toPlainText().isEmpty()) return;
        pendingExport = type;

        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (type == ExportType.MARKDOWN) {
            intent.setType("text/markdown");
            intent.putExtra(Intent.EXTRA_TITLE, "OCR-" + timestamp + ".md");
        } else if (type == ExportType.DOCX) {
            intent.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            intent.putExtra(Intent.EXTRA_TITLE, "OCR-" + timestamp + ".docx");
        } else {
            intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            intent.putExtra(Intent.EXTRA_TITLE, "OCR-" + timestamp + ".xlsx");
        }
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void writeExport(Uri target, ExportType type) {
        DocumentModel document = effectiveDocument();
        setBusy(true);
        worker.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(target, "w")) {
                if (output == null) throw new IOException("Output stream is unavailable.");
                if (type == ExportType.MARKDOWN) {
                    output.write(DocumentRenderer.toMarkdown(document).getBytes(StandardCharsets.UTF_8));
                } else if (type == ExportType.DOCX) {
                    DocxExporter.write(document, output);
                } else {
                    XlsxExporter.write(document, output);
                }
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showFailure(getString(R.string.save_failed, safeMessage(error)));
                });
            }
        });
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        galleryButton.setEnabled(!busy);
        cameraButton.setEnabled(!busy);
        settingsButton.setEnabled(!busy);
        tabInput.setEnabled(!busy);
        tabOutput.setEnabled(!busy);
        if (busy) enableResultActions(false);
        else if (!resultEditor.getText().toString().trim().isEmpty()) enableResultActions(true);
    }

    private void enableResultActions(boolean enabled) {
        setActionEnabled(copyButton, enabled);
        setActionEnabled(shareButton, enabled);
        setActionEnabled(markdownButton, enabled);
        setActionEnabled(docxButton, enabled);
        setActionEnabled(xlsxButton, enabled);
    }

    private static void setActionEnabled(View action, boolean enabled) {
        action.setEnabled(enabled);
        action.setAlpha(enabled ? 1f : 0.45f);
    }

    private void showFailure(String message) {
        showTab(false);
        status.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty()
                ? "Unknown processing error"
                : message;
    }

    @Override
    protected void onSaveInstanceState(Bundle output) {
        super.onSaveInstanceState(output);
        output.putString(STATE_RESULT, resultEditor.getText().toString());
        output.putBoolean(STATE_OUTPUT_TAB, showingOutput);
    }

    @Override
    protected void onDestroy() {
        ocrEngine.close();
        worker.shutdownNow();
        if (currentBitmap != null && !currentBitmap.isRecycled()) currentBitmap.recycle();
        super.onDestroy();
    }
}
