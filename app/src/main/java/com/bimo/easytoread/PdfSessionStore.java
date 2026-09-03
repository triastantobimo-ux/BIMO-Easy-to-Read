package com.bimo.easytoread;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Local-only PDF reader state. No document content is copied into preferences. */
public final class PdfSessionStore {
    private static final String FILE = "pdf_reader_state";
    private static final String KEY_RECENT = "recent";
    private static final int MAX_RECENT = 10;

    public static final class RecentPdf {
        public final Uri uri;
        public final String displayName;
        public final int lastPage;
        public final boolean writable;
        public final long openedAt;

        RecentPdf(Uri uri, String displayName, int lastPage, boolean writable, long openedAt) {
            this.uri = uri;
            this.displayName = displayName;
            this.lastPage = Math.max(0, lastPage);
            this.writable = writable;
            this.openedAt = openedAt;
        }
    }

    private PdfSessionStore() {}

    public static synchronized void remember(
            Context context,
            Uri uri,
            String displayName,
            int lastPage,
            boolean writable
    ) {
        List<RecentPdf> items = new ArrayList<>(getRecent(context));
        items.removeIf(item -> item.uri.equals(uri));
        items.add(0, new RecentPdf(
                uri,
                displayName == null || displayName.trim().isEmpty() ? "PDF" : displayName,
                lastPage,
                writable,
                System.currentTimeMillis()
        ));
        if (items.size() > MAX_RECENT) items = items.subList(0, MAX_RECENT);
        writeRecent(context, items);
    }

    public static synchronized void updatePage(Context context, Uri uri, int page) {
        List<RecentPdf> items = new ArrayList<>(getRecent(context));
        boolean changed = false;
        for (int index = 0; index < items.size(); index++) {
            RecentPdf item = items.get(index);
            if (item.uri.equals(uri)) {
                items.set(index, new RecentPdf(
                        item.uri,
                        item.displayName,
                        page,
                        item.writable,
                        item.openedAt
                ));
                changed = true;
                break;
            }
        }
        if (changed) writeRecent(context, items);
    }

    public static int getLastPage(Context context, Uri uri) {
        for (RecentPdf item : getRecent(context)) {
            if (item.uri.equals(uri)) return item.lastPage;
        }
        return 0;
    }

    public static boolean isWritable(Context context, Uri uri) {
        for (RecentPdf item : getRecent(context)) {
            if (item.uri.equals(uri)) return item.writable;
        }
        return false;
    }

    public static synchronized List<RecentPdf> getRecent(Context context) {
        String raw = preferences(context).getString(KEY_RECENT, "[]");
        ArrayList<RecentPdf> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) continue;
                String uri = item.optString("uri", "");
                if (uri.isEmpty()) continue;
                result.add(new RecentPdf(
                        Uri.parse(uri),
                        item.optString("name", "PDF"),
                        item.optInt("page", 0),
                        item.optBoolean("writable", false),
                        item.optLong("openedAt", 0L)
                ));
            }
        } catch (JSONException ignored) {
            // Corrupt metadata is treated as an empty recent list; PDF files are untouched.
        }
        return Collections.unmodifiableList(result);
    }

    public static synchronized void clearRecent(Context context) {
        preferences(context).edit().remove(KEY_RECENT).apply();
    }

    public static synchronized boolean toggleBookmark(Context context, Uri uri, int page) {
        String key = bookmarkKey(uri);
        Set<String> pages = new LinkedHashSet<>(
                preferences(context).getStringSet(key, Collections.emptySet())
        );
        String value = Integer.toString(Math.max(0, page));
        boolean added;
        if (pages.contains(value)) {
            pages.remove(value);
            added = false;
        } else {
            pages.add(value);
            added = true;
        }
        preferences(context).edit().putStringSet(key, pages).apply();
        return added;
    }

    public static boolean isBookmarked(Context context, Uri uri, int page) {
        return preferences(context)
                .getStringSet(bookmarkKey(uri), Collections.emptySet())
                .contains(Integer.toString(Math.max(0, page)));
    }

    public static List<Integer> getBookmarks(Context context, Uri uri) {
        Set<String> stored = preferences(context)
                .getStringSet(bookmarkKey(uri), Collections.emptySet());
        ArrayList<Integer> result = new ArrayList<>();
        for (String value : stored) {
            try {
                result.add(Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
                // Ignore invalid metadata only.
            }
        }
        Collections.sort(result);
        return result;
    }

    private static void writeRecent(Context context, List<RecentPdf> items) {
        JSONArray array = new JSONArray();
        for (RecentPdf item : items) {
            JSONObject object = new JSONObject();
            try {
                object.put("uri", item.uri.toString());
                object.put("name", item.displayName);
                object.put("page", item.lastPage);
                object.put("writable", item.writable);
                object.put("openedAt", item.openedAt);
                array.put(object);
            } catch (JSONException ignored) {
                // Values are primitives; this is a defensive boundary.
            }
        }
        preferences(context).edit().putString(KEY_RECENT, array.toString()).apply();
    }

    private static String bookmarkKey(Uri uri) {
        return "bookmarks_" + stableHash(uri.toString());
    }

    private static String stableHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(24);
            for (int index = 0; index < 12; index++) {
                result.append(String.format(java.util.Locale.ROOT, "%02x", bytes[index]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}

