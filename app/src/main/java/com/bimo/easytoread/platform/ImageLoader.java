package com.bimo.easytoread.platform;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.IOException;

public final class ImageLoader {
    public static final class Result {
        private final Bitmap bitmap;
        private final boolean lowResolution;

        Result(Bitmap bitmap, boolean lowResolution) {
            this.bitmap = bitmap;
            this.lowResolution = lowResolution;
        }

        public Bitmap getBitmap() { return bitmap; }
        public boolean isLowResolution() { return lowResolution; }
    }

    private ImageLoader() {}

    public static Result load(Context context, Uri uri, int maxDimension) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "r")) {
            if (descriptor == null) throw new IOException("Image descriptor is unavailable.");
            BitmapFactory.decodeFileDescriptor(descriptor.getFileDescriptor(), null, bounds);
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Unsupported or damaged image.");
        }

        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / (sample * 2) >= maxDimension) sample *= 2;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap decoded;
        try (ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "r")) {
            if (descriptor == null) throw new IOException("Image descriptor is unavailable.");
            decoded = BitmapFactory.decodeFileDescriptor(descriptor.getFileDescriptor(), null, options);
        }
        if (decoded == null) throw new IOException("Bitmap decoder returned no image.");

        int rotation = readRotation(resolver, uri);
        Bitmap oriented = rotate(decoded, rotation);
        if (oriented != decoded) decoded.recycle();

        boolean lowResolution = Math.min(oriented.getWidth(), oriented.getHeight()) < 720;
        return new Result(oriented, lowResolution);
    }

    private static int readRotation(ContentResolver resolver, Uri uri) {
        try (ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "r")) {
            if (descriptor == null) return 0;
            ExifInterface exif = new ExifInterface(descriptor.getFileDescriptor());
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) return 90;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_180) return 180;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        } catch (Exception ignored) {
            // Missing EXIF is not a processing failure.
        }
        return 0;
    }

    private static Bitmap rotate(Bitmap source, int degrees) {
        if (degrees == 0) return source;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(
                source,
                0,
                0,
                source.getWidth(),
                source.getHeight(),
                matrix,
                true
        );
    }
}
