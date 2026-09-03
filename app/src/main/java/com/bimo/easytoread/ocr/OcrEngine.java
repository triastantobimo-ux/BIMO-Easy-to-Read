package com.bimo.easytoread.ocr;

import android.graphics.Bitmap;
import com.bimo.easytoread.core.DocumentModel;

public interface OcrEngine extends AutoCloseable {
    interface Callback {
        void onSuccess(DocumentModel document);
        void onFailure(Throwable error);
    }

    void recognize(Bitmap bitmap, Callback callback);

    @Override
    void close();
}
