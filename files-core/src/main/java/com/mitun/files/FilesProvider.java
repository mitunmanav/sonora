package com.mitun.files;

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

/**
 * Lets another app read the one file the user opened in it. Not exported: an app
 * only gets in through the grant on the intent, and it can never write.
 */
public final class FilesProvider extends ContentProvider {
    /** The address of a file, or null when this file may not be handed out. */
    static Uri uriFor(Context context, File file) {
        return new Uri.Builder().scheme("content")
                .authority(context.getPackageName() + ".filesprovider")
                .appendPath("f").appendEncodedPath(Uri.encode(file.getAbsolutePath(), "/").substring(1))
                .build();
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        String path = uri.getPath();
        if (path == null || !path.startsWith("/f/")) {
            throw new FileNotFoundException("Unknown address");
        }
        File file = new File(path.substring(2));
        try {
            String canonical = file.getCanonicalPath();
            // The app's own private data is not the user's to hand out, except the
            // folder it keeps for them.
            Context context = getContext();
            String data = context.getApplicationInfo().dataDir;
            String files = context.getFilesDir().getCanonicalPath();
            if (FileLogic.isInside(data, canonical) && !FileLogic.isInside(files, canonical)) {
                throw new FileNotFoundException("Private");
            }
            return new File(canonical);
        } catch (IOException e) {
            throw new FileNotFoundException("Unreadable");
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Read only");
        }
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        String path = uri.getPath();
        return path == null ? null : FileLogic.mimeOf(path);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        File file;
        try {
            file = fileFor(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        String[] columns = {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        cursor.addRow(new Object[]{file.getName(), file.length()});
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
