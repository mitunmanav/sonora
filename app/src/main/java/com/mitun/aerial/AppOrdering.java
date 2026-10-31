package com.mitun.aerial;

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

    public static List<AppEntry> order(List<AppEntry> apps, String savedOrder, Set<String> hidden) {
        Set<String> blocked = hidden == null ? Collections.emptySet() : new HashSet<>(hidden);
        List<AppEntry> sorted = new ArrayList<>();
        for (AppEntry entry : apps) {
            if (entry.settings || entry.packageName == null || blocked.contains(entry.packageName)) {
                continue;
            }
            sorted.add(entry);
        }
        final Collator collator = Collator.getInstance();
        Collections.sort(sorted, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return collator.compare(a.label, b.label);
            }
        });

        Map<String, AppEntry> byPackage = new HashMap<>();
        for (AppEntry entry : sorted) {
            byPackage.put(entry.packageName, entry);
        }

        List<AppEntry> result = new ArrayList<>();
        if (savedOrder != null) {
            for (String pkg : savedOrder.split("\\n")) {
                AppEntry entry = byPackage.remove(pkg.trim());
                if (entry != null) {
                    result.add(entry);
                }
            }
        }
        List<AppEntry> remaining = new ArrayList<>(byPackage.values());
        Collections.sort(remaining, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return collator.compare(a.label, b.label);
            }
        });
        result.addAll(remaining);
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
}
