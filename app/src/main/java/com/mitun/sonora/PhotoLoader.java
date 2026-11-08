package com.mitun.sonora;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Loads photos from device storage for the screensaver. Very light: scans once,
 * caches paths, decodes on demand, releases when hidden.
 */
public final class PhotoLoader {
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20 MB per photo
    private static final long CACHE_MS = 5 * 60 * 1000;
    /** Far deeper than any camera folder; a bound keeps a weird tree finite. */
    private static final int MAX_DEPTH = 32;
    private static final List<String> SUPPORTED = Collections.unmodifiableList(
            java.util.Arrays.asList(".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif"));

    private PhotoLoader() {
    }

    /** Photo paths, newest first. Null when not yet scanned. */
    private static volatile List<String> cachedPaths;
    /** When the cache was built, to know when to rebuild. */
    private static volatile long cacheTime;

    /**
     * All photos from the standard Pictures folder, newest first.
     *
     * The scan is not synchronized: holding a lock across a minutes-long walk
     * could stall the main thread. Callers run on their own thread and the
     * result is published at the end, so a reader never sees a half list.
     */
    public static List<String> photoPaths(Context context) {
        List<String> cached = cachedPaths;
        if (cached != null && SystemClock.elapsedRealtime() - cacheTime < CACHE_MS) {
            return cached;
        }
        List<String> paths = scanPicturesFolder();
        if (paths.isEmpty() && !Thread.currentThread().isInterrupted()) {
            paths = scanAllStorage(context);
        }
        // lastModified is a filesystem call: two of them per comparison made
        // the sort itself a storage benchmark, so each file is stat'ed once.
        final java.util.Map<String, Long> modified = new java.util.HashMap<>();
        for (String path : paths) {
            modified.put(path, new File(path).lastModified());
        }
        Collections.sort(paths, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                Long ma = modified.get(a);
                Long mb = modified.get(b);
                return Long.compare(mb == null ? 0 : mb, ma == null ? 0 : ma);
            }
        });
        if (!Thread.currentThread().isInterrupted()) {
            // An abandoned scan publishes nothing: the next caller rescans
            // rather than trusting a partial list.
            cacheTime = SystemClock.elapsedRealtime();
            cachedPaths = paths;
        }
        return paths;
    }

    /** Decode one photo, sized to cover the screen. Returns null on failure. */
    public static Bitmap decode(String path, int maxWidth, int maxHeight, Bitmap.Config config) {
        if (path == null) {
            return null;
        }
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);
            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                return null;
            }
            int reqW = Math.max(1, maxWidth);
            int reqH = Math.max(1, maxHeight);
            int sample = 1;
            // Cover-fit sampling, like Ui.decodeScene: halve while both sides
            // still clear the target.
            while (opts.outWidth / (sample * 2) >= reqW
                    && opts.outHeight / (sample * 2) >= reqH) {
                sample *= 2;
            }
            opts.inSampleSize = sample;
            opts.inJustDecodeBounds = false;
            opts.inPreferredConfig = config;
            Bitmap bitmap = BitmapFactory.decodeFile(path, opts);
            if (bitmap == null || Build.VERSION.SDK_INT < 24) {
                // android.media.ExifInterface exists from API 24; below it a
                // rotated photo is accepted as it lies.
                return bitmap;
            }
            int orientation = new ExifInterface(path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            return rotate(bitmap, orientation);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Turns a decoded photo upright according to its EXIF orientation tag. */
    private static Bitmap rotate(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(270);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(270);
                break;
            default:
                return bitmap;
        }
        try {
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
            }
            return rotated;
        } catch (OutOfMemoryError e) {
            return bitmap;
        }
    }

    /** Wipe the cache so the next call rebuilds it. */
    public static void invalidateCache() {
        cachedPaths = null;
        cacheTime = 0;
    }

    private static List<String> scanPicturesFolder() {
        List<String> out = new ArrayList<>();
        File pictures = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        if (pictures == null || !pictures.exists() || !pictures.isDirectory()) {
            return out;
        }
        scanDir(pictures, out);
        return out;
    }

    private static List<String> scanAllStorage(Context context) {
        List<String> out = new ArrayList<>();
        // The full walk only runs where the app can actually read all of
        // storage: MANAGE_EXTERNAL_STORAGE on 30+, the read permission below.
        if (hasStorageAccess(context)) {
            scanDir(Environment.getExternalStorageDirectory(), out);
        }
        return out;
    }

    /** Whether the app can read photos off storage at all. */
    public static boolean hasStorageAccess(Context context) {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        return context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static void scanDir(File dir, List<String> out) {
        scanDir(dir, out, new HashSet<String>(), 0);
    }

    private static void scanDir(File dir, List<String> out, Set<String> visited, int depth) {
        if (dir == null || depth > MAX_DEPTH || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        // Canonical paths catch a symlink that points back into the tree.
        String key;
        try {
            key = dir.getCanonicalPath();
        } catch (IOException e) {
            key = dir.getAbsolutePath();
        }
        if (!visited.add(key)) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (Thread.currentThread().isInterrupted()) {
                // The screen saver hid while the walk was still going.
                return;
            }
            if (file.isDirectory()) {
                scanDir(file, out, visited, depth + 1);
            } else if (isPhoto(file)) {
                out.add(file.getAbsolutePath());
            }
        }
    }

    private static boolean isPhoto(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        if (file.length() > MAX_FILE_SIZE) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        for (String ext : SUPPORTED) {
            if (name.endsWith(ext)) {
                // BitmapFactory cannot read HEIF before API 28; a photo that
                // never decodes only buys the saver a beat of black.
                return Build.VERSION.SDK_INT >= 28 || !(".heic".equals(ext) || ".heif".equals(ext));
            }
        }
        return false;
    }
}
