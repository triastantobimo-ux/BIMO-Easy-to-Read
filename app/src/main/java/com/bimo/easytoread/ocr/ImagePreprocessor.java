package com.bimo.easytoread.ocr;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

final class ImagePreprocessor {
    private static final int MAX_ENHANCED_DIMENSION = 2400;

    private ImagePreprocessor() {}

    static Bitmap enhanceForSceneText(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        float resize = Math.min(
                1f,
                (float) MAX_ENHANCED_DIMENSION / Math.max(width, height)
        );
        int targetWidth = Math.max(1, Math.round(width * resize));
        int targetHeight = Math.max(1, Math.round(height * resize));

        Bitmap working = source;
        if (targetWidth != width || targetHeight != height) {
            working = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
        }

        float contrast = estimateContrast(working);
        float offset = 128f * (1f - contrast);
        ColorMatrix matrix = new ColorMatrix(new float[] {
                0.2126f * contrast, 0.7152f * contrast, 0.0722f * contrast, 0f, offset,
                0.2126f * contrast, 0.7152f * contrast, 0.0722f * contrast, 0f, offset,
                0.2126f * contrast, 0.7152f * contrast, 0.0722f * contrast, 0f, offset,
                0f, 0f, 0f, 1f, 0f
        });

        Bitmap enhanced = Bitmap.createBitmap(
                working.getWidth(),
                working.getHeight(),
                Bitmap.Config.ARGB_8888
        );
        Paint paint = new Paint(
                Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG
        );
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        new Canvas(enhanced).drawBitmap(working, 0f, 0f, paint);

        if (working != source) working.recycle();
        return enhanced;
    }

    private static float estimateContrast(Bitmap bitmap) {
        int[] histogram = new int[256];
        int step = Math.max(2, Math.max(bitmap.getWidth(), bitmap.getHeight()) / 320);
        int samples = 0;
        for (int y = 0; y < bitmap.getHeight(); y += step) {
            for (int x = 0; x < bitmap.getWidth(); x += step) {
                int pixel = bitmap.getPixel(x, y);
                int luminance = Math.round(
                        Color.red(pixel) * 0.2126f
                                + Color.green(pixel) * 0.7152f
                                + Color.blue(pixel) * 0.0722f
                );
                histogram[Math.max(0, Math.min(255, luminance))]++;
                samples++;
            }
        }
        if (samples == 0) return 1.15f;

        int lowTarget = Math.max(1, Math.round(samples * 0.03f));
        int highTarget = Math.max(1, Math.round(samples * 0.97f));
        int cumulative = 0;
        int low = 0;
        int high = 255;
        for (int value = 0; value < histogram.length; value++) {
            cumulative += histogram[value];
            if (cumulative >= lowTarget) {
                low = value;
                break;
            }
        }
        cumulative = 0;
        for (int value = 0; value < histogram.length; value++) {
            cumulative += histogram[value];
            if (cumulative >= highTarget) {
                high = value;
                break;
            }
        }

        int spread = Math.max(1, high - low);
        float adaptive = 230f / spread;
        return Math.max(1.08f, Math.min(1.55f, adaptive));
    }
}
