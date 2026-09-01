package com.bimo.easytoread;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public final class HubActivity extends Activity {
    public static final String EXTRA_DESTINATION = "destination";
    public static final String DESTINATION_DOCUMENTS = "documents";
    public static final String DESTINATION_TOOLS = "tools";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppPreferences.wrapLanguage(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        AppPreferences.applyTheme(this);
        super.onCreate(state);
        setContentView(R.layout.activity_hub);

        String destination = getIntent().getStringExtra(EXTRA_DESTINATION);
        boolean tools = DESTINATION_TOOLS.equals(destination);
        ((TextView) findViewById(R.id.hubTitle)).setText(
                tools ? R.string.nav_tools : R.string.nav_documents
        );
        ((TextView) findViewById(R.id.hubDescription)).setText(
                tools ? R.string.tools_hierarchy : R.string.documents_hierarchy
        );

        findViewById(R.id.buttonHubBack).setOnClickListener(view -> finish());
        findViewById(R.id.buttonHubPrimary).setOnClickListener(view -> {
            if (tools) {
                startActivity(new Intent(this, MainActivity.class));
            } else {
                startActivity(new Intent(this, HomeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            }
        });
        findViewById(R.id.buttonNavHome).setOnClickListener(view ->
                startActivity(new Intent(this, HomeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)));
        findViewById(R.id.buttonNavDocuments).setOnClickListener(view -> {
            if (tools) recreateAs(DESTINATION_DOCUMENTS);
        });
        findViewById(R.id.buttonNavScan).setOnClickListener(view ->
                startActivity(new Intent(this, MainActivity.class)));
        findViewById(R.id.buttonNavTools).setOnClickListener(view -> {
            if (!tools) recreateAs(DESTINATION_TOOLS);
        });
        findViewById(R.id.buttonNavSettings).setOnClickListener(view ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void recreateAs(String destination) {
        getIntent().putExtra(EXTRA_DESTINATION, destination);
        recreate();
    }
}
