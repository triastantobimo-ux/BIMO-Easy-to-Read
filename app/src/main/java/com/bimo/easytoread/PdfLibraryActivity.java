package com.bimo.easytoread;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** Dedicated PDF entry point. Images and OCR are intentionally routed elsewhere. */
public final class PdfLibraryActivity extends Activity {
    private static final int REQUEST_OPEN_PDF = 2301;
    private LinearLayout recentContainer;
    private TextView emptyState;
    private Button clearRecent;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppPreferences.wrapLanguage(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        AppPreferences.applyTheme(this);
        super.onCreate(state);
        setContentView(R.layout.activity_pdf_library);

        recentContainer = findViewById(R.id.pdfRecentContainer);
        emptyState = findViewById(R.id.pdfRecentEmpty);
        clearRecent = findViewById(R.id.buttonPdfClearRecent);
        findViewById(R.id.buttonPdfLibraryBack).setOnClickListener(view -> finish());
        findViewById(R.id.buttonPdfLibrarySettings).setOnClickListener(view ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.buttonPdfOpen).setOnClickListener(view -> openPicker());
        clearRecent.setOnClickListener(view -> {
            PdfSessionStore.clearRecent(this);
            renderRecent();
        });
        AppNavigation.bind(this, NavigationContract.Screen.PDF);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderRecent();
    }

    private void openPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_OPEN_PDF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OPEN_PDF || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        int offered = data.getFlags();
        boolean writable = (offered & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0;
        int persistableFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (writable) persistableFlags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        try {
            getContentResolver().takePersistableUriPermission(uri, persistableFlags);
        } catch (SecurityException ignored) {
            // Immediate grant remains valid for this Activity stack.
        }
        String name = resolveDisplayName(uri);
        PdfSessionStore.remember(this, uri, name, 0, writable);
        startActivity(PdfViewerActivity.createIntent(this, uri));
    }

    private void renderRecent() {
        List<PdfSessionStore.RecentPdf> items = PdfSessionStore.getRecent(this);
        recentContainer.removeAllViews();
        boolean empty = items.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        clearRecent.setVisibility(empty ? View.GONE : View.VISIBLE);
        for (PdfSessionStore.RecentPdf item : items) {
            Button row = new Button(this);
            row.setAllCaps(false);
            row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            row.setText(item.displayName + "\n" + getString(
                    R.string.pdf_recent_page,
                    item.lastPage + 1
            ));
            row.setTextColor(getColor(R.color.text_primary));
            row.setTextSize(15f);
            row.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pdf, 0, 0, 0);
            row.setCompoundDrawablePadding(dp(14));
            row.setBackgroundResource(R.drawable.bg_card);
            row.setPadding(dp(18), dp(10), dp(16), dp(10));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(72)
            );
            params.topMargin = dp(10);
            row.setLayoutParams(params);
            row.setOnClickListener(view -> startActivity(
                    PdfViewerActivity.createIntent(this, item.uri)
            ));
            recentContainer.addView(row);
        }
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
            Toast.makeText(this, R.string.pdf_open_failed, Toast.LENGTH_SHORT).show();
        }
        String segment = uri.getLastPathSegment();
        return segment == null ? getString(R.string.pdf_document) : segment;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

