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

    /**
     * The pinned packages after one is added or removed.
     *
     * A new pin goes at the end: the list is the order the apps were pinned
     * in, not the reverse of it.
     */
    public static List<String> toggleFavourite(List<String> favourites, String packageName) {
        List<String> out = new ArrayList<>(names(favourites));
        if (!out.remove(packageName)) {
            out.add(packageName);
        }
        return out;
    }

    /**
     * Pure: whether arranging may move the app at {@code from} to {@code to}.
     *
     * The pinned apps always lead the grid, in the order they were pinned, so
     * a pinned app moves among the pins and any other app among the rest. A
     * move across that edge would only snap back at the next rebuild, so it
     * is refused like the edge of the grid.
     *
     * @param pinned how many apps at the start of the grid are pinned
     */
    public static boolean canMove(int from, int to, int pinned) {
        return from >= 0 && to >= 0 && (from < pinned) == (to < pinned);
    }

    /** How many apps at the start of an ordered grid are pinned. */
    public static int pinnedCount(List<AppEntry> ordered, List<String> favourites) {
        Set<String> pins = new HashSet<>(names(favourites));
        int count = 0;
        while (count < ordered.size() && ordered.get(count) != null
                && pins.contains(ordered.get(count).packageName)) {
            count++;
        }
        return count;
    }

    /** The pin order an arranged grid now shows: its first {@code pinned} apps. */
    public static List<String> pinOrder(List<AppEntry> ordered, int pinned) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < pinned && i < ordered.size(); i++) {
            out.add(ordered.get(i).packageName);
        }
        return out;
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
