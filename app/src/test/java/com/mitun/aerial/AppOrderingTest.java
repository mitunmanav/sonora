package com.mitun.aerial;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class AppOrderingTest {
    private static AppEntry app(String pkg, String label) {
        return new AppEntry(label, pkg, null, null, null, 0, false);
    }

    @Test
    public void keepsSavedOrderAndAppendsNewAppsAlphabetically() {
        List<AppEntry> apps = Arrays.asList(
                app("com.zed", "Zed"),
                app("com.alpha", "Alpha"),
                app("com.beta", "Beta"),
                app("com.gamma", "Gamma")
        );
        List<AppEntry> ordered = AppOrdering.order(apps, "com.gamma\ncom.missing\ncom.zed", Collections.emptySet());
        assertEquals(Arrays.asList("com.gamma", "com.zed", "com.alpha", "com.beta"),
                Arrays.asList(ordered.get(0).packageName, ordered.get(1).packageName,
                        ordered.get(2).packageName, ordered.get(3).packageName));
    }

    @Test
    public void removesHiddenAndSpecialEntries() {
        AppEntry settings = new AppEntry("Settings", null, null, null, null, 0, true);
        List<AppEntry> apps = Arrays.asList(app("com.keep", "Keep"), app("com.hide", "Hide"), settings);
        List<AppEntry> ordered = AppOrdering.order(apps, "", new HashSet<>(Collections.singletonList("com.hide")));
        assertEquals(1, ordered.size());
        assertEquals("com.keep", ordered.get(0).packageName);
    }

    @Test
    public void savesOnlyRealApps() {
        AppEntry settings = new AppEntry("Settings", null, null, null, null, 0, true);
        List<AppEntry> apps = Arrays.asList(app("com.a", "A"), settings, app("com.b", "B"));
        assertEquals("com.a\ncom.b", AppOrdering.saveOrder(apps));
    }
}
