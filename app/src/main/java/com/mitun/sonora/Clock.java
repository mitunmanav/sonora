package com.mitun.sonora;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The one way Sonora writes the time, so the shelf pill, Control Center and
 * the screen saver always agree.
 */
public final class Clock {

    private Clock() {
    }

    /** The time as the prefs ask for it: 24-hour or not, seconds or not. */
    public static String time(Prefs prefs) {
        return time(prefs.clock24(), prefs.showSeconds());
    }

    public static String time(boolean h24, boolean seconds) {
        String pattern = h24
                ? (seconds ? "HH:mm:ss" : "HH:mm")
                : (seconds ? "h:mm:ss a" : "h:mm a");
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date());
    }

    public static String date() {
        return new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(new Date());
    }

    /** Date and time on one line, the date only when the user wants it. */
    public static String line(Prefs prefs) {
        String time = time(prefs);
        return prefs.showDate() ? date() + "  " + time : time;
    }
    /** Milliseconds until the next tick a seconds/minute clock should re-draw at. */
    public static long tickDelay(boolean showSeconds, long nowMs) {
        return showSeconds ? 1000 - nowMs % 1000 : 60000 - nowMs % 60000;
    }
}
