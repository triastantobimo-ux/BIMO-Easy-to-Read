package com.bimo.easytoread.platform;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.PersistableBundle;
import com.bimo.easytoread.core.DocumentModel;
import com.bimo.easytoread.core.DocumentRenderer;

public final class ClipboardWriter {
    private static final String EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE";

    private ClipboardWriter() {}

    public static void copy(Context context, DocumentModel document, boolean sensitive) {
        ClipboardManager manager =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        String plain = document.toPlainText();
        String html = DocumentRenderer.toHtml(document);
        ClipData clip = ClipData.newHtmlText("OCR result", plain, html);

        if (sensitive) {
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(EXTRA_IS_SENSITIVE, true);
            ClipDescription description = clip.getDescription();
            description.setExtras(extras);
        }
        manager.setPrimaryClip(clip);
    }
}
