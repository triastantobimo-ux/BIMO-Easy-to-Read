package com.bimo.easytoread;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Compatibility routing facade retained for callers created before BIMO PDF Core. */
public final class PdfViewerActivity {
    private PdfViewerActivity() {}

    public static Intent createIntent(Context context, Uri uri) {
        return PdfCompatActivity.createIntent(context, uri);
    }
}
