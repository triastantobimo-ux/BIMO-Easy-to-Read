package com.bimo.easytoread;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

public final class HomeActivity extends Activity {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppPreferences.wrapLanguage(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        AppPreferences.applyTheme(this);
        super.onCreate(state);
        setContentView(R.layout.activity_home);

        findViewById(R.id.buttonOpenDocument).setOnClickListener(view ->
                startActivity(new Intent(this, PdfLibraryActivity.class)));
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

}

