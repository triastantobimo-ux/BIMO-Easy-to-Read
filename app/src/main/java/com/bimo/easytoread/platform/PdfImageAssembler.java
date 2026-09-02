package com.bimo.easytoread.platform;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public final class PdfImageAssembler {
    private static final int PORTRAIT_WIDTH = 595;
    private static final int PORTRAIT_HEIGHT = 842;
    private static final int PAGE_MARGIN = 18;

    private PdfImageAssembler() {}

    public static void write(Context context, List<Uri> pages, OutputStream output)
            throws IOException {
        if (pages == null || pages.isEmpty()) throw new IOException("No scan pages selected.");
        try (PdfDocument document = new PdfDocument()) {
            Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            for (int index = 0; index < pages.size(); index++) {
                ImageLoader.Result loaded = ImageLoader.load(context, pages.get(index), 2800);
                Bitmap bitmap = loaded.getBitmap();
                try {
                    boolean landscape = bitmap.getWidth() > bitmap.getHeight();
                    int pageWidth = landscape ? PORTRAIT_HEIGHT : PORTRAIT_WIDTH;
                    int pageHeight = landscape ? PORTRAIT_WIDTH : PORTRAIT_HEIGHT;
                    PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                            pageWidth,
                            pageHeight,
                            index + 1
                    ).create();
                    PdfDocument.Page page = document.startPage(info);
                    Canvas canvas = page.getCanvas();
                    canvas.drawColor(Color.WHITE);
                    RectF destination = fitCenter(
                            bitmap.getWidth(),
                            bitmap.getHeight(),
                            pageWidth,
                            pageHeight
                    );
                    canvas.drawBitmap(bitmap, null, destination, bitmapPaint);
                    document.finishPage(page);
                } finally {
                    if (!bitmap.isRecycled()) bitmap.recycle();
                }
            }
            document.writeTo(output);
        }
    }

    private static RectF fitCenter(
            int sourceWidth,
            int sourceHeight,
            int pageWidth,
            int pageHeight
    ) {
        float availableWidth = pageWidth - PAGE_MARGIN * 2f;
        float availableHeight = pageHeight - PAGE_MARGIN * 2f;
        float scale = Math.min(
                availableWidth / Math.max(1, sourceWidth),
                availableHeight / Math.max(1, sourceHeight)
        );
        float width = sourceWidth * scale;
        float height = sourceHeight * scale;
        float left = (pageWidth - width) / 2f;
        float top = (pageHeight - height) / 2f;
        return new RectF(left, top, left + width, top + height);
    }
}
