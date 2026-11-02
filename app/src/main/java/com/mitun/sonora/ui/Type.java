package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.widget.TextView;

/**
 * The type scale.
 *
 * Tracking and leading follow the size rather than being one value for
 * everything: large text is tracked tighter and led tighter because big
 * letters read too far apart at display sizes, while small text is tracked
 * slightly open and led looser because it needs the air to stay legible.
 *
 * Sizes are in SP, so the whole scale follows the user's font size setting.
 */
public final class Type {
    /** Screen titles. */
    public static final int DISPLAY = 0;
    /** The app name over the artwork. */
    public static final int TITLE = 1;
    /** A row label in a list. */
    public static final int HEADING = 2;
    /** A value in a list. */
    public static final int BODY = 3;
    /** Secondary text under a label. */
    public static final int CAPTION = 4;
    /** Small upper-case section headers. */
    public static final int MICRO = 5;

    /** The size of each style, in SP. */
    private static final float[] SIZES = {34f, 34f, 19f, 17f, 13f, 12f};
    /** The tracking of each style: tighter the larger the type. */
    private static final float[] TRACKING = {-0.02f, -0.02f, 0f, 0f, 0.01f, 0.12f};
    /** The leading of each style: tighter the larger the type. */
    private static final float[] LEADING = {1.05f, 1.02f, 1.22f, 1.34f, 1.38f, 1.20f};
    /** The weight of each style. */
    private static final String[] FACES = {
            "sans-serif-medium", "sans-serif-medium", "sans-serif-medium",
            "sans-serif", "sans-serif", "sans-serif-medium"};

    private Type() {
    }

    /** Applies a style to a text view. */
    public static void apply(TextView view, int style) {
        view.setTextSize(SIZES[style]);
        view.setTypeface(Typeface.create(FACES[style], Typeface.NORMAL));
        view.setLetterSpacing(TRACKING[style]);
        view.setLineSpacing(0f, LEADING[style]);
    }

    /**
     * Applies a style to text drawn straight onto a canvas, where the size is
     * in pixels rather than scaled units.
     */
    public static void apply(Paint paint, int style, Context context) {
        paint.setTextSize(Ui.spf(context, SIZES[style]));
        paint.setTypeface(Typeface.create(FACES[style], Typeface.NORMAL));
        paint.setLetterSpacing(TRACKING[style]);
    }
}
