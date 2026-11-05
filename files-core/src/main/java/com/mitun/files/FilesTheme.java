package com.mitun.files;

import android.content.Context;
import android.util.TypedValue;

/** The system colours of an Apple desktop, in dark and light, in one place. */
final class FilesTheme {
    final boolean light;
    final int window;
    final int sidebar;
    final int ink;
    final int inkSecondary;
    final int inkTertiary;
    final int accent;
    final int stripe;
    final int separator;
    final int hover;
    final int sheet;
    final int danger;
    final int dim;

    FilesTheme(boolean light) {
        this.light = light;
        window = light ? 0xFFFFFFFF : 0xFF1E1E1E;
        sidebar = light ? 0xFFE9E9EE : 0xFF2C2C2E;
        ink = light ? 0xFF1D1D1F : 0xFFF5F5F7;
        inkSecondary = light ? 0xFF6E6E73 : 0xFF98989D;
        inkTertiary = light ? 0xFF8E8E93 : 0xFF6E6E73;
        accent = light ? 0xFF007AFF : 0xFF0A84FF;
        stripe = light ? 0x08000000 : 0x0AFFFFFF;
        separator = light ? 0x1F000000 : 0x24FFFFFF;
        hover = light ? 0x1A000000 : 0x24FFFFFF;
        sheet = light ? 0xFFF5F5F7 : 0xFF323234;
        danger = light ? 0xFFD70015 : 0xFFFF453A;
        dim = light ? 0x33000000 : 0x66000000;
    }

    static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    /** About 1 GB: no picture thumbnails, and Quick Look decodes at half size. */
    static boolean lowMemory(Context context) {
        android.app.ActivityManager manager = (android.app.ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        android.app.ActivityManager.MemoryInfo info = new android.app.ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        return manager.isLowRamDevice() || info.totalMem > 0 && info.totalMem < 1400L * 1024L * 1024L;
    }
}
