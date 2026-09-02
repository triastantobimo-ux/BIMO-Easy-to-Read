package com.bimo.easytoread;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public final class HubActivity extends Activity {
    public static final String EXTRA_DESTINATION = "destination";
    public static final String DESTINATION_DOCUMENTS = "documents";
    public static final String DESTINATION_TOOLS = "tools";
    public static final String DESTINATION_ACTIVITY = "activity";
    private static final int REQUEST_OPEN_DOCUMENT = 2201;

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
        renderDestination();

        findViewById(R.id.buttonHubBack).setOnClickListener(view -> finish());
        findViewById(R.id.buttonHubSettings).setOnClickListener(view ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.buttonHubPrimary).setOnClickListener(view -> runPrimaryAction());

        findViewById(R.id.buttonNavHome).setOnClickListener(view ->
                startActivity(new Intent(this, HomeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)));
        findViewById(R.id.buttonNavDocuments).setOnClickListener(view ->
                showDestination(DESTINATION_DOCUMENTS));
        findViewById(R.id.buttonNavScan).setOnClickListener(view ->
                startActivity(new Intent(this, MainActivity.class)));
        findViewById(R.id.buttonNavTools).setOnClickListener(view ->
                showDestination(DESTINATION_TOOLS));
        findViewById(R.id.buttonNavActivity).setOnClickListener(view ->
                showDestination(DESTINATION_ACTIVITY));
    }

    private void renderDestination() {
        TextView title = findViewById(R.id.hubTitle);
        TextView intro = findViewById(R.id.hubIntro);
        TextView description = findViewById(R.id.hubDescription);
        Button primary = findViewById(R.id.buttonHubPrimary);

        if (DESTINATION_TOOLS.equals(destination)) {
            title.setText(R.string.nav_tools);
            intro.setText(R.string.tools_intro);
            description.setText(R.string.tools_available);
            primary.setText(R.string.tools_primary);
            primary.setVisibility(View.VISIBLE);
        } else if (DESTINATION_ACTIVITY.equals(destination)) {
            title.setText(R.string.nav_activity);
            intro.setText(R.string.activity_intro);
            description.setText(R.string.activity_empty);
            primary.setVisibility(View.GONE);
        } else {
            title.setText(R.string.nav_documents);
            intro.setText(R.string.documents_intro);
            description.setText(R.string.documents_hierarchy);
            primary.setText(R.string.documents_primary);
            primary.setVisibility(View.VISIBLE);
        }
    }

    private void runPrimaryAction() {
        if (DESTINATION_DOCUMENTS.equals(destination)) {
            openDocument();
        } else if (DESTINATION_TOOLS.equals(destination)) {
            startActivity(new Intent(this, MainActivity.class));
        }
    }

    private void showDestination(String next) {
        if (next.equals(destination)) return;
        destination = next;
        getIntent().putExtra(EXTRA_DESTINATION, next);
        renderDestination();
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
            // Immediate access is sufficient when a provider does not support persistence.
        }

        String type = getContentResolver().getType(uri);
        Intent forward = new Intent(this, MainActivity.class);
        forward.setAction(Intent.ACTION_SEND);
        forward.setType(type == null ? "*/*" : type);
        forward.putExtra(Intent.EXTRA_STREAM, uri);
        forward.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(forward);
    }

    private static String normalizeDestination(String value) {
        if (DESTINATION_TOOLS.equals(value) || DESTINATION_ACTIVITY.equals(value)) return value;
        return DESTINATION_DOCUMENTS;
    }
}
