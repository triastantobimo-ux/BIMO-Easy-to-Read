package com.bimo.easytoread;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.Toast;

public final class SettingsActivity extends Activity {
    private RadioGroup themeGroup;
    private RadioGroup languageGroup;
    private CheckBox autoCopy;
    private CheckBox sensitive;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppPreferences.wrapLanguage(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        AppPreferences.applyTheme(this);
        super.onCreate(state);
        setContentView(R.layout.activity_settings);

        themeGroup = findViewById(R.id.themeGroup);
        languageGroup = findViewById(R.id.languageGroup);
        autoCopy = findViewById(R.id.checkAutoCopy);
        sensitive = findViewById(R.id.checkSensitive);

        selectCurrentValues();
        findViewById(R.id.buttonSaveSettings).setOnClickListener(view -> saveAndClose());
    }

    private void selectCurrentValues() {
        String theme = AppPreferences.getTheme(this);
        if (AppPreferences.THEME_LIGHT.equals(theme)) {
            themeGroup.check(R.id.themeLight);
        } else if (AppPreferences.THEME_DARK.equals(theme)) {
            themeGroup.check(R.id.themeDark);
        } else {
            themeGroup.check(R.id.themeSystem);
        }

        String language = AppPreferences.getLanguage(this);
        languageGroup.check(
                AppPreferences.LANGUAGE_ENGLISH.equals(language)
                        ? R.id.languageEnglish
                        : R.id.languageIndonesian
        );
        autoCopy.setChecked(AppPreferences.isAutoCopy(this));
        sensitive.setChecked(AppPreferences.isSensitiveClipboard(this));
    }

    private void saveAndClose() {
        String theme;
        if (themeGroup.getCheckedRadioButtonId() == R.id.themeLight) {
            theme = AppPreferences.THEME_LIGHT;
        } else if (themeGroup.getCheckedRadioButtonId() == R.id.themeDark) {
            theme = AppPreferences.THEME_DARK;
        } else {
            theme = AppPreferences.THEME_SYSTEM;
        }

        String language = languageGroup.getCheckedRadioButtonId() == R.id.languageEnglish
                ? AppPreferences.LANGUAGE_ENGLISH
                : AppPreferences.LANGUAGE_INDONESIAN;

        AppPreferences.save(this, theme, language, autoCopy.isChecked(), sensitive.isChecked());
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }
}
