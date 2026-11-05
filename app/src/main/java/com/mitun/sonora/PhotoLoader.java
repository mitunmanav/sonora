package com.mitun.sonora;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Loads photos from device storage for the screensaver. Very light: scans once,
 * caches paths, decodes on demand, releases when hidden.
 */
public final class PhotoLoader {
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20 MB per photo
    private static final List<String> SUPPORTED = Collections.unmodifiableList(
            java.util.Arrays.asList(".jpg", ".jpeg", ".png", ".webp"));

    private PhotoLoader() {
    }

    /** Photo paths, newest first. Null when not yet scanned. */
    private static volatile List<String> cachedPaths;
    /** When the cache was built, to know when to rebuild. */
    private static volatile long cacheTime;

    /** All photos from the standard Pictures folder, newest first. */
    public static synchronized List<String> photoPaths(Context context) {
        long now = System.currentTimeMillis();
        if (cachedPaths != null && now - cacheTime < 5 * 60 * 1000) {
            return cachedPaths;
        }
        List<String> paths = scanPicturesFolder();
        if (paths.isEmpty()) {
            paths = scanAllStorage(context);
        }
        Collections.sort(paths, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                File fa = new File(a);
                File fb = new File(b);
                return Long.compare(fb.lastModified(), fa.lastModified());
            }
        });
        cachedPaths = paths;
        cacheTime = now;
        return paths;
    }

    /** Decode one photo, sized for the screen. Returns null on failure. */
    public static Bitmap decode(String path, int maxWidth, int maxHeight) {
        if (path == null) {
            return null;
        }
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);
            if (opts.outWidth == 0 || opts.outHeight == 0) {
                return null;
            }
            int sample = 1;
            while (opts.outWidth / sample > maxWidth || opts.outHeight / sample > maxHeight) {
                sample *= 2;
            }
            opts.inSampleSize = sample;
            opts.inJustDecodeBounds = false;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(path, opts);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Wipe the cache so the next call rebuilds it. */
    public static synchronized void invalidateCache() {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            scanDir(Environment.getExternalStorageDirectory(), out);
        }
        return out;
    }

    private static void scanDir(File dir, List<String> out) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scanDir(file, out);
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
        String name = file.getName().toLowerCase();
        for (String ext : SUPPORTED) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
