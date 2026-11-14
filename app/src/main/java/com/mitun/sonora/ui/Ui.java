package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.FocusFinder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;


/** Drawing and platform helpers shared by the launcher and the screen saver. */
public final class Ui {
    public static final int INK = 0xFFF5F6F8;
    // The least prominent ink still has to be readable over the wallpaper, so
    // it is dimmer than the ink but not faint.
    public static final int INK_TERTIARY = 0xB3F5F6F8;
    /** A mark that sits on the glass while it is at rest. */
    public static final int INK_ON_GLASS = 0xE6F5F6F8;
    /** A mark that sits on the glass once it has caught the light. */
    public static final int INK_ON_LIT = 0xFF0A0D12;
    public static final int NIGHT = 0xFF05070B;

    private Ui() {
    }

    public static float dpf(Context context, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }

    public static int dp(Context context, float dp) {
        return Math.round(dpf(context, dp));
    }

    public static float spf(Context context, float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                context.getResources().getDisplayMetrics());
    }

    public static int alpha(int color, float factor) {
        int a = Math.round(Color.alpha(color) * Math.max(0f, Math.min(1f, factor)));
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    /**
     * Blends two colours. A mark that changes with the state of the surface
     * under it is one colour at a time, not one at a time and another after.
     */
    public static int mix(int from, int to, float amount) {
        float t = amount < 0f ? 0f : (amount > 1f ? 1f : amount);
        return Color.argb(
                Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t),
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t));
    }

    /**
     * The highlight on the row a list has selected.
     *
     * A list on a TV is driven by the D-pad, and the list itself holds the
     * focus, so the selection has to be drawn for it.
     */
    public static android.graphics.drawable.Drawable listSelector(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dpf(context, 12));
        // Light at the top, fading down: the same material as the rest of the
        // app, so the row reads as a surface and not as a flat rectangle.
        drawable.setColors(new int[]{0x26FFFFFF, 0x0CFFFFFF});
        drawable.setStroke(Math.max(1, Math.round(dpf(context, 1))), 0x33FFFFFF);
        return drawable;
    }

    public static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    public static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Moves the focus one row up or down inside a vertical list.
     *
     * The panels do this themselves instead of letting the framework search
     * for the next focusable view: a framework search would walk out of the
     * panel and land on the launcher grid behind it, where OK would launch an
     * app the user never picked.
     */
    public static boolean moveListFocus(LinearLayout list, boolean up) {
        if (list == null) {
            return false;
        }
        // getFocusedChild, not findFocus: findFocus asks the parent whether it
        // is focused, which it never is, and always answers no.
        View focused = list.getFocusedChild();
        int index = focused == null ? -1 : list.indexOfChild(focused);
        int count = list.getChildCount();
        for (int i = index + (up ? -1 : 1); i >= 0 && i < count; i += (up ? -1 : 1)) {
            View child = list.getChildAt(i);
            if (child.isFocusable() && child.getVisibility() == View.VISIBLE) {
                child.requestFocus();
                return true;
            }
        }
        return false;
    }

    /** Where a child is laid out inside a screen-sized ancestor, ignoring its scale. */
    /**
     * The framework may look for the next focus anywhere, but a modal surface
     * is the only place focus may go: the screen beneath still shows at the
     * edge, and landing on it would drive a covered screen. Finds the next
     * view inside {@code host}; when none is found in an escape direction the
     * surface is asked to close instead.
     */
    public static View focusSearchContained(ViewGroup host, View focused, int direction,
                                            Runnable onEscape, int... escapeDirections) {
        View next = FocusFinder.getInstance().findNextFocus(host, focused, direction);
        if (next != null) {
            return next;
        }
        // View.FOCUS_* are not bit flags, so escapes compare one at a time.
        for (int escape : escapeDirections) {
            if (direction == escape) {
                onEscape.run();
                return focused;
            }
        }
        return focused;
    }

    /** Where a view is laid out inside a host, ignoring its focus scale. */
    public static Rect regionIn(View host, View view) {
        int x = 0;
        int y = 0;
        View walk = view;
        while (walk != null && walk != host) {
            x += walk.getLeft();
            y += walk.getTop();
            walk = walk.getParent() instanceof View ? (View) walk.getParent() : null;
        }
        return new Rect(x, y, x + view.getWidth(), y + view.getHeight());
    }
}
