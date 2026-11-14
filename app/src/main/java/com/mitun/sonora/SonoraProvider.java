package com.mitun.sonora;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import android.content.Context;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * Serves local files to other apps as content URIs.
 *
 * A file:// URI is not allowed to leave the app on modern Android, so sharing
 * a file means handing out a content URI instead. This is the smallest thing
 * that does that: it never grants anything unless the app asks for it, and it
 * is not exported. Only read access within approved roots is permitted.
 */
public final class SonoraProvider extends ContentProvider {
    private static final String AUTHORITY = "com.mitun.sonora.files";

    /** A content URI for a file, shareable with other apps. */
    public static Uri uri(File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendQueryParameter("path", file.getAbsolutePath())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file = fileOf(uri);
        if (file == null || !file.exists()) {
            return null;
        }
        MatrixCursor cursor = new MatrixCursor(
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[]{file.getName(), file.length()});
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        File file = fileOf(uri);
        if (file == null) {
            return "application/octet-stream";
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String type = dot < 0 ? null
                : MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                name.substring(dot + 1).toLowerCase(java.util.Locale.US));
        return type == null ? "application/octet-stream" : type;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && mode.contains("w")) {
            throw new SecurityException("SonoraProvider is read-only");
        }
        File file = fileOf(uri);
        if (file == null || !file.exists()) {
            throw new FileNotFoundException(uri != null ? uri.toString() : "null");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    private File fileOf(Uri uri) {
        return fileOf(getContext(), uri);
    }

    public static File fileOf(Context context, Uri uri) {
        if (context == null || uri == null) {
            return null;
        }
        String path = uri.getQueryParameter("path");
        if (path == null || path.length() == 0) {
            return null;
        }
        try {
            File candidate = new File(path).getCanonicalFile();
            if (!isAllowed(context, candidate)) {
                return null;
            }
            return candidate;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isAllowed(Context context, File file) {
        if (context == null || file == null) {
            return false;
        }
        try {
            String target = file.getCanonicalPath();
            List<File> roots = new ArrayList<>();
            File appFolder = com.mitun.sonora.tools.FileServer.appFolder(context);
            if (appFolder != null) roots.add(appFolder);

            File filesDir = context.getFilesDir();
            if (filesDir != null) roots.add(new File(filesDir, "sonora"));

            File extDir = android.os.Environment.getExternalStorageDirectory();
            if (extDir != null) roots.add(extDir);

            File[] extFilesDirs = context.getExternalFilesDirs(null);
            if (extFilesDirs != null) {
                for (File d : extFilesDirs) {
                    if (d != null) roots.add(d);
                }
            }

            File cacheDir = context.getCacheDir();
            if (cacheDir != null) roots.add(cacheDir);

            File[] extCacheDirs = context.getExternalCacheDirs();
            if (extCacheDirs != null) {
                for (File d : extCacheDirs) {
                    if (d != null) roots.add(d);
                }
            }

            for (File root : roots) {
                if (root == null) continue;
                String rootPath = root.getCanonicalPath();
                if (target.equals(rootPath) || target.startsWith(rootPath + File.separator)) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }
}
