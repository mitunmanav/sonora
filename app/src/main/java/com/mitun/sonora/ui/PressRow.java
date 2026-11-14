package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Rect;
import android.widget.LinearLayout;

/**
 * A list row that grows when it takes the focus and yields under OK.
 *
 * Both are the same spring, so a press that lands while the row is still
 * growing carries on from where it is. {@link android.view.View} already
 * calls setPressed on key-down and clears it on key-up or when the focus
 * leaves, so a half press cannot leave the row shrunk.
 */
public final class PressRow extends LinearLayout {
    /** How far the focused row sinks under the OK button. */
    private static final float PRESS = 0.96f;

    private final float focusScale;

    public PressRow(Context context, float focusScale) {
        super(context);
        this.focusScale = focusScale;
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        Motion.scale(this, focused ? focusScale : 1f,
                Motion.DAMPING_CRITICAL, Motion.RESPONSE_FOCUS);
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        Motion.press(this, pressed, isFocused() ? focusScale : 1f, PRESS);
    }
}
