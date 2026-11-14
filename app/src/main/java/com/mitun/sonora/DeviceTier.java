package com.mitun.sonora;

import android.content.Context;

/**
 * How much this TV can afford, decided from its RAM.
 *
 * A "1 GB" TV reports well under 1 GiB after the kernel takes its share and a
 * "2 GB" one a little under 2, so the lines sit between the sizes that are sold.
 * Every costly effect asks here, so one setting moves all of them together.
 */
public final class DeviceTier {
    /** About 1 GB. Half-size 16-bit wallpaper, frosted glass, no card shadows. */
    public static final int LOW = 0;
    /** About 2 GB. Full wallpaper held on the GPU, bent glass baked at half size. */
    public static final int MID = 1;
    /** 3 GB and up. The same, with artwork kept sharp at the focused size. */
    public static final int HIGH = 2;
    /** Prefs value: decide from the RAM. */
    public static final int AUTO = -1;

    private static final long GIB = 1024L * 1024L * 1024L;
    private static final long LOW_BELOW = GIB * 14 / 10;
    private static final long MID_BELOW = GIB * 25 / 10;

    private static volatile int cached = Integer.MIN_VALUE;
    /** Set when the system ran critically short of memory while we were on screen. */
    private static volatile boolean degraded;

    private DeviceTier() {
    }

    /** Pure: the tier for this much RAM, honouring a choice made in Settings. */
    public static int resolve(long totalBytes, boolean lowRamFlag, int override) {
        if (override == LOW || override == MID || override == HIGH) {
            return override;
        }
        if (lowRamFlag || totalBytes > 0 && totalBytes < LOW_BELOW) {
            return LOW;
        }
        // An unknown size is treated as the middle, not as the top.
        return totalBytes <= 0 || totalBytes < MID_BELOW ? MID : HIGH;
    }

    /** Pure: what the wallpaper's sides are divided by. */
    public static int wallpaperDivisor(int tier) {
        return tier == LOW ? 2 : 1;
    }

    /** Pure: whether the glass bends the picture, which costs a bitmap per pane. */
    public static boolean bentGlass(int tier) {
        return tier != LOW;
    }

    /** Pure: whether a focused card casts a soft shadow, which costs a redraw per frame. */
    public static boolean cardShadow(int tier) {
        return tier != LOW;
    }

    /** Pure: the width artwork is stored at, given the card and its focused size. */
    public static int artworkWidth(int tier, int cardWidth, float focusScale) {
        return tier == LOW ? cardWidth : Math.round(cardWidth * focusScale);
    }

    public static int get(Context context) {
        watch(context);
        if (degraded) {
            return LOW;
        }
        int tier = cached;
        if (tier == Integer.MIN_VALUE) {
            SystemInfo info = SystemInfo.get(context);
            tier = resolve(info.totalMemory, info.lowRam, new Prefs(context).performance());
            cached = tier;
        }
        return tier;
    }

    private static volatile boolean watching;

    /** The Settings choice drops the cache itself; callers need no refresh call. */
    private static void watch(Context context) {
        if (!watching) {
            synchronized (DeviceTier.class) {
                if (!watching) {
                    watching = true;
                    Prefs.listen(context, (prefs, key) -> {
                        if (Prefs.KEY_PERFORMANCE.equals(key)) {
                            cached = Integer.MIN_VALUE;
                        }
                    });
                }
            }
        }
    }

    /** Forgets the tier and the memory-pressure drop, at app start. */
    public static void refresh() {
        cached = Integer.MIN_VALUE;
        degraded = false;
    }

    /** Drops to the lightest tier until the app next starts. */
    public static void degrade() {
        degraded = true;
    }
}
