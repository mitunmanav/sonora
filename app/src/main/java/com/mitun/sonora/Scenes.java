package com.mitun.sonora;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Bundled aerial stills and device photos. Credit text is shown in About. */
public final class Scenes {
    public static final String AFRICA = "Africa";
    public static final String ASIA = "Asia";
    public static final String EUROPE = "Europe";

    public static final class Scene {
        public final Integer res;
        public final String path;
        public final String name;
        public final String credit;
        public final String region;

        Scene(int res, String name, String credit, String region) {
            this.res = res;
            this.path = null;
            this.name = name;
            this.credit = credit;
            this.region = region;
        }

        Scene(String path, String name) {
            this.res = null;
            this.path = path;
            this.name = name;
            this.credit = null;
            this.region = "Photos";
        }
    }

    /** The bundled scenes only. */
    public static List<Scene> bundled() {
        return Arrays.asList(
                new Scene(R.drawable.sonora_ngorongoro, "Ngorongoro, Tanzania", "Tasha~visual · CC BY-SA 4.0", AFRICA),
                new Scene(R.drawable.sonora_vigo, "Vigo di Fassa, Italy", "Syrio · CC BY-SA 4.0", EUROPE),
                new Scene(R.drawable.sonora_zhuravlyne, "Zhuravlyne Lake, Ukraine", "Medoffer · CC BY-SA 4.0", EUROPE),
                new Scene(R.drawable.sonora_punjab, "Punjab, India", "Bhullargraphic · CC BY-SA 4.0", ASIA),
                new Scene(R.drawable.sonora_dulmen, "Dülmen, Germany", "Dietmar Rabich · CC BY-SA 4.0", EUROPE)
        );
    }

    /** The bundled scenes only, for settings UI that never shows device photos. */
    public static final List<Scene> ALL = bundled();

    /** All scenes based on user preference: bundled, device photos, or both. */
    public static List<Scene> all(Context context) {
        Prefs prefs = new Prefs(context);
        int source = prefs.screensaverPhotoSource();
        List<Scene> out = new ArrayList<>(bundled());
        if (source == 1 || source == 2) {
            List<String> photoPaths = PhotoLoader.photoPaths(context);
            for (String path : photoPaths) {
                String name = new java.io.File(path).getName();
                out.add(new Scene(path, name));
            }
        }
        return out;
    }

    private Scenes() {
    }

    /** Bundled-only safe index for settings UI. */
    public static int safeIndex(int index) {
        return Math.max(0, Math.min(ALL.size() - 1, index));
    }

    /** Full list safe index including device photos. */
    public static int safeIndex(int index, Context context) {
        List<Scene> all = all(context);
        return Math.max(0, Math.min(all.size() - 1, index));
    }

    /** Bundled-only regions for settings UI. */
    public static String[] regions() {
        String[] out = new String[ALL.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = ALL.get(i).region;
        }
        return out;
    }

    /** Full list regions including device photos. */
    public static String[] regions(Context context) {
        List<Scene> all = all(context);
        String[] out = new String[all.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = all.get(i).region;
        }
        return out;
    }

    /** Bundled-only hidden flags for settings UI. */
    public static boolean[] hiddenFlags(Prefs prefs) {
        List<String> hidden = prefs.hiddenScenes();
        boolean[] out = new boolean[ALL.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = hidden.contains(ALL.get(i).name);
        }
        return out;
    }

    /** Full list hidden flags including device photos. */
    public static boolean[] hiddenFlags(Prefs prefs, Context context) {
        List<String> hidden = prefs.hiddenScenes();
        List<Scene> all = all(context);
        boolean[] out = new boolean[all.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = hidden.contains(all.get(i).name);
        }
        return out;
    }
}
