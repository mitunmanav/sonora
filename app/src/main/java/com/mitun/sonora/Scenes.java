package com.mitun.sonora;

import java.util.Arrays;
import java.util.List;

/** Bundled aerial stills. Credit text is shown in About. */
public final class Scenes {
    public static final String AFRICA = "Africa";
    public static final String ASIA = "Asia";
    public static final String EUROPE = "Europe";

    public static final class Scene {
        public final int res;
        public final String name;
        public final String credit;
        public final String region;

        Scene(int res, String name, String credit, String region) {
            this.res = res;
            this.name = name;
            this.credit = credit;
            this.region = region;
        }
    }

    public static final List<Scene> ALL = Arrays.asList(
            new Scene(R.drawable.sonora_ngorongoro, "Ngorongoro, Tanzania", "Tasha~visual · CC BY-SA 4.0", AFRICA),
            new Scene(R.drawable.sonora_vigo, "Vigo di Fassa, Italy", "Syrio · CC BY-SA 4.0", EUROPE),
            new Scene(R.drawable.sonora_zhuravlyne, "Zhuravlyne Lake, Ukraine", "Medoffer · CC BY-SA 4.0", EUROPE),
            new Scene(R.drawable.sonora_punjab, "Punjab, India", "Bhullargraphic · CC BY-SA 4.0", ASIA),
            new Scene(R.drawable.sonora_dulmen, "Dülmen, Germany", "Dietmar Rabich · CC BY-SA 4.0", EUROPE)
    );

    private Scenes() {
    }

    public static int safeIndex(int index) {
        return Math.max(0, Math.min(ALL.size() - 1, index));
    }

    /** The region of every scene, in order. */
    public static String[] regions() {
        String[] out = new String[ALL.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = ALL.get(i).region;
        }
        return out;
    }

    /** Which scenes the user has hidden, in order. */
    public static boolean[] hiddenFlags(Prefs prefs) {
        List<String> hidden = prefs.hiddenScenes();
        boolean[] out = new boolean[ALL.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = hidden.contains(ALL.get(i).name);
        }
        return out;
    }
}
