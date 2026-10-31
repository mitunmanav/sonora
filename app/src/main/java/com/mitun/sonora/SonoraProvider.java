package com.mitun.sonora;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Serves local files to other apps as content URIs.
 *
 * A file:// URI is not allowed to leave the app on modern Android, so sharing
 * a file means handing out a content URI instead. This is the smallest thing
 * that does that: it never grants anything unless the app asks for it, and it
 * is not exported.
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
        File file = fileOf(uri);
        if (file == null || !file.exists()) {
            throw new FileNotFoundException(uri.toString());
        }
        int flags = mode.contains("w")
                ? ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE
                : ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(file, flags);
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

    private static File fileOf(Uri uri) {
        String path = uri.getQueryParameter("path");
        if (path == null || path.length() == 0) {
            return null;
        }
        try {
            return new File(path);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
