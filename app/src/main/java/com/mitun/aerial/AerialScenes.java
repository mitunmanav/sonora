package com.mitun.aerial;

import java.util.Arrays;
import java.util.List;

/** Bundled aerial stills. Credit text is shown in About. */
public final class AerialScenes {
    public static final class Scene {
        public final int res;
        public final String name;
        public final String credit;

        Scene(int res, String name, String credit) {
            this.res = res;
            this.name = name;
            this.credit = credit;
        }
    }

    public static final List<Scene> ALL = Arrays.asList(
            new Scene(R.drawable.aerial_ngorongoro, "Ngorongoro, Tanzania", "Tasha~visual · CC BY-SA 4.0"),
            new Scene(R.drawable.aerial_vigo, "Vigo di Fassa, Italy", "Syrio · CC BY-SA 4.0"),
            new Scene(R.drawable.aerial_zhuravlyne, "Zhuravlyne Lake, Ukraine", "Medoffer · CC BY-SA 4.0"),
            new Scene(R.drawable.aerial_punjab, "Punjab, India", "Bhullargraphic · CC BY-SA 4.0"),
            new Scene(R.drawable.aerial_dulmen, "Dülmen, Germany", "Dietmar Rabich · CC BY-SA 4.0")
    );

    private AerialScenes() {
    }

    public static int safeIndex(int index) {
        return Math.max(0, Math.min(ALL.size() - 1, index));
    }
}
