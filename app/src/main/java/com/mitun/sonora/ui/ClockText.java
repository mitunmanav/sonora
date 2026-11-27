package com.mitun.sonora.ui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The clock's formatters, built once per pattern and language.
 *
 * The clock ticks every second with seconds on, and the screen saver draws
 * its clock every frame; a new formatter each time is garbage the 1 GB tier
 * pays for in collections. The UI thread is the only caller.
 */
public final class ClockText {
    private static final Map<String, SimpleDateFormat> formats = new HashMap<>();

    private ClockText() {
    }

    public static String format(String pattern, Date when) {
        Locale locale = Locale.getDefault();
        String key = locale + "|" + pattern;
        SimpleDateFormat format = formats.get(key);
        if (format == null) {
            format = new SimpleDateFormat(pattern, locale);
            formats.put(key, format);
        }
        // The zone can change while the formatter is kept.
        format.setTimeZone(java.util.TimeZone.getDefault());
        return format.format(when);
    }
}
