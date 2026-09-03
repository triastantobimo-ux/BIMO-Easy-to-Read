package com.bimo.easytoread;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import java.util.Locale;

public final class AppPreferences {
    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String LANGUAGE_INDONESIAN = "id";
    public static final String LANGUAGE_ENGLISH = "en";

    private static final String FILE = "user_settings";
    private static final String KEY_THEME = "theme";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_AUTO_COPY = "auto_copy";
    private static final String KEY_SENSITIVE = "sensitive_clip";
    private static final String KEY_TEXT_SCALE = "text_scale";

    private AppPreferences() {}

    public static Context wrapLanguage(Context base) {
        SharedPreferences preferences = preferences(base);
        String language = preferences.getString(KEY_LANGUAGE, LANGUAGE_INDONESIAN);
        String theme = preferences.getString(KEY_THEME, THEME_SYSTEM);

        Locale locale = Locale.forLanguageTag(language);
        Locale.setDefault(locale);
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(locale);

        if (THEME_LIGHT.equals(theme)) {
            configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | Configuration.UI_MODE_NIGHT_NO;
        } else if (THEME_DARK.equals(theme)) {
            configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | Configuration.UI_MODE_NIGHT_YES;
        }
        return base.createConfigurationContext(configuration);
    }

    public static void applyTheme(Activity activity) {
        String theme = getTheme(activity);
        if (THEME_LIGHT.equals(theme)) {
            activity.setTheme(R.style.Theme_EasyRead_Light);
        } else if (THEME_DARK.equals(theme)) {
            activity.setTheme(R.style.Theme_EasyRead_Dark);
        } else {
            activity.setTheme(R.style.Theme_EasyRead_System);
        }
    }

    public static String getTheme(Context context) {
        return preferences(context).getString(KEY_THEME, THEME_SYSTEM);
    }

    public static String getLanguage(Context context) {
        return preferences(context).getString(KEY_LANGUAGE, LANGUAGE_INDONESIAN);
    }

    public static boolean isAutoCopy(Context context) {
        return preferences(context).getBoolean(KEY_AUTO_COPY, true);
    }

    public static boolean isSensitiveClipboard(Context context) {
        return preferences(context).getBoolean(KEY_SENSITIVE, true);
    }

    public static int getTextScale(Context context) {
        return preferences(context).getInt(KEY_TEXT_SCALE, 100);
    }

    public static void setTextScale(Context context, int percent) {
        preferences(context).edit().putInt(KEY_TEXT_SCALE, percent).apply();
    }

    public static void save(
            Context context,
            String theme,
            String language,
            boolean autoCopy,
            boolean sensitive
    ) {
        preferences(context).edit()
                .putString(KEY_THEME, theme)
                .putString(KEY_LANGUAGE, language)
                .putBoolean(KEY_AUTO_COPY, autoCopy)
                .putBoolean(KEY_SENSITIVE, sensitive)
                .apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
