package com.bimo.easytoread;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.ParcelFileDescriptor;
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
import android.window.OnBackInvokedDispatcher;
import com.bimo.easytoread.core.DocumentModel;
import com.bimo.easytoread.core.DocumentRenderer;
import com.bimo.easytoread.core.DocxExporter;
import com.bimo.easytoread.core.XlsxExporter;
import com.bimo.easytoread.ocr.PaddleOcrEngine;
import com.bimo.easytoread.ocr.OcrEngine;
import com.bimo.easytoread.platform.CaptureContentProvider;
import com.bimo.easytoread.platform.ClipboardWriter;
import com.bimo.easytoread.platform.ImageLoader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    private enum ExportDestination { SAVE_AS, OPEN_WITH, SHARE_FILE }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private OcrEngine ocrEngine;

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
    private Button fileButton;
    private View primaryCaptureButton;
    private Button textSmallerButton;
    private Button textLargerButton;
    private View copyButton;
    private View shareButton;
    private View exportButton;
    private View xlsxButton;
    private Button modeDocument;
    private Button modeTable;
    private Button modeQuickText;
    private Button modeMultiPage;
    private Button workspaceRead;
    private Button workspaceEdit;
    private Button workspaceReview;
    private View workspaceUtility;
    private TextView workspaceStatus;
    private View detectionBadge;
    private View cropFrame;

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
        ocrEngine = new PaddleOcrEngine(getApplicationContext());
        bindViews();
        bindActions();
        registerPredictiveBack();

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
        if (BuildConfig.DEBUG
                && "workspace".equals(getIntent().getStringExtra("ui_qa_screen"))) {
            loadQaWorkspace();
        } else {
            handleIncomingShare(getIntent());
        }
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
        fileButton = findViewById(R.id.buttonFile);
        primaryCaptureButton = findViewById(R.id.buttonPrimaryCapture);
        settingsButton = findViewById(R.id.buttonSettings);
        textSmallerButton = findViewById(R.id.buttonTextSmaller);
        textLargerButton = findViewById(R.id.buttonTextLarger);
        copyButton = findViewById(R.id.buttonCopy);
        shareButton = findViewById(R.id.buttonShare);
        exportButton = findViewById(R.id.buttonWorkspaceExport);
        xlsxButton = findViewById(R.id.buttonXlsx);
        modeDocument = findViewById(R.id.modeDocument);
        modeTable = findViewById(R.id.modeTable);
        modeQuickText = findViewById(R.id.modeQuickText);
        modeMultiPage = findViewById(R.id.modeMultiPage);
        workspaceRead = findViewById(R.id.workspaceRead);
        workspaceEdit = findViewById(R.id.workspaceEdit);
        workspaceReview = findViewById(R.id.workspaceReview);
        workspaceUtility = findViewById(R.id.workspaceUtility);
        workspaceStatus = findViewById(R.id.textWorkspaceStatus);
        detectionBadge = findViewById(R.id.detectionBadge);
        cropFrame = findViewById(R.id.cropFrame);
    }

    private void bindActions() {
        tabInput.setOnClickListener(view -> showTab(false));
        tabOutput.setOnClickListener(view -> showTab(true));
        galleryButton.setOnClickListener(view -> chooseImageOnly());
        fileButton.setOnClickListener(view -> chooseFile());
        primaryCaptureButton.setOnClickListener(view -> takePhoto());
        settingsButton.setOnClickListener(
                view -> startActivityForResult(new Intent(this, SettingsActivity.class), REQUEST_SETTINGS)
        );
        textSmallerButton.setOnClickListener(view -> adjustTextScale(-TEXT_SCALE_STEP));
        textLargerButton.setOnClickListener(view -> adjustTextScale(TEXT_SCALE_STEP));
        copyButton.setOnClickListener(view -> copyResult(false));
        shareButton.setOnClickListener(view -> shareResult());
        exportButton.setOnClickListener(view -> showExportFormatMenu());
        xlsxButton.setOnClickListener(view -> showExportDestinationMenu(ExportType.XLSX));

        modeDocument.setOnClickListener(view ->
                selectScanMode(modeDocument, R.string.scan_mode_document_selected));
        modeTable.setOnClickListener(view ->
                selectScanMode(modeTable, R.string.scan_mode_table_selected));
        modeQuickText.setOnClickListener(view ->
                selectScanMode(modeQuickText, R.string.scan_mode_quick_selected));
        modeMultiPage.setOnClickListener(view ->
                selectScanMode(modeMultiPage, R.string.scan_mode_multi_selected));

        findViewById(R.id.buttonHistory).setOnClickListener(view ->
                startActivity(new Intent(this, HubActivity.class)
                        .putExtra(HubActivity.EXTRA_DESTINATION, HubActivity.DESTINATION_ACTIVITY)));

        findViewById(R.id.buttonNavHome).setOnClickListener(view -> {
            startActivity(new Intent(this, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        });
        findViewById(R.id.buttonNavDocuments).setOnClickListener(view ->
                startActivity(new Intent(this, HubActivity.class)
                        .putExtra(HubActivity.EXTRA_DESTINATION, HubActivity.DESTINATION_DOCUMENTS)));
        findViewById(R.id.buttonNavScan).setOnClickListener(view -> showTab(false));
        findViewById(R.id.buttonNavTools).setOnClickListener(view ->
                startActivity(new Intent(this, HubActivity.class)
                        .putExtra(HubActivity.EXTRA_DESTINATION, HubActivity.DESTINATION_TOOLS)));
        findViewById(R.id.buttonNavActivity).setOnClickListener(view ->
                startActivity(new Intent(this, HubActivity.class)
                        .putExtra(HubActivity.EXTRA_DESTINATION, HubActivity.DESTINATION_ACTIVITY)));

        findViewById(R.id.buttonWorkspaceBack).setOnClickListener(view -> showTab(false));
        workspaceRead.setOnClickListener(view -> selectWorkspaceMode(workspaceRead, false, false));
        workspaceEdit.setOnClickListener(view -> selectWorkspaceMode(workspaceEdit, true, false));
        workspaceReview.setOnClickListener(view -> selectWorkspaceMode(workspaceReview, false, true));
        View.OnClickListener focusSearch = view -> {
            resultEditor.requestFocus();
            resultEditor.setSelection(0);
        };
        findViewById(R.id.buttonWorkspaceSearch).setOnClickListener(focusSearch);

        selectScanMode(modeDocument, R.string.no_image);
        selectWorkspaceMode(workspaceRead, false, false);

        resultEditor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable value) {
                enableResultActions(!value.toString().trim().isEmpty());
            }
        });
    }

    private void selectScanMode(Button selected, int announcement) {
        Button[] modes = { modeDocument, modeTable, modeQuickText, modeMultiPage };
        for (Button mode : modes) {
            boolean active = mode == selected;
            mode.setBackgroundResource(active
                    ? R.drawable.bg_card_selected
                    : R.drawable.bg_card);
            mode.setTextColor(getColor(active
                    ? R.color.accent_on_primary
                    : R.color.text_primary));
            mode.setSelected(active);
        }
        status.setText(announcement);
    }

    private void selectWorkspaceMode(Button selected, boolean editable, boolean review) {
        Button[] tabs = { workspaceRead, workspaceEdit, workspaceReview };
        for (Button tab : tabs) {
            boolean active = tab == selected;
            tab.setBackgroundResource(active
                    ? R.drawable.bg_tab_selected
                    : R.drawable.bg_tab_unselected);
            tab.setTextColor(getColor(active
                    ? R.color.accent_secondary
                    : R.color.text_primary));
            tab.setTypeface(Typeface.create(
                    "sans-serif",
                    active ? Typeface.BOLD : Typeface.NORMAL
            ));
            tab.setSelected(active);
        }
        resultEditor.setFocusable(editable);
        resultEditor.setFocusableInTouchMode(editable);
        resultEditor.setCursorVisible(editable);
        workspaceUtility.setVisibility(review ? View.VISIBLE : View.GONE);
        workspaceStatus.setText(selected == workspaceRead
                ? R.string.workspace_read_status
                : selected == workspaceEdit
                        ? R.string.workspace_edit_status
                        : R.string.workspace_review_status);
        if (editable) resultEditor.requestFocus();
    }

    private void loadQaWorkspace() {
        String sample = "Executive Summary\n\n"
                + "The Audit Committee presents the key findings and conclusions "
                + "for the current reporting period.\n\n"
                + "Based on our review, the Company’s internal control system is "
                + "adequate and operating effectively.\n\n"
                + "Key Audit Update\n\n"
                + "Area                 Focus                         Conclusion\n"
                + "Financial Reporting  Accuracy and completeness     Satisfactory\n"
                + "Internal Control     Operating effectiveness       Satisfactory\n"
                + "Risk Management      Identification and mitigation Satisfactory\n"
                + "Compliance           Regulatory and policy         Satisfactory";
        currentDocument = DocumentModel.fromPlainText(sample);
        resultEditor.setText(sample);
        resultEditor.setSelection(0);
        enableResultActions(true);
        updateResultMeta(currentDocument, false);
        showTab(true);
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
        if (output) selectWorkspaceMode(workspaceRead, false, false);
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

    private void chooseImageOnly() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_IMAGE);
    }

    private void chooseFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "application/pdf",
                "image/*"
        });
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
        String type = intent.getType();
        if (type == null || !(type.startsWith("image/") || "application/pdf".equals(type))) return;

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
                String mime = getContentResolver().getType(uri);
                boolean pdf = "application/pdf".equals(mime)
                        || uri.toString().toLowerCase(Locale.ROOT).endsWith(".pdf");
                Bitmap bitmap;
                boolean lowResolution;
                if (pdf) {
                    bitmap = renderFirstPdfPage(uri, 2800);
                    lowResolution = bitmap.getWidth() < 960 || bitmap.getHeight() < 960;
                } else {
                    ImageLoader.Result loaded = ImageLoader.load(this, uri, 2800);
                    bitmap = loaded.getBitmap();
                    lowResolution = loaded.isLowResolution();
                }
                runOnUiThread(() -> {
                    replacePreview(bitmap);
                    if (lowResolution) {
                        status.setText(R.string.low_resolution_warning);
                    }
                    ocrEngine.recognize(bitmap, new OcrEngine.Callback() {
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

    private Bitmap renderFirstPdfPage(Uri uri, int maxSide) throws IOException {
        try (ParcelFileDescriptor descriptor =
                     getContentResolver().openFileDescriptor(uri, "r")) {
            if (descriptor == null) throw new IOException("PDF descriptor is unavailable.");
            try (PdfRenderer renderer = new PdfRenderer(descriptor);
                 PdfRenderer.Page page = renderer.openPage(0)) {
                float scale = Math.min(
                        1f,
                        maxSide / (float) Math.max(page.getWidth(), page.getHeight())
                );
                int width = Math.max(1, Math.round(page.getWidth() * scale));
                int height = Math.max(1, Math.round(page.getHeight() * scale));
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(
                        bitmap,
                        null,
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                );
                return bitmap;
            }
        }
    }

    private void replacePreview(Bitmap bitmap) {
        Bitmap old = currentBitmap;
        currentBitmap = bitmap;
        imagePreview.setImageBitmap(bitmap);
        detectionBadge.setVisibility(View.VISIBLE);
        cropFrame.setVisibility(View.VISIBLE);
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
        if (document.getWorksheet() != null) {
            status.setText(getString(
                    document.getWorksheet().getVerificationStatus()
                            == com.bimo.easytoread.core.WorksheetModel.VerificationStatus.REVIEW_REQUIRED
                            ? R.string.worksheet_review_required
                            : R.string.worksheet_detected,
                    document.getWorksheet().getRowCount(),
                    document.getWorksheet().getColumnCount()
            ));
        } else {
            status.setText(getString(
                    R.string.document_ready,
                    document.getBlocks().size(),
                    document.countLines()
            ));
        }
        updateResultMeta(document, false);
        workspaceStatus.setText(document.getWorksheet() != null
                ? R.string.workspace_review_status
                : R.string.workspace_ocr_status);
        showTab(true);
        if (AppPreferences.isAutoCopy(this)) copyResult(true);
    }

    private void updateResultMeta(DocumentModel document, boolean copied) {
        if (document == null || document.toPlainText().isEmpty()) {
            resultMeta.setText(R.string.no_result_meta);
            return;
        }
        String base = getString(
                copied ? R.string.result_meta_copied : R.string.result_meta,
                document.getBlocks().size(),
                document.countLines()
        );
        String engineId = document.getEngineId();
        int engineLabel = engineId.startsWith("pp-ocrv6-medium")
                ? R.string.engine_ppocr_medium
                : engineId.startsWith("mlkit")
                        ? R.string.engine_mlkit_fallback
                        : engineId.startsWith("manual-edit")
                                ? R.string.engine_manual_edit
                                : R.string.engine_unknown;
        resultMeta.setText(base + getString(R.string.engine_meta, getString(engineLabel)));
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

    private void showExportFormatMenu() {
        DocumentModel document = effectiveDocument();
        if (document.toPlainText().isEmpty()) return;

        ArrayList<ExportType> types = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        types.add(ExportType.MARKDOWN);
        labels.add(getString(R.string.markdown_short));
        types.add(ExportType.DOCX);
        labels.add(getString(R.string.word_short));
        if (document.getWorksheet() != null) {
            types.add(ExportType.XLSX);
            labels.add(getString(R.string.excel_short));
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.export_format_title)
                .setItems(labels.toArray(new String[0]),
                        (dialog, index) -> showExportDestinationMenu(types.get(index)))
                .show();
    }

    private void showExportDestinationMenu(ExportType type) {
        DocumentModel document = effectiveDocument();
        if (document.toPlainText().isEmpty()) return;
        if (type == ExportType.XLSX && document.getWorksheet() == null) {
            Toast.makeText(this, R.string.excel_table_only, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] destinations = {
                getString(R.string.export_save_as),
                getString(R.string.export_open_with),
                getString(R.string.export_share_file)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.export_destination_title)
                .setItems(destinations, (dialog, index) -> {
                    ExportDestination destination = ExportDestination.values()[index];
                    if (destination == ExportDestination.SAVE_AS) requestExport(type);
                    else prepareExport(type, destination);
                })
                .show();
    }

    private void prepareExport(ExportType type, ExportDestination destination) {
        DocumentModel document = effectiveDocument();
        if (document.toPlainText().isEmpty()) return;
        setBusy(true);
        Toast.makeText(this, R.string.export_prepare, Toast.LENGTH_SHORT).show();

        worker.execute(() -> {
            try {
                Uri target = CaptureContentProvider.createExportUri(this, exportFilename(type));
                try (OutputStream output = getContentResolver().openOutputStream(target, "w")) {
                    if (output == null) throw new IOException("Output stream is unavailable.");
                    writeExportPayload(document, output, type);
                }
                runOnUiThread(() -> {
                    setBusy(false);
                    launchPreparedExport(target, type, destination);
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showExportFailure(getString(R.string.save_failed, safeMessage(error)));
                });
            }
        });
    }

    private void launchPreparedExport(
            Uri target,
            ExportType type,
            ExportDestination destination
    ) {
        Intent intent;
        if (destination == ExportDestination.OPEN_WITH) {
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(target, exportMime(type));
        } else {
            intent = new Intent(Intent.ACTION_SEND);
            intent.setType(exportMime(type));
            intent.putExtra(Intent.EXTRA_STREAM, target);
        }
        intent.setClipData(ClipData.newRawUri("BIMO EasyDocs export", target));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            if (intent.resolveActivity(getPackageManager()) == null) {
                Toast.makeText(this, R.string.export_no_app, Toast.LENGTH_LONG).show();
                return;
            }
            startActivity(Intent.createChooser(intent, getString(R.string.export_destination_title)));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.export_no_app, Toast.LENGTH_LONG).show();
        }
    }

    private void requestExport(ExportType type) {
        DocumentModel document = effectiveDocument();
        if (document.toPlainText().isEmpty()) return;
        if (type == ExportType.XLSX && document.getWorksheet() == null) {
            Toast.makeText(this, R.string.excel_table_only, Toast.LENGTH_SHORT).show();
            return;
        }
        pendingExport = type;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(exportMime(type));
        intent.putExtra(Intent.EXTRA_TITLE, exportFilename(type));
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void writeExport(Uri target, ExportType type) {
        DocumentModel document = effectiveDocument();
        setBusy(true);
        worker.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(target, "w")) {
                if (output == null) throw new IOException("Output stream is unavailable.");
                writeExportPayload(document, output, type);
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showExportFailure(getString(R.string.save_failed, safeMessage(error)));
                });
            }
        });
    }

    private static void writeExportPayload(
            DocumentModel document,
            OutputStream output,
            ExportType type
    ) throws IOException {
        if (type == ExportType.MARKDOWN) {
            output.write(DocumentRenderer.toMarkdown(document).getBytes(StandardCharsets.UTF_8));
        } else if (type == ExportType.DOCX) {
            DocxExporter.write(document, output);
        } else {
            XlsxExporter.write(document, output);
        }
    }

    private static String exportMime(ExportType type) {
        if (type == ExportType.MARKDOWN) return "text/markdown";
        if (type == ExportType.DOCX) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    private static String exportFilename(ExportType type) {
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        if (type == ExportType.MARKDOWN) return "BIMO-EasyDocs-" + timestamp + ".md";
        if (type == ExportType.DOCX) return "BIMO-EasyDocs-" + timestamp + ".docx";
        return "BIMO-EasyDocs-" + timestamp + ".xlsx";
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        galleryButton.setEnabled(!busy);
        fileButton.setEnabled(!busy);
        primaryCaptureButton.setEnabled(!busy);
        settingsButton.setEnabled(!busy);
        tabInput.setEnabled(!busy);
        tabOutput.setEnabled(!busy);
        if (busy) enableResultActions(false);
        else if (!resultEditor.getText().toString().trim().isEmpty()) enableResultActions(true);
    }

    private void enableResultActions(boolean enabled) {
        setActionEnabled(copyButton, enabled);
        setActionEnabled(shareButton, enabled);
        setActionEnabled(exportButton, enabled);
        boolean worksheet = hasExportableWorksheet();
        xlsxButton.setVisibility(worksheet ? View.VISIBLE : View.GONE);
        setActionEnabled(xlsxButton, enabled && worksheet);
    }

    private boolean hasExportableWorksheet() {
        if (currentDocument == null || currentDocument.getWorksheet() == null) return false;
        return resultEditor.getText().toString().trim()
                .equals(currentDocument.toPlainText().trim());
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

    private void showExportFailure(String message) {
        workspaceStatus.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty()
                ? "Unknown processing error"
                : message;
    }

    private void registerPredictiveBack() {
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::handleBackNavigation
            );
        }
    }

    private void handleBackNavigation() {
        if (showingOutput) {
            showTab(false);
        } else {
            finishAfterTransition();
        }
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        handleBackNavigation();
    }

    @Override
    protected void onSaveInstanceState(Bundle output) {
        super.onSaveInstanceState(output);
        output.putString(STATE_RESULT, resultEditor.getText().toString());
        output.putBoolean(STATE_OUTPUT_TAB, showingOutput);
    }

    @Override
    protected void onDestroy() {
        if (ocrEngine != null) ocrEngine.close();
        worker.shutdownNow();
        if (currentBitmap != null && !currentBitmap.isRecycled()) currentBitmap.recycle();
        super.onDestroy();
    }
}
