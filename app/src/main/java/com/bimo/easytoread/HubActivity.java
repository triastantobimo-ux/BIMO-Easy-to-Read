package com.bimo.easytoread;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.bimo.easytoread.platform.PdfImageAssembler;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HubActivity extends Activity {
    public static final String EXTRA_DESTINATION = "destination";
    public static final String DESTINATION_DOCUMENTS = "documents";
    public static final String DESTINATION_TOOLS = "tools";
    private static final int REQUEST_OPEN_DOCUMENT = 2201;
    private static final int REQUEST_TOOL_IMAGES = 2202;
    private static final int REQUEST_TOOL_OUTPUT = 2203;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayList<Uri> pendingImages = new ArrayList<>();
    private String destination;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppPreferences.wrapLanguage(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        AppPreferences.applyTheme(this);
        super.onCreate(state);
        setContentView(R.layout.activity_hub);

        destination = normalizeDestination(getIntent().getStringExtra(EXTRA_DESTINATION));
        findViewById(R.id.buttonHubBack).setOnClickListener(view -> finish());
        findViewById(R.id.buttonHubSettings).setOnClickListener(view ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.buttonHubPrimary).setOnClickListener(view -> runPrimaryAction());
        renderDestination();
    }

    private void renderDestination() {
        TextView title = findViewById(R.id.hubTitle);
        TextView eyebrow = findViewById(R.id.hubEyebrow);
        TextView intro = findViewById(R.id.hubIntro);
        TextView cardTitle = findViewById(R.id.hubCardTitle);
        TextView cardDescription = findViewById(R.id.hubCardDescription);
        TextView primary = findViewById(R.id.buttonHubPrimary);
        ImageView cardIcon = findViewById(R.id.hubCardIcon);

        if (DESTINATION_TOOLS.equals(destination)) {
            title.setText(R.string.nav_tools);
            eyebrow.setText(R.string.tools_category_convert);
            intro.setText(R.string.tools_intro_v4);
            cardTitle.setText(R.string.tool_image_to_pdf);
            cardDescription.setText(R.string.tool_image_to_pdf_description);
            primary.setText(R.string.tool_choose_images);
            cardIcon.setImageResource(R.drawable.ic_tools);
            AppNavigation.bind(this, NavigationContract.Screen.TOOLS);
        } else {
            title.setText(R.string.nav_documents);
            eyebrow.setText(R.string.documents_library);
            intro.setText(R.string.documents_intro_v4);
            cardTitle.setText(R.string.documents_empty_title);
            cardDescription.setText(R.string.documents_empty_description);
            primary.setText(R.string.documents_open_file);
            cardIcon.setImageResource(R.drawable.ic_folder);
            AppNavigation.bind(this, NavigationContract.Screen.PDF);
        }
    }

    private void runPrimaryAction() {
        if (DESTINATION_TOOLS.equals(destination)) chooseImagesForPdf();
        else openDocument();
    }

    private void openDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] { "application/pdf", "image/*" });
        startActivityForResult(intent, REQUEST_OPEN_DOCUMENT);
    }

    private void chooseImagesForPdf() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_TOOL_IMAGES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQUEST_OPEN_DOCUMENT && data.getData() != null) {
            openSelectedDocument(data.getData());
        } else if (requestCode == REQUEST_TOOL_IMAGES) {
            pendingImages.clear();
            collectUris(data, pendingImages);
            if (pendingImages.isEmpty()) return;
            requestPdfDestination();
        } else if (requestCode == REQUEST_TOOL_OUTPUT && data.getData() != null) {
            writeImagePdf(data.getData());
        }
    }

    private void openSelectedDocument(Uri uri) {
        persistReadPermission(uri);
        String type = getContentResolver().getType(uri);
        boolean pdf = "application/pdf".equals(type)
                || uri.toString().toLowerCase(Locale.ROOT).endsWith(".pdf");
        if (pdf) {
            startActivity(PdfViewerActivity.createIntent(this, uri));
            return;
        }
        Intent forward = MainActivity.createEntryIntent(this, MainActivity.ENTRY_OCR);
        forward.setAction(Intent.ACTION_SEND);
        forward.setType(type == null ? "image/*" : type);
        forward.putExtra(Intent.EXTRA_STREAM, uri);
        forward.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(forward);
    }

    private void requestPdfDestination() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE, "BIMO-EasyDocs-" + timestamp + ".pdf");
        startActivityForResult(intent, REQUEST_TOOL_OUTPUT);
    }

    private void writeImagePdf(Uri target) {
        setBusy(true);
        ArrayList<Uri> pages = new ArrayList<>(pendingImages);
        worker.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(target, "w")) {
                if (output == null) throw new java.io.IOException("Output stream is unavailable.");
                PdfImageAssembler.write(this, pages, output);
                runOnUiThread(() -> {
                    pendingImages.clear();
                    setBusy(false);
                    Toast.makeText(this, R.string.pdf_created, Toast.LENGTH_LONG).show();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(
                            this,
                            getString(R.string.pdf_create_failed, safeMessage(error)),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void setBusy(boolean busy) {
        findViewById(R.id.hubProgress).setVisibility(busy ? View.VISIBLE : View.GONE);
        findViewById(R.id.buttonHubPrimary).setEnabled(!busy);
    }

    private void persistReadPermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException ignored) {
            // Transient permission remains sufficient for the current workflow.
        }
    }

    private void collectUris(Intent data, List<Uri> output) {
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int index = 0; index < clip.getItemCount() && output.size() < 50; index++) {
                Uri uri = clip.getItemAt(index).getUri();
                if (uri != null) {
                    persistReadPermission(uri);
                    output.add(uri);
                }
            }
        } else if (data.getData() != null) {
            persistReadPermission(data.getData());
            output.add(data.getData());
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty() ? "Unknown error" : message;
    }

    private static String normalizeDestination(String value) {
        return DESTINATION_TOOLS.equals(value) ? DESTINATION_TOOLS : DESTINATION_DOCUMENTS;
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}

