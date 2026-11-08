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
            // Handed out only where the file manager itself can point: the storage
            // roots and the app's own folder. A link reaching anywhere else is
            // refused, whatever path it came in on.
            Context context = getContext();
            boolean inside = FileLogic.isInside(context.getFilesDir().getCanonicalPath(),
                    canonical);
            for (Places.Place place : Places.locations(context)) {
                inside |= FileLogic.isInside(place.folder.getCanonicalPath(), canonical);
            }
            if (!inside) {
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
        File file = fileFor(uri);
        if (!file.isFile()) {
            throw new FileNotFoundException("Not a file");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        String path = uri.getPath();
        return path == null ? null : FileLogic.mimeOf(new File(path).getName());
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
        if (!file.isFile()) {
            return null;
        }
        String[] columns = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) {
                row[i] = file.getName();
            } else if (OpenableColumns.SIZE.equals(columns[i])) {
                row[i] = file.length();
            }
        }
        // A caller that asks for no columns gets no row either; a row with
        // zero columns is a legal but strange answer.
        if (columns.length > 0) {
            cursor.addRow(row);
        }
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
