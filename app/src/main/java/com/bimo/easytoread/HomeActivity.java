package com.bimo.easytoread;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

public final class HomeActivity extends Activity {
    private static final int REQUEST_OPEN_DOCUMENT = 2101;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppPreferences.wrapLanguage(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        AppPreferences.applyTheme(this);
        super.onCreate(state);
        setContentView(R.layout.activity_home);

        findViewById(R.id.buttonOpenDocument).setOnClickListener(view -> openDocument());
        findViewById(R.id.buttonScanDocument).setOnClickListener(view -> openScan());
        findViewById(R.id.buttonOcrScanner).setOnClickListener(view -> openOcr());
        findViewById(R.id.buttonPdfTools).setOnClickListener(view ->
                openHub(HubActivity.DESTINATION_TOOLS));
        findViewById(R.id.buttonHomeProfile).setOnClickListener(view -> openSettings());
        AppNavigation.bind(this, NavigationContract.Screen.HOME);
    }

    private void openScan() {
        startActivity(MainActivity.createEntryIntent(this, MainActivity.ENTRY_SCAN));
    }

    private void openOcr() {
        startActivity(MainActivity.createEntryIntent(this, MainActivity.ENTRY_OCR));
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void openHub(String destination) {
        startActivity(new Intent(this, HubActivity.class)
                .putExtra(HubActivity.EXTRA_DESTINATION, destination));
    }

    private void openDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "application/pdf",
                "image/*"
        });
        startActivityForResult(intent, REQUEST_OPEN_DOCUMENT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OPEN_DOCUMENT || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException ignored) {
            // Immediate read access is sufficient when persistence is unavailable.
        }

        String type = getContentResolver().getType(uri);
        boolean pdf = "application/pdf".equals(type)
                || uri.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
        if (pdf) {
            startActivity(PdfViewerActivity.createIntent(this, uri));
        } else {
            Intent forward = MainActivity.createEntryIntent(this, MainActivity.ENTRY_OCR);
            forward.setAction(Intent.ACTION_SEND);
            forward.setType(type == null ? "image/*" : type);
            forward.putExtra(Intent.EXTRA_STREAM, uri);
            forward.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(forward);
        }
    }
}
