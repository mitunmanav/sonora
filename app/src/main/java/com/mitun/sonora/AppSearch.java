package com.mitun.sonora;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finds the apps that match what the user typed.
 *
 * Pure, so it can be tested without a device. The search is local: it never
 * asks the network what an app is called.
 */
public final class AppSearch {
    private AppSearch() {
    }

    /**
     * The apps that match the query, best match first.
     *
     * Four passes, in the order a person would guess them: the name starts
     * with what was typed, the initials of the name do ("yt" finds YouTube),
     * a word in the name starts with it, or the name contains it.
     *
     * An empty query returns everything: the search screen is also the
     * quickest way to see everything there is.
     */
    public static List<AppEntry> filter(List<AppEntry> apps, String query) {
        List<AppEntry> out = new ArrayList<>();
        if (apps == null) {
            return out;
        }
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            out.addAll(apps);
            return out;
        }

        List<AppEntry> initials = new ArrayList<>();
        List<AppEntry> words = new ArrayList<>();
        List<AppEntry> contains = new ArrayList<>();
        for (AppEntry entry : apps) {
            if (entry == null || entry.label == null) {
                continue;
            }
            String label = entry.label.toLowerCase(Locale.ROOT);
            if (label.startsWith(needle)) {
                out.add(entry);
            } else if (initials(entry.label).startsWith(needle)) {
                initials.add(entry);
            } else if (wordStartsWith(label, needle)) {
                words.add(entry);
            } else if (label.contains(needle)) {
                contains.add(entry);
            }
        }
        out.addAll(initials);
        out.addAll(words);
        out.addAll(contains);
        return out;
    }

    /**
     * Where an app sits in the results.
     *
     * The card that had the focus keeps it when the app it is showing is
     * still in the list, so a change of results does not throw the focus
     * away and leave the user with a remote that goes nowhere.
     *
     * @return the index of the app, or -1 when it is not in the list
     */
    public static int focusedIndex(String packageName, List<AppEntry> matches) {
        if (packageName == null || matches == null) {
            return -1;
        }
        for (int i = 0; i < matches.size(); i++) {
            AppEntry entry = matches.get(i);
            if (entry != null && packageName.equals(entry.packageName)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean wordStartsWith(String label, String needle) {
        int index = label.indexOf(needle);
        while (index > 0) {
            char before = label.charAt(index - 1);
            if (before == ' ' || before == '-' || before == '(' || before == '&' || before == '.') {
                return true;
            }
            index = label.indexOf(needle, index + 1);
        }
        return false;
    }

    /**
     * The initials of a name: "YouTube" gives "yt", "Google Play" gives "gp".
     *
     * A capital letter in the middle of a word starts a new one, which is how
     * people write the names of apps.
     */
    private static String initials(String label) {
        StringBuilder sb = new StringBuilder();
        boolean start = true;
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                start = true;
                continue;
            }
            if (start) {
                sb.append(c);
                start = false;
            } else if (Character.isUpperCase(c) && Character.isLetter(label.charAt(i - 1))) {
                sb.append(c);
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
