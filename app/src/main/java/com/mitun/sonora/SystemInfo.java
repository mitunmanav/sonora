package com.mitun.sonora;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;

import java.io.File;
import java.util.Locale;

/**
 * Device facts, read once and cached. Everything here is a cheap platform
 * query - no reflection, no shell, no files walked.
 */
public final class SystemInfo {
    private static volatile SystemInfo instance;

    public final String model;
    public final String manufacturer;
    public final String androidVersion;
    public final int sdk;
    public final int widthDp;
    public final int heightDp;
    public final int densityDpi;
    public final boolean lowRam;
    public final long totalMemory;
    public final long availableMemory;
    public final long memoryClassMb;
    public final long javaHeapLimit;
    public final boolean externalStorageMounted;
    public final long externalStorageTotal;
    public final long externalStorageFree;

    private SystemInfo(Context context) {
        Context app = context.getApplicationContext();
        DisplayMetrics metrics = app.getResources().getDisplayMetrics();
        densityDpi = metrics.densityDpi;
        widthDp = Math.round(metrics.widthPixels / metrics.density);
        heightDp = Math.round(metrics.heightPixels / metrics.density);

        model = Build.MODEL == null ? "unknown" : Build.MODEL;
        manufacturer = Build.MANUFACTURER == null ? "unknown" : Build.MANUFACTURER;
        androidVersion = Build.VERSION.RELEASE == null ? "?" : Build.VERSION.RELEASE;
        sdk = Build.VERSION.SDK_INT;

        ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
        lowRam = am != null && am.isLowRamDevice();
        memoryClassMb = am == null ? 16 : am.getMemoryClass();
        long total = 0;
        long available = 0;
        if (am != null) {
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            total = info.totalMem;
            available = info.availMem;
        }
        totalMemory = total;
        availableMemory = available;
        javaHeapLimit = Runtime.getRuntime().maxMemory();

        boolean mounted = Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
        externalStorageMounted = mounted;
        long storageTotal = 0;
        long storageFree = 0;
        if (mounted) {
            try {
                File dir = Environment.getExternalStorageDirectory();
                StatFs stat = new StatFs(dir.getAbsolutePath());
                storageTotal = stat.getTotalBytes();
                storageFree = stat.getAvailableBytes();
            } catch (RuntimeException ignored) {
            }
        }
        externalStorageTotal = storageTotal;
        externalStorageFree = storageFree;
    }

    public static SystemInfo get(Context context) {
        SystemInfo local = instance;
        if (local == null) {
            synchronized (SystemInfo.class) {
                local = instance;
                if (local == null) {
                    local = new SystemInfo(context);
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Heap actually in use, in bytes. */
    public static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /** Native heap in use, in bytes. */
    public static long nativeHeap() {
        return Debug.getNativeHeapAllocatedSize();
    }

    public static String bytes(long value) {
        if (value < 1024) {
            return value + " B";
        }
        if (value < 1024 * 1024) {
            return String.format(Locale.US, "%.0f KB", value / 1024f);
        }
        if (value < 1024L * 1024 * 1024) {
            return String.format(Locale.US, "%.1f MB", value / (1024f * 1024));
        }
        return String.format(Locale.US, "%.2f GB", value / (1024f * 1024 * 1024));
    }
}
