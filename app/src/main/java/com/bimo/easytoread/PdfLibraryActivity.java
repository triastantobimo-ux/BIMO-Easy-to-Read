package com.bimo.easytoread;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextUtils;
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
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_card);
            row.setPadding(dp(14), dp(8), dp(12), dp(8));
            row.setClickable(true);
            row.setFocusable(true);

            ImageView icon = new ImageView(this);
            icon.setImageResource(R.drawable.ic_pdf);
            icon.setContentDescription(null);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(30), dp(30));
            iconParams.setMarginEnd(dp(12));
            row.addView(icon, iconParams);

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView name = new TextView(this);
            name.setText(item.displayName);
            name.setTextColor(getColor(R.color.text_primary));
            name.setTextSize(14f);
            name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            labels.addView(name, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView page = new TextView(this);
            page.setText(getString(R.string.pdf_recent_page, item.lastPage + 1));
            page.setTextColor(getColor(R.color.text_secondary));
            page.setTextSize(11f);
            page.setSingleLine(true);
            LinearLayout.LayoutParams pageParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            pageParams.topMargin = dp(2);
            labels.addView(page, pageParams);
            row.addView(labels, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(58)
            );
            params.topMargin = dp(8);
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
