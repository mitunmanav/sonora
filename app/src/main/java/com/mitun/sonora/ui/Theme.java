package com.mitun.sonora.ui;

import android.content.Context;

import com.mitun.sonora.Prefs;

/**
 * The colours of everything that is not the home screen, in dark and in light.
 *
 * A focused surface is white with dark ink in both, as on tvOS, so only what sits
 * at rest changes. The home screen stays over its wallpaper in both.
 */
public final class Theme {
    /** Dark ink on a focused white surface, in both appearances. */
    public static final int INK_ON_FOCUS = 0xFF101C26;
    public static final int INK_ON_FOCUS_SOFT = 0xCC101C26;
    public static final int DANGER_ON_FOCUS = 0xFF8C1D18;

    private static boolean light;
    private static volatile Context app;

    private Theme() {
    }

    /**
     * Reads the choice. Each screen calls this as it starts.
     *
     * @return true when it differs from what was in use, so a screen that is
     *         already built knows it has to be built again
     */
    public static boolean load(Context context) {
        watch(context);
        boolean wanted = new Prefs(context).lightAppearance();
        boolean changed = wanted != light;
        light = wanted;
        return changed;
    }

    /** Keeps the cached choice right when the pref is rewritten from anywhere. */
    private static void watch(Context context) {
        if (app == null) {
            synchronized (Theme.class) {
                if (app == null) {
                    app = context.getApplicationContext();
                    Prefs.listen(app, (prefs, key) -> light = new Prefs(app).lightAppearance());
                }
            }
        }
    }

    public static boolean isLight() {
        return light;
    }

    /** Titles and labels. */
    public static int ink() {
        return light ? 0xFF1C1C1E : 0xFFFFFFFF;
    }

    /** Labels of rows at rest. */
    public static int inkSoft() {
        return light ? 0xE61C1C1E : 0xE6FFFFFF;
    }

    /** Values and supporting text. */
    public static int inkSecondary() {
        return light ? 0xB33C3C43 : 0xB3FFFFFF;
    }

    /** Section headings, notes and chevrons. */
    public static int inkTertiary() {
        return light ? 0x8C3C3C43 : 0x8CFFFFFF;
    }

    public static int danger() {
        return light ? 0xFFD70015 : 0xFFFF6B6B;
    }

    /** A list row at rest. */
    public static int rowRest() {
        return light ? 0x14000000 : 0x17FFFFFF;
    }

    /** The shade over the blurred wallpaper behind the tools. */
    public static int backdropScrim() {
        return light ? 0xD1F2F2F7 : 0xB3000000;
    }

    /** The surface of a modal task. */
    public static int modal() {
        return light ? 0xF5F2F2F7 : 0xF7000000;
    }

    /** The two ends of the surface of a plain panel. */
    public static int[] panel() {
        return light ? new int[]{0xFFF5F5F7, 0xFFE8E8ED} : new int[]{0xFF08131D, 0xFF050A10};
    }

    /** The veil over frosted glass, which keeps marks on it readable. */
    public static int glassVeil() {
        return light ? 0x8CFFFFFF : 0x33000000;
    }

    /** The dim over the home screen behind Control Center: tvOS leaves 70%. */
    public static int dim() {
        return 0x4D000000;
    }
}
