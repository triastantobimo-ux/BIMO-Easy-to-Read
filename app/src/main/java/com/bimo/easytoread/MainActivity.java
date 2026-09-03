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
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;
import com.bimo.easytoread.core.DocumentModel;
import com.bimo.easytoread.core.DocumentRenderer;
import com.bimo.easytoread.core.DocxExporter;
import com.bimo.easytoread.core.WorksheetModel;
import com.bimo.easytoread.core.XlsxExporter;
import com.bimo.easytoread.ocr.OcrEngine;
import com.bimo.easytoread.ocr.PaddleOcrEngine;
import com.bimo.easytoread.platform.CaptureContentProvider;
import com.bimo.easytoread.platform.ClipboardWriter;
import com.bimo.easytoread.platform.ImageLoader;
import com.bimo.easytoread.platform.PdfImageAssembler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    public static final String EXTRA_ENTRY_MODE = "entry_mode";
    public static final String EXTRA_PDF_PAGE_INDEX = "pdf_page_index";
    public static final String ENTRY_SCAN = "scan";
    public static final String ENTRY_OCR = "ocr";

    private static final int REQUEST_SOURCE = 1001;
    private static final int REQUEST_CAMERA = 1002;
    private static final int REQUEST_SETTINGS = 1003;
    private static final int REQUEST_EXPORT = 1004;
    private static final int REQUEST_SCAN_PDF = 1005;
    private static final String STATE_RESULT = "result_text";
    private static final String STATE_OUTPUT = "showing_output";
    private static final String STATE_ENTRY = "entry";
    private static final String STATE_SCAN_MODE = "scan_mode";
    private static final String STATE_SCAN_URIS = "scan_uris";
    private static final String STATE_SCAN_INDEX = "scan_index";
    private static final int MIN_TEXT_SCALE = 50;
    private static final int MAX_TEXT_SCALE = 150;
    private static final int TEXT_SCALE_STEP = 10;
    private static final float BASE_TEXT_SIZE_SP = 18f;
    private static final int MAX_SCAN_PAGES = 50;

    private enum ExportType { MARKDOWN, DOCX, XLSX }
    private enum ExportDestination { SAVE_AS, OPEN_WITH, SHARE_FILE }
    private enum ScanMode { SINGLE, BATCH }
    private enum CaptureTarget { OCR, SCAN_PAGE }
    private enum ScanPdfDestination { SAVE_AS, SHARE, OPEN }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayList<Uri> scanPages = new ArrayList<>();
    private final ArrayList<ArrayList<EditText>> worksheetEditors = new ArrayList<>();
    private OcrEngine ocrEngine;

    private View inputPanel;
    private View outputPanel;
    private View scanSetupPanel;
    private View ocrSourcePanel;
    private View scanReviewPanel;
    private View globalNavigation;
    private View settingsButton;
    private TextView inputTitle;
    private TextView inputSubtitle;
    private TextView status;
    private TextView resultMeta;
    private TextView textScaleValue;
    private TextView workspaceStatus;
    private TextView scanPageMeta;
    private ProgressBar inputProgress;
    private ProgressBar outputProgress;
    private ImageView scanPagePreview;
    private EditText resultEditor;
    private View tableScroll;
    private TableLayout worksheetTable;
    private Button modeSingle;
    private Button modeBatch;
    private Button startScanButton;
    private Button ocrCameraButton;
    private Button galleryButton;
    private Button fileButton;
    private Button previousPageButton;
    private Button nextPageButton;
    private Button moveLeftButton;
    private Button moveRightButton;
    private Button addPageButton;
    private Button textSmallerButton;
    private Button textLargerButton;
    private View copyButton;
    private View shareButton;
    private View exportButton;
    private View xlsxButton;

    private DocumentModel currentDocument;
    private Bitmap currentBitmap;
    private Uri pendingCaptureUri;
    private ExportType pendingExport;
    private CaptureTarget captureTarget = CaptureTarget.OCR;
    private ScanMode scanMode = ScanMode.SINGLE;
    private String entryMode = ENTRY_SCAN;
    private int scanPageIndex;
    private int selectedPdfPage;
    private int textScalePercent;
    private boolean showingOutput;
    private boolean showingTable;

    public static Intent createEntryIntent(Context context, String entry) {
        return new Intent(context, MainActivity.class)
                .putExtra(EXTRA_ENTRY_MODE, ENTRY_OCR.equals(entry) ? ENTRY_OCR : ENTRY_SCAN);
    }

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
        if (state != null) restoreState(state);
        else entryMode = normalizeEntry(getIntent().getStringExtra(EXTRA_ENTRY_MODE));
        selectedPdfPage = Math.max(0, getIntent().getIntExtra(EXTRA_PDF_PAGE_INDEX, 0));
        applyTextScale();
        configureEntry();

        if (BuildConfig.DEBUG && "workspace".equals(getIntent().getStringExtra("ui_qa_screen"))) {
            loadQaWorkspace();
        } else if (state == null) {
            handleIncomingShare(getIntent());
        } else if (showingOutput && currentDocument != null) {
            showResultSurface();
        }
    }

    private void bindViews() {
        inputPanel = findViewById(R.id.inputPanel);
        outputPanel = findViewById(R.id.outputPanel);
        scanSetupPanel = findViewById(R.id.scanSetupPanel);
        ocrSourcePanel = findViewById(R.id.ocrSourcePanel);
        scanReviewPanel = findViewById(R.id.scanReviewPanel);
        globalNavigation = findViewById(R.id.globalBottomNavigation);
        settingsButton = findViewById(R.id.buttonSettings);
        inputTitle = findViewById(R.id.inputTitle);
        inputSubtitle = findViewById(R.id.inputSubtitle);
        status = findViewById(R.id.textStatus);
        resultMeta = findViewById(R.id.textResultMeta);
        textScaleValue = findViewById(R.id.textScaleValue);
        workspaceStatus = findViewById(R.id.textWorkspaceStatus);
        scanPageMeta = findViewById(R.id.scanPageMeta);
        inputProgress = findViewById(R.id.inputProgress);
        outputProgress = findViewById(R.id.outputProgress);
        scanPagePreview = findViewById(R.id.scanPagePreview);
        resultEditor = findViewById(R.id.editResult);
        tableScroll = findViewById(R.id.tableVerticalScroll);
        worksheetTable = findViewById(R.id.worksheetTable);
        modeSingle = findViewById(R.id.modeSingle);
        modeBatch = findViewById(R.id.modeBatch);
        startScanButton = findViewById(R.id.buttonStartScan);
        ocrCameraButton = findViewById(R.id.buttonOcrCamera);
        galleryButton = findViewById(R.id.buttonGallery);
        fileButton = findViewById(R.id.buttonFile);
        previousPageButton = findViewById(R.id.buttonPreviousPage);
        nextPageButton = findViewById(R.id.buttonNextPage);
        moveLeftButton = findViewById(R.id.buttonMoveLeft);
        moveRightButton = findViewById(R.id.buttonMoveRight);
        addPageButton = findViewById(R.id.buttonAddPage);
        textSmallerButton = findViewById(R.id.buttonTextSmaller);
        textLargerButton = findViewById(R.id.buttonTextLarger);
        copyButton = findViewById(R.id.buttonCopy);
        shareButton = findViewById(R.id.buttonShare);
        exportButton = findViewById(R.id.buttonWorkspaceExport);
        xlsxButton = findViewById(R.id.buttonXlsx);
    }

    private void bindActions() {
        settingsButton.setOnClickListener(view -> startActivityForResult(
                new Intent(this, SettingsActivity.class),
                REQUEST_SETTINGS
        ));
        modeSingle.setOnClickListener(view -> selectScanMode(ScanMode.SINGLE));
        modeBatch.setOnClickListener(view -> selectScanMode(ScanMode.BATCH));
        startScanButton.setOnClickListener(view -> startScanCapture(false));
        ocrCameraButton.setOnClickListener(view -> startOcrCamera());
        galleryButton.setOnClickListener(view -> chooseImageOnly());
        fileButton.setOnClickListener(view -> chooseFile());
        previousPageButton.setOnClickListener(view -> selectScanPage(scanPageIndex - 1));
        nextPageButton.setOnClickListener(view -> selectScanPage(scanPageIndex + 1));
        moveLeftButton.setOnClickListener(view -> moveScanPage(-1));
        moveRightButton.setOnClickListener(view -> moveScanPage(1));
        findViewById(R.id.buttonRotatePage).setOnClickListener(view -> rotateScanPage());
        findViewById(R.id.buttonDeletePage).setOnClickListener(view -> deleteScanPage());
        addPageButton.setOnClickListener(view -> startScanCapture(true));
        findViewById(R.id.buttonFinishScan).setOnClickListener(view -> showFinishScanMenu());

        findViewById(R.id.buttonWorkspaceBack).setOnClickListener(view -> showInputSurface());
        findViewById(R.id.buttonWorkspaceSearch).setOnClickListener(view -> showFindDialog());
        findViewById(R.id.buttonWorkspaceMore).setOnClickListener(view -> showWorkspaceMenu());
        textSmallerButton.setOnClickListener(view -> adjustTextScale(-TEXT_SCALE_STEP));
        textLargerButton.setOnClickListener(view -> adjustTextScale(TEXT_SCALE_STEP));
        copyButton.setOnClickListener(view -> copyResult(false));
        shareButton.setOnClickListener(view -> shareResult());
        exportButton.setOnClickListener(view -> showExportFormatMenu());
        xlsxButton.setOnClickListener(view -> showExportDestinationMenu(ExportType.XLSX));

        resultEditor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable value) {
                if (!showingTable) enableResultActions(!value.toString().trim().isEmpty());
            }
        });
    }

    private void configureEntry() {
        NavigationContract.Screen active = ENTRY_OCR.equals(entryMode)
                ? NavigationContract.Screen.OCR
                : NavigationContract.Screen.SCAN;
        AppNavigation.bind(this, active);
        selectScanMode(scanMode);
        if (showingOutput && currentDocument != null) showResultSurface();
        else showInputSurface();
    }

    private void showInputSurface() {
        showingOutput = false;
        inputPanel.setVisibility(View.VISIBLE);
        outputPanel.setVisibility(View.GONE);
        boolean review = ENTRY_SCAN.equals(entryMode) && !scanPages.isEmpty();
        scanSetupPanel.setVisibility(ENTRY_SCAN.equals(entryMode) && !review ? View.VISIBLE : View.GONE);
        ocrSourcePanel.setVisibility(ENTRY_OCR.equals(entryMode) ? View.VISIBLE : View.GONE);
        scanReviewPanel.setVisibility(review ? View.VISIBLE : View.GONE);
        globalNavigation.setVisibility(review ? View.GONE : View.VISIBLE);

        if (review) {
            inputTitle.setText(R.string.scan_review_title);
            inputSubtitle.setText(R.string.scan_review_subtitle);
            loadScanPreview();
        } else if (ENTRY_OCR.equals(entryMode)) {
            inputTitle.setText(R.string.nav_ocr);
            inputSubtitle.setText(R.string.ocr_source_subtitle);
            status.setText(R.string.ocr_source_status);
        } else {
            inputTitle.setText(R.string.nav_scan);
            inputSubtitle.setText(R.string.scan_setup_subtitle);
            status.setText(R.string.scan_setup_status);
        }
    }

    private void showResultSurface() {
        showingOutput = true;
        inputPanel.setVisibility(View.GONE);
        outputPanel.setVisibility(View.VISIBLE);
        if (currentDocument != null && currentDocument.getWorksheet() != null) showWorksheet(true);
        else showWorksheet(false);
    }

    private void selectScanMode(ScanMode selected) {
        scanMode = selected;
        applyModeStyle(modeSingle, selected == ScanMode.SINGLE);
        applyModeStyle(modeBatch, selected == ScanMode.BATCH);
    }

    private void applyModeStyle(Button button, boolean active) {
        button.setSelected(active);
        button.setBackgroundResource(active ? R.drawable.bg_card_selected : R.drawable.bg_card);
        button.setTextColor(getColor(active ? R.color.accent_on_primary : R.color.text_primary));
    }

    private void startScanCapture(boolean append) {
        if (!append && scanMode == ScanMode.SINGLE) clearScanPages();
        if (scanPages.size() >= MAX_SCAN_PAGES) {
            Toast.makeText(this, R.string.scan_page_limit, Toast.LENGTH_LONG).show();
            return;
        }
        captureTarget = CaptureTarget.SCAN_PAGE;
        takePhoto();
    }

    private void startOcrCamera() {
        captureTarget = CaptureTarget.OCR;
        takePhoto();
    }

    private void chooseImageOnly() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_SOURCE);
    }

    private void chooseFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] { "application/pdf", "image/*" });
        startActivityForResult(intent, REQUEST_SOURCE);
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
        intent.setClipData(ClipData.newRawUri("BIMO EasyDocs capture", pendingCaptureUri));
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
        if (value instanceof Uri) {
            entryMode = ENTRY_OCR;
            selectedPdfPage = Math.max(0, intent.getIntExtra(EXTRA_PDF_PAGE_INDEX, 0));
            configureEntry();
            processUri((Uri) value, selectedPdfPage);
        }
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
        if (requestCode == REQUEST_SCAN_PDF && resultCode == RESULT_OK && data != null) {
            Uri target = data.getData();
            if (target != null) writeScanPdf(target, ScanPdfDestination.SAVE_AS);
            return;
        }
        if (resultCode != RESULT_OK) return;

        if (requestCode == REQUEST_SOURCE && data != null && data.getData() != null) {
            Uri uri = data.getData();
            persistReadPermission(uri);
            processUri(uri, 0);
        } else if (requestCode == REQUEST_CAMERA && pendingCaptureUri != null) {
            revokeUriPermission(pendingCaptureUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (captureTarget == CaptureTarget.SCAN_PAGE) addScanPage(pendingCaptureUri);
            else processUri(pendingCaptureUri, 0);
        }
    }

    private void addScanPage(Uri uri) {
        scanPages.add(uri);
        scanPageIndex = scanPages.size() - 1;
        showInputSurface();
        status.setText(getString(R.string.scan_page_added, scanPages.size()));
    }

    private void selectScanPage(int requested) {
        if (scanPages.isEmpty()) return;
        scanPageIndex = Math.max(0, Math.min(scanPages.size() - 1, requested));
        loadScanPreview();
    }

    private void loadScanPreview() {
        if (scanPages.isEmpty()) return;
        setBusy(true);
        Uri uri = scanPages.get(scanPageIndex);
        worker.execute(() -> {
            try {
                ImageLoader.Result loaded = ImageLoader.load(this, uri, 1800);
                runOnUiThread(() -> {
                    replacePreview(loaded.getBitmap());
                    updateScanReviewControls();
                    setBusy(false);
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showFailure(getString(R.string.image_failed, safeMessage(error)));
                });
            }
        });
    }

    private void updateScanReviewControls() {
        int count = scanPages.size();
        scanPageMeta.setText(getString(R.string.scan_page_count, scanPageIndex + 1, count));
        previousPageButton.setEnabled(scanPageIndex > 0);
        nextPageButton.setEnabled(scanPageIndex + 1 < count);
        moveLeftButton.setEnabled(scanPageIndex > 0);
        moveRightButton.setEnabled(scanPageIndex + 1 < count);
        addPageButton.setVisibility(scanMode == ScanMode.BATCH ? View.VISIBLE : View.GONE);
    }

    private void moveScanPage(int delta) {
        int target = scanPageIndex + delta;
        if (target < 0 || target >= scanPages.size()) return;
        Collections.swap(scanPages, scanPageIndex, target);
        scanPageIndex = target;
        updateScanReviewControls();
    }

    private void rotateScanPage() {
        if (scanPages.isEmpty()) return;
        Uri uri = scanPages.get(scanPageIndex);
        setBusy(true);
        worker.execute(() -> {
            Bitmap source = null;
            Bitmap rotated = null;
            try {
                source = ImageLoader.load(this, uri, 2800).getBitmap();
                Matrix matrix = new Matrix();
                matrix.postRotate(90f);
                rotated = Bitmap.createBitmap(
                        source,
                        0,
                        0,
                        source.getWidth(),
                        source.getHeight(),
                        matrix,
                        true
                );
                try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                    if (output == null || !rotated.compress(Bitmap.CompressFormat.JPEG, 94, output)) {
                        throw new IOException("Unable to save rotated page.");
                    }
                }
                runOnUiThread(() -> loadScanPreview());
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showFailure(getString(R.string.image_failed, safeMessage(error)));
                });
            } finally {
                if (rotated != null && rotated != source && !rotated.isRecycled()) rotated.recycle();
                if (source != null && !source.isRecycled()) source.recycle();
            }
        });
    }

    private void deleteScanPage() {
        if (scanPages.isEmpty()) return;
        Uri removed = scanPages.remove(scanPageIndex);
        getContentResolver().delete(removed, null, null);
        if (scanPages.isEmpty()) {
            scanPageIndex = 0;
            showInputSurface();
            return;
        }
        scanPageIndex = Math.min(scanPageIndex, scanPages.size() - 1);
        loadScanPreview();
    }

    private void showFinishScanMenu() {
        if (scanPages.isEmpty()) return;
        String[] actions = {
                getString(R.string.scan_save_pdf),
                getString(R.string.scan_share_pdf),
                getString(R.string.scan_open_pdf),
                getString(R.string.scan_continue_ocr)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.scan_finish_title)
                .setItems(actions, (dialog, index) -> {
                    if (index == 0) requestScanPdfSave();
                    else if (index == 1) prepareScanPdf(ScanPdfDestination.SHARE);
                    else if (index == 2) prepareScanPdf(ScanPdfDestination.OPEN);
                    else recognizeScanPages();
                })
                .show();
    }

    private void requestScanPdfSave() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, scanPdfFilename());
        startActivityForResult(intent, REQUEST_SCAN_PDF);
    }

    private void prepareScanPdf(ScanPdfDestination destination) {
        setBusy(true);
        worker.execute(() -> {
            try {
                Uri target = CaptureContentProvider.createExportUri(this, scanPdfFilename());
                writeScanPdfPayload(target);
                runOnUiThread(() -> {
                    setBusy(false);
                    launchScanPdf(target, destination);
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showFailure(getString(R.string.pdf_create_failed, safeMessage(error)));
                });
            }
        });
    }

    private void writeScanPdf(Uri target, ScanPdfDestination destination) {
        setBusy(true);
        worker.execute(() -> {
            try {
                writeScanPdfPayload(target);
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, R.string.pdf_created, Toast.LENGTH_LONG).show();
                    if (destination != ScanPdfDestination.SAVE_AS) launchScanPdf(target, destination);
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showFailure(getString(R.string.pdf_create_failed, safeMessage(error)));
                });
            }
        });
    }

    private void writeScanPdfPayload(Uri target) throws IOException {
        try (OutputStream output = getContentResolver().openOutputStream(target, "w")) {
            if (output == null) throw new IOException("Output stream is unavailable.");
            PdfImageAssembler.write(this, new ArrayList<>(scanPages), output);
        }
    }

    private void launchScanPdf(Uri target, ScanPdfDestination destination) {
        if (destination == ScanPdfDestination.OPEN) {
            startActivity(PdfViewerActivity.createIntent(this, target));
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, target);
        intent.setClipData(ClipData.newRawUri("BIMO EasyDocs scan", target));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, getString(R.string.share_document)));
    }

    private void recognizeScanPages() {
        if (scanPages.isEmpty()) return;
        setBusy(true);
        status.setText(getString(R.string.ocr_batch_progress, 1, scanPages.size()));
        ArrayList<String> pageTexts = new ArrayList<>();
        recognizeScanPage(0, pageTexts, null);
    }

    private void recognizeScanPage(int index, ArrayList<String> pageTexts, DocumentModel single) {
        if (index >= scanPages.size()) {
            DocumentModel result = scanPages.size() == 1 && single != null
                    ? single
                    : DocumentModel.fromPlainText(String.join("\n\n---\n\n", pageTexts));
            acceptDocument(result);
            return;
        }
        worker.execute(() -> {
            try {
                Bitmap bitmap = ImageLoader.load(this, scanPages.get(index), 2800).getBitmap();
                runOnUiThread(() -> ocrEngine.recognize(bitmap, new OcrEngine.Callback() {
                    @Override
                    public void onSuccess(DocumentModel document) {
                        if (!bitmap.isRecycled()) bitmap.recycle();
                        pageTexts.add(document.toPlainText());
                        status.setText(getString(
                                R.string.ocr_batch_progress,
                                Math.min(index + 2, scanPages.size()),
                                scanPages.size()
                        ));
                        recognizeScanPage(index + 1, pageTexts, index == 0 ? document : single);
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        if (!bitmap.isRecycled()) bitmap.recycle();
                        setBusy(false);
                        showFailure(getString(R.string.ocr_failed, safeMessage(error)));
                    }
                }));
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showFailure(getString(R.string.image_failed, safeMessage(error)));
                });
            }
        });
    }

    private void processUri(Uri uri, int pdfPage) {
        entryMode = ENTRY_OCR;
        configureEntry();
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
                    bitmap = renderPdfPage(uri, pdfPage, 2800);
                    lowResolution = bitmap.getWidth() < 960 || bitmap.getHeight() < 960;
                } else {
                    ImageLoader.Result loaded = ImageLoader.load(this, uri, 2800);
                    bitmap = loaded.getBitmap();
                    lowResolution = loaded.isLowResolution();
                }
                runOnUiThread(() -> {
                    if (lowResolution) status.setText(R.string.low_resolution_warning);
                    ocrEngine.recognize(bitmap, new OcrEngine.Callback() {
                        @Override
                        public void onSuccess(DocumentModel document) {
                            if (!bitmap.isRecycled()) bitmap.recycle();
                            runOnUiThread(() -> acceptDocument(document));
                        }

                        @Override
                        public void onFailure(Throwable error) {
                            if (!bitmap.isRecycled()) bitmap.recycle();
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

    private Bitmap renderPdfPage(Uri uri, int requestedPage, int maxSide) throws IOException {
        try (BimoPdfEngine pdf = BimoPdfEngine.open(this, uri, null)) {
            int target = Math.max(0, Math.min(pdf.getPageCount() - 1, requestedPage));
            return pdf.renderPage(target, maxSide);
        }
    }

    private void replacePreview(Bitmap bitmap) {
        Bitmap old = currentBitmap;
        currentBitmap = bitmap;
        scanPagePreview.setImageBitmap(bitmap);
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
            workspaceStatus.setText(R.string.empty_result);
            showResultSurface();
            return;
        }
        updateResultMeta(document, false);
        if (document.getWorksheet() != null) {
            renderWorksheet(document.getWorksheet());
            workspaceStatus.setText(document.getWorksheet().getVerificationStatus()
                    == WorksheetModel.VerificationStatus.REVIEW_REQUIRED
                    ? R.string.workspace_table_review_status
                    : R.string.workspace_table_status);
        } else {
            workspaceStatus.setText(R.string.workspace_ocr_status);
        }
        enableResultActions(true);
        showResultSurface();
        if (AppPreferences.isAutoCopy(this)) copyResult(true);
    }

    private void renderWorksheet(WorksheetModel worksheet) {
        worksheetTable.removeAllViews();
        worksheetEditors.clear();
        List<List<String>> rows = worksheet.toRows();
        float[][] confidence = new float[worksheet.getRowCount()][worksheet.getColumnCount()];
        for (float[] row : confidence) java.util.Arrays.fill(row, 1f);
        for (WorksheetModel.Cell cell : worksheet.getCells()) {
            if (cell.getRow() < confidence.length && cell.getColumn() < confidence[cell.getRow()].length) {
                confidence[cell.getRow()][cell.getColumn()] = cell.getConfidence();
            }
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            TableRow tableRow = new TableRow(this);
            ArrayList<EditText> editorRow = new ArrayList<>();
            for (int column = 0; column < worksheet.getColumnCount(); column++) {
                EditText cell = new EditText(this);
                cell.setText(rows.get(rowIndex).get(column));
                cell.setTextColor(getColor(R.color.text_primary));
                cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, BASE_TEXT_SIZE_SP * textScalePercent / 100f);
                cell.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                cell.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                cell.setMinWidth(dp(140));
                cell.setMinHeight(dp(54));
                cell.setPadding(dp(10), dp(8), dp(10), dp(8));
                boolean header = rowIndex == worksheet.getPrimaryHeaderRowIndex();
                boolean warning = confidence[rowIndex][column] >= 0f && confidence[rowIndex][column] < 0.80f;
                cell.setTypeface(Typeface.create("sans-serif", header ? Typeface.BOLD : Typeface.NORMAL));
                cell.setBackgroundResource(warning
                        ? R.drawable.bg_table_cell_warning
                        : R.drawable.bg_table_cell);
                tableRow.addView(cell);
                editorRow.add(cell);
            }
            worksheetEditors.add(editorRow);
            worksheetTable.addView(tableRow);
        }
    }

    private void showWorksheet(boolean table) {
        boolean available = currentDocument != null && currentDocument.getWorksheet() != null;
        showingTable = table && available;
        resultEditor.setVisibility(showingTable ? View.GONE : View.VISIBLE);
        tableScroll.setVisibility(showingTable ? View.VISIBLE : View.GONE);
        enableResultActions(currentDocument != null && !currentDocument.toPlainText().isEmpty());
    }

    private void showWorkspaceMenu() {
        ArrayList<String> labels = new ArrayList<>();
        if (currentDocument != null && currentDocument.getWorksheet() != null) {
            labels.add(getString(showingTable ? R.string.show_plain_text : R.string.show_table));
        }
        labels.add(getString(R.string.settings));
        new AlertDialog.Builder(this)
                .setTitle(R.string.more_actions)
                .setItems(labels.toArray(new String[0]), (dialog, index) -> {
                    if (labels.size() == 2 && index == 0) showWorksheet(!showingTable);
                    else startActivityForResult(
                            new Intent(this, SettingsActivity.class),
                            REQUEST_SETTINGS
                    );
                })
                .show();
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
        if (currentDocument != null && currentDocument.getWorksheet() != null && showingTable) {
            WorksheetModel original = currentDocument.getWorksheet();
            ArrayList<WorksheetModel.Cell> cells = new ArrayList<>();
            StringBuilder plain = new StringBuilder();
            for (int row = 0; row < worksheetEditors.size(); row++) {
                if (row > 0) plain.append('\n');
                ArrayList<EditText> editorRow = worksheetEditors.get(row);
                for (int column = 0; column < editorRow.size(); column++) {
                    if (column > 0) plain.append('\t');
                    String value = editorRow.get(column).getText().toString().trim();
                    plain.append(value);
                    cells.add(new WorksheetModel.Cell(row, column, 1, 1, value, 1f));
                }
            }
            WorksheetModel editedWorksheet = new WorksheetModel(
                    original.getRowCount(),
                    original.getColumnCount(),
                    cells,
                    original.getPrimaryHeaderRowIndex(),
                    original.getTopologyConfidence(),
                    original.getTextConfidence(),
                    original.getVerificationStatus(),
                    original.getDetectionNote()
            );
            DocumentModel textModel = DocumentModel.fromPlainText(plain.toString());
            return new DocumentModel(
                    currentDocument.getEngineId() + "-edited",
                    textModel.getBlocks(),
                    editedWorksheet
            );
        }
        String edited = resultEditor.getText().toString().trim();
        if (currentDocument == null || !edited.equals(currentDocument.toPlainText())) {
            return DocumentModel.fromPlainText(edited);
        }
        return currentDocument;
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
        for (ArrayList<EditText> row : worksheetEditors) {
            for (EditText cell : row) cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize);
        }
        textScaleValue.setText(getString(R.string.text_scale_value, textScalePercent));
        textSmallerButton.setEnabled(textScalePercent > MIN_TEXT_SCALE);
        textLargerButton.setEnabled(textScalePercent < MAX_TEXT_SCALE);
        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        resultEditor.setTypeface(Typeface.create(dark ? "monospace" : "sans-serif", Typeface.NORMAL));
    }

    private static int clampTextScale(int value) {
        return Math.max(MIN_TEXT_SCALE, Math.min(MAX_TEXT_SCALE, value));
    }

    private void copyResult(boolean automatic) {
        DocumentModel document = effectiveDocument();
        if (document.toPlainText().isEmpty()) return;
        ClipboardWriter.copy(this, document, AppPreferences.isSensitiveClipboard(this));
        updateResultMeta(document, true);
        Toast.makeText(this, automatic ? R.string.copied : R.string.copy_updated, Toast.LENGTH_SHORT).show();
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

    private void showFindDialog() {
        EditText query = new EditText(this);
        query.setSingleLine(true);
        query.setHint(R.string.search_query_hint);
        query.setPadding(dp(20), 0, dp(20), 0);
        new AlertDialog.Builder(this)
                .setTitle(R.string.search)
                .setView(query)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.search, (dialog, which) -> {
                    String needle = query.getText().toString().trim();
                    if (needle.isEmpty()) return;
                    if (showingTable) {
                        if (!findInWorksheet(needle)) {
                            Toast.makeText(this, R.string.search_not_found, Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }
                    String haystack = resultEditor.getText().toString();
                    int index = haystack.toLowerCase(Locale.ROOT)
                            .indexOf(needle.toLowerCase(Locale.ROOT));
                    if (index < 0) {
                        Toast.makeText(this, R.string.search_not_found, Toast.LENGTH_SHORT).show();
                    } else {
                        resultEditor.requestFocus();
                        resultEditor.setSelection(index, index + needle.length());
                    }
                })
                .show();
    }

    private boolean findInWorksheet(String needle) {
        String normalized = needle.toLowerCase(Locale.ROOT);
        for (ArrayList<EditText> row : worksheetEditors) {
            for (EditText cell : row) {
                String value = cell.getText().toString().toLowerCase(Locale.ROOT);
                int index = value.indexOf(normalized);
                if (index >= 0) {
                    cell.requestFocus();
                    cell.setSelection(index, index + needle.length());
                    return true;
                }
            }
        }
        return false;
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

    private void launchPreparedExport(Uri target, ExportType type, ExportDestination destination) {
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
                offerSaveFallback(type);
                return;
            }
            startActivity(Intent.createChooser(intent, getString(R.string.export_destination_title)));
        } catch (ActivityNotFoundException error) {
            offerSaveFallback(type);
        }
    }

    private void offerSaveFallback(ExportType type) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.export_no_app_title)
                .setMessage(R.string.export_no_app_fallback)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.export_save_as, (dialog, which) -> requestExport(type))
                .show();
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

    private static void writeExportPayload(DocumentModel document, OutputStream output, ExportType type)
            throws IOException {
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
        String timestamp = timestamp();
        if (type == ExportType.MARKDOWN) return "BIMO-EasyDocs-" + timestamp + ".md";
        if (type == ExportType.DOCX) return "BIMO-EasyDocs-" + timestamp + ".docx";
        return "BIMO-EasyDocs-" + timestamp + ".xlsx";
    }

    private static String scanPdfFilename() {
        return "BIMO-Scan-" + timestamp() + ".pdf";
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
    }

    private void setBusy(boolean busy) {
        inputProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        outputProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        settingsButton.setEnabled(!busy);
        startScanButton.setEnabled(!busy);
        ocrCameraButton.setEnabled(!busy);
        galleryButton.setEnabled(!busy);
        fileButton.setEnabled(!busy);
        if (busy) enableResultActions(false);
        else if (currentDocument != null && !currentDocument.toPlainText().isEmpty()) {
            enableResultActions(true);
        }
    }

    private void enableResultActions(boolean enabled) {
        setActionEnabled(copyButton, enabled);
        setActionEnabled(shareButton, enabled);
        setActionEnabled(exportButton, enabled);
        boolean worksheet = currentDocument != null && currentDocument.getWorksheet() != null;
        xlsxButton.setVisibility(worksheet ? View.VISIBLE : View.GONE);
        setActionEnabled(xlsxButton, enabled && worksheet);
    }

    private static void setActionEnabled(View action, boolean enabled) {
        action.setEnabled(enabled);
        action.setAlpha(enabled ? 1f : 0.45f);
    }

    private void showFailure(String message) {
        status.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showExportFailure(String message) {
        workspaceStatus.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void persistReadPermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Some providers intentionally grant transient access only.
        }
    }

    private void clearScanPages() {
        for (Uri uri : scanPages) getContentResolver().delete(uri, null, null);
        scanPages.clear();
        scanPageIndex = 0;
    }

    private void restoreState(Bundle state) {
        entryMode = normalizeEntry(state.getString(STATE_ENTRY));
        scanMode = "batch".equals(state.getString(STATE_SCAN_MODE)) ? ScanMode.BATCH : ScanMode.SINGLE;
        ArrayList<String> uriStrings = state.getStringArrayList(STATE_SCAN_URIS);
        if (uriStrings != null) {
            for (String value : uriStrings) scanPages.add(Uri.parse(value));
        }
        scanPageIndex = Math.max(0, Math.min(
                Math.max(0, scanPages.size() - 1),
                state.getInt(STATE_SCAN_INDEX, 0)
        ));
        String restored = state.getString(STATE_RESULT, "");
        showingOutput = state.getBoolean(STATE_OUTPUT, !restored.isEmpty());
        if (!restored.isEmpty()) {
            currentDocument = DocumentModel.fromPlainText(restored);
            resultEditor.setText(restored);
            resultEditor.setSelection(0);
            updateResultMeta(currentDocument, false);
        }
    }

    private void loadQaWorkspace() {
        String sample = "Executive Summary\n\n"
                + "BIMO EasyDocs keeps OCR results in one immediately editable workspace.\n\n"
                + "Copy, share, export, find, and text scaling remain available without mode tabs.";
        currentDocument = DocumentModel.fromPlainText(sample);
        resultEditor.setText(sample);
        resultEditor.setSelection(0);
        updateResultMeta(currentDocument, false);
        workspaceStatus.setText(R.string.workspace_ocr_status);
        showResultSurface();
        enableResultActions(true);
    }

    private static String normalizeEntry(String value) {
        return ENTRY_OCR.equals(value) ? ENTRY_OCR : ENTRY_SCAN;
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty() ? "Unknown processing error" : message;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
            showInputSurface();
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
        output.putString(STATE_RESULT, effectiveDocument().toPlainText());
        output.putBoolean(STATE_OUTPUT, showingOutput);
        output.putString(STATE_ENTRY, entryMode);
        output.putString(STATE_SCAN_MODE, scanMode == ScanMode.BATCH ? "batch" : "single");
        ArrayList<String> values = new ArrayList<>();
        for (Uri uri : scanPages) values.add(uri.toString());
        output.putStringArrayList(STATE_SCAN_URIS, values);
        output.putInt(STATE_SCAN_INDEX, scanPageIndex);
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        if (currentBitmap != null && !currentBitmap.isRecycled()) currentBitmap.recycle();
        super.onDestroy();
    }
}
