package com.mitun.sonora.ui;

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

    private Type() {
    }

    public static void apply(TextView view, int style) {
        switch (style) {
            case DISPLAY:
                view.setTextSize(34);
                view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                view.setLetterSpacing(-0.02f);
                view.setLineSpacing(0f, 1.05f);
                break;
            case TITLE:
                view.setTextSize(30);
                view.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                view.setLetterSpacing(-0.015f);
                view.setLineSpacing(0f, 1.10f);
                break;
            case HEADING:
                view.setTextSize(19);
                view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                view.setLetterSpacing(0f);
                view.setLineSpacing(0f, 1.22f);
                break;
            case BODY:
                view.setTextSize(17);
                view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                view.setLetterSpacing(0f);
                view.setLineSpacing(0f, 1.34f);
                break;
            case CAPTION:
                view.setTextSize(13);
                view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                view.setLetterSpacing(0.01f);
                view.setLineSpacing(0f, 1.38f);
                break;
            case MICRO:
                view.setTextSize(12);
                view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                view.setLetterSpacing(0.12f);
                view.setLineSpacing(0f, 1.20f);
                break;
            default:
                break;
        }
    }
}
