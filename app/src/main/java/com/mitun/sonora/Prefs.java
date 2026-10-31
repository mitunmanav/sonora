package com.mitun.sonora;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** All user choices in one tiny place. */
public final class Prefs {
    private static final String FILE = "sonora";
    public static final int LABELS_OFF = 0;
    public static final int LABELS_FOCUSED = 1;
    public static final int LABELS_ALL = 2;

    private final SharedPreferences sp;
    private final Context appContext;

    public Prefs(Context context) {
        appContext = context.getApplicationContext();
        sp = appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public int wallpaper() {
        return sp.getInt("wallpaper", 0);
    }

    public String wallpaperUri() {
        return sp.getString("wallpaper_uri", null);
    }

    public void setWallpaper(int index, String uri) {
        sp.edit().putInt("wallpaper", index).putString("wallpaper_uri", uri).apply();
    }

    /** 0 means "fit the screen", which is what every resolution should do. */
    public int columns() {
        return Math.max(0, Math.min(Grid.MAX_COLUMNS, sp.getInt("columns", 0)));
    }

    public void setColumns(int columns) {
        sp.edit().putInt("columns", Math.max(0, Math.min(Grid.MAX_COLUMNS, columns))).apply();
    }

    public int labels() {
        return Math.max(0, Math.min(2, sp.getInt("labels", LABELS_FOCUSED)));
    }

    public void setLabels(int mode) {
        sp.edit().putInt("labels", mode).apply();
    }

    public boolean clock24() {
        return sp.getBoolean("clock24", android.text.format.DateFormat.is24HourFormat(appContext));
    }

    public void setClock24(boolean value) {
        sp.edit().putBoolean("clock24", value).apply();
    }

    public boolean showDate() {
        return sp.getBoolean("show_date", false);
    }

    public void setShowDate(boolean value) {
        sp.edit().putBoolean("show_date", value).apply();
    }

    public boolean showSeconds() {
        return sp.getBoolean("show_seconds", false);
    }

    public void setShowSeconds(boolean value) {
        sp.edit().putBoolean("show_seconds", value).apply();
    }

    public boolean reduceMotion() {
        return sp.getBoolean("reduce_motion", false);
    }

    public void setReduceMotion(boolean value) {
        sp.edit().putBoolean("reduce_motion", value).apply();
    }

    /** The browser is bundled but off until the user asks for it. */
    public boolean browserEnabled() {
        return sp.getBoolean("browser", false);
    }

    public void setBrowserEnabled(boolean value) {
        sp.edit().putBoolean("browser", value).apply();
    }

    /** The file manager and the LAN share are bundled but off by default. */
    public boolean filesEnabled() {
        return sp.getBoolean("files", false);
    }

    public void setFilesEnabled(boolean value) {
        sp.edit().putBoolean("files", value).apply();
    }

    public boolean browserAdBlock() {
        return sp.getBoolean("browser_adblock", true);
    }

    public void setBrowserAdBlock(boolean value) {
        sp.edit().putBoolean("browser_adblock", value).apply();
    }

    /** The bookmarked addresses, one per line. */
    public List<String> browserBookmarks() {
        String raw = sp.getString("browser_bookmarks", "");
        List<String> out = new ArrayList<>();
        if (raw != null) {
            for (String line : raw.split("\\n")) {
                String trimmed = line.trim();
                if (trimmed.length() > 0) {
                    out.add(trimmed);
                }
            }
        }
        return out;
    }

    public void addBrowserBookmark(String url) {
        List<String> bookmarks = browserBookmarks();
        if (!bookmarks.contains(url)) {
            bookmarks.add(0, url);
        }
        StringBuilder sb = new StringBuilder();
        for (String bookmark : bookmarks) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(bookmark);
        }
        sp.edit().putString("browser_bookmarks", sb.toString()).apply();
    }

    public int screensaverDelaySec() {
        return Math.max(0, sp.getInt("ss_delay", 300));
    }

    public void setScreensaverDelaySec(int value) {
        sp.edit().putInt("ss_delay", Math.max(0, value)).apply();
    }

    public int screensaverSceneSec() {
        return Math.max(15, sp.getInt("ss_scene", 60));
    }

    public void setScreensaverSceneSec(int value) {
        sp.edit().putInt("ss_scene", Math.max(15, value)).apply();
    }

    /** 0 = on input, 1 = always, 2 = never. */
    public int screensaverClock() {
        return Math.max(0, Math.min(2, sp.getInt("ss_clock", 0)));
    }

    public void setScreensaverClock(int value) {
        sp.edit().putInt("ss_clock", Math.max(0, Math.min(2, value))).apply();
    }

    public String order() {
        return sp.getString("order", "");
    }

    public void setOrder(String order) {
        sp.edit().putString("order", order == null ? "" : order).apply();
    }

    public Set<String> hidden() {
        Set<String> copy = sp.getStringSet("hidden", Collections.emptySet());
        return new HashSet<>(copy);
    }

    public void setHidden(Set<String> hidden) {
        sp.edit().putStringSet("hidden", new HashSet<>(hidden)).apply();
    }

    public boolean welcomeSeen() {
        return sp.getBoolean("welcome", false);
    }

    public void setWelcomeSeen(boolean value) {
        sp.edit().putBoolean("welcome", value).apply();
    }

    public void reset() {
        boolean welcome = welcomeSeen();
        sp.edit().clear().apply();
        sp.edit().putBoolean("welcome", welcome).apply();
    }
}
