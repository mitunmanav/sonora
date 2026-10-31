package com.mitun.sonora;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic app ordering independent of Android, so it can be unit-tested. */
public final class AppOrdering {
    private AppOrdering() {
    }

    /**
     * The order the home screen shows the apps in.
     *
     * The apps the user pinned come first, in the order they pinned them.
     * Then the order they arranged themselves, or alphabetical. Nothing
     * moves on its own: a home screen that reorders itself while you use it
     * is a home screen you cannot learn.
     *
     * @param savedOrder the packages the user arranged, one per line
     * @param hidden     the packages that are not on the home screen
     * @param favourites the packages the user pinned, in the order they pinned them
     */
    public static List<AppEntry> order(List<AppEntry> apps, String savedOrder, Set<String> hidden,
                                       List<String> favourites) {
        Set<String> blocked = hidden == null ? Collections.emptySet() : new HashSet<>(hidden);
        final Collator collator = Collator.getInstance();
        List<AppEntry> sorted = new ArrayList<>();
        Map<String, AppEntry> byPackage = new HashMap<>();
        for (AppEntry entry : apps) {
            if (entry == null || entry.settings || entry.packageName == null
                    || blocked.contains(entry.packageName)
                    || byPackage.containsKey(entry.packageName)) {
                continue;
            }
            sorted.add(entry);
            byPackage.put(entry.packageName, entry);
        }
        Collections.sort(sorted, byLabel(collator));

        // The order the user saved, then everything that is left, alphabetically.
        List<AppEntry> arranged = new ArrayList<>();
        Set<String> used = new HashSet<>();
        if (savedOrder != null) {
            for (String pkg : savedOrder.split("\\n")) {
                String key = pkg.trim();
                AppEntry entry = byPackage.get(key);
                if (entry != null && used.add(key)) {
                    arranged.add(entry);
                }
            }
        }
        for (AppEntry entry : sorted) {
            if (used.add(entry.packageName)) {
                arranged.add(entry);
            }
        }

        List<AppEntry> result = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        for (String pkg : names(favourites)) {
            AppEntry entry = byPackage.get(pkg);
            if (entry != null && placed.add(pkg)) {
                result.add(entry);
            }
        }
        for (AppEntry entry : arranged) {
            if (placed.add(entry.packageName)) {
                result.add(entry);
            }
        }
        return result;
    }

    public static String saveOrder(List<AppEntry> apps) {
        StringBuilder sb = new StringBuilder();
        for (AppEntry entry : apps) {
            if (entry.settings || entry.packageName == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(entry.packageName);
        }
        return sb.toString();
    }

    private static Comparator<AppEntry> byLabel(final Collator collator) {
        return new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return collator.compare(label(a), label(b));
            }
        };
    }

    private static String label(AppEntry entry) {
        return entry.label == null ? "" : entry.label;
    }

    private static List<String> names(List<String> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
