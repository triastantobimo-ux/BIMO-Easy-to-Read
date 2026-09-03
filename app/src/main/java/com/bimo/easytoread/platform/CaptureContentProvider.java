package com.bimo.easytoread.platform;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

public final class CaptureContentProvider extends ContentProvider {
    private static final String CAPTURE_DIRECTORY = "captures";
    private static final String EXPORT_DIRECTORY = "exports";

    public static Uri createCaptureUri(Context context) throws IOException {
        File directory = new File(context.getCacheDir(), CAPTURE_DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create secure capture directory.");
        }
        String filename = String.format(
                Locale.ROOT,
                "capture-%d.jpg",
                System.currentTimeMillis()
        );
        File target = new File(directory, filename);
        if (!target.createNewFile()) throw new IOException("Unable to create capture file.");

        return new Uri.Builder()
                .scheme(ContentResolverScheme.CONTENT)
                .authority(context.getPackageName() + ".captures")
                .appendPath(filename)
                .build();
    }

    public static Uri createExportUri(Context context, String filename) throws IOException {
        if (filename == null || !filename.matches("[A-Za-z0-9._-]+")) {
            throw new IOException("Invalid export filename.");
        }
        File directory = new File(context.getCacheDir(), EXPORT_DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create secure export directory.");
        }
        File target = new File(directory, filename);
        if (target.exists() && !target.delete()) {
            throw new IOException("Unable to replace temporary export file.");
        }

        return new Uri.Builder()
                .scheme(ContentResolverScheme.CONTENT)
                .authority(context.getPackageName() + ".captures")
                .appendPath(EXPORT_DIRECTORY)
                .appendPath(filename)
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        String path = uri == null ? "" : uri.toString().toLowerCase(Locale.ROOT);
        if (path.endsWith(".md")) return "text/markdown";
        if (path.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (path.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (path.endsWith(".pdf")) return "application/pdf";
        return "image/jpeg";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = resolve(uri);
        int flags;
        if (mode.contains("w") && mode.contains("r")) {
            flags = ParcelFileDescriptor.MODE_READ_WRITE
                    | ParcelFileDescriptor.MODE_CREATE;
        } else if (mode.contains("w")) {
            flags = ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_TRUNCATE;
        } else {
            flags = ParcelFileDescriptor.MODE_READ_ONLY;
        }
        return ParcelFileDescriptor.open(file, flags);
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder
    ) {
        File file;
        try {
            file = resolve(uri);
        } catch (FileNotFoundException error) {
            return new MatrixCursor(new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE });
        }

        String[] columns = projection == null
                ? new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE }
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) row.add(file.getName());
            else if (OpenableColumns.SIZE.equals(column)) row.add(file.length());
            else row.add(null);
        }
        return cursor;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        try {
            return resolve(uri).delete() ? 1 : 0;
        } catch (FileNotFoundException error) {
            return 0;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Insert is not supported.");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        Context context = getContext();
        if (context == null) throw new FileNotFoundException("Provider has no context.");
        java.util.List<String> segments = uri.getPathSegments();
        boolean export = segments.size() == 2 && EXPORT_DIRECTORY.equals(segments.get(0));
        boolean capture = segments.size() == 1;
        if (!capture && !export) throw new FileNotFoundException("Invalid provider path.");
        String filename = uri.getLastPathSegment();
        if (filename == null || !filename.matches("[A-Za-z0-9._-]+")) {
            throw new FileNotFoundException("Invalid capture path.");
        }

        try {
            File root = new File(
                    context.getCacheDir(),
                    export ? EXPORT_DIRECTORY : CAPTURE_DIRECTORY
            ).getCanonicalFile();
            File candidate = new File(root, filename).getCanonicalFile();
            if (!candidate.getParentFile().equals(root)) {
                throw new FileNotFoundException("Capture path escaped its secure directory.");
            }
            return candidate;
        } catch (IOException error) {
            throw new FileNotFoundException(error.getMessage());
        }
    }

    private static final class ContentResolverScheme {
        private static final String CONTENT = "content";
    }
}
