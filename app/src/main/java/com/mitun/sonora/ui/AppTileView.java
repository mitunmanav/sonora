package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mitun.sonora.AppEntry;
import com.mitun.sonora.Prefs;

/** A focusable app tile: the wide icon plus an optional name. */
public final class AppTileView extends LinearLayout {
    public interface FocusListener {
        void onTileFocused(AppTileView tile, AppEntry entry);
    }

    public static final float FOCUS_SCALE = 1.22f;
    /** The focused card yields under OK, then springs back. */
    private static final float PRESS_SCALE = 0.95f;

    private final AppIconView icon;
    private final TextView label;
    private final int iconHeight;
    private final boolean labelSpace;
    private AppEntry entry;
    private FocusListener focusListener;
    private boolean focused;
    private int labelMode;

    public AppTileView(Context context, int iconHeight, int labelSpace, Prefs prefs) {
        super(context);
        this.iconHeight = iconHeight;
        this.labelSpace = labelSpace > 0;
        this.labelMode = prefs.labels();
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setFocusable(true);
        setFocusableInTouchMode(false);
        setClickable(true);
        setLongClickable(true);
        setClipChildren(false);
        setClipToPadding(false);

        icon = new AppIconView(context);
        LayoutParams iconParams = new LayoutParams(LayoutParams.MATCH_PARENT, iconHeight);
        addView(icon, iconParams);

        if (labelSpace <= 0) {
            // Names are off by default, and a view per app costs on a big list.
            label = null;
            setOnFocusChangeListener((v, hasFocus) -> {
                focused = hasFocus;
                updateFocus();
            });
            return;
        }
        label = new TextView(context);
        label.setTextColor(Color.WHITE);
        Type.apply(label, Type.CAPTION);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setAlpha(0.82f);
        label.setMinHeight(labelSpace);
        LayoutParams labelParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = Ui.dp(context, 5);
        addView(label, labelParams);
        // A label only shows for the focused tile in that mode, so the ones
        // that are not focused have to start hidden rather than fade out
        // after the tile has been focused once.
        if (labelMode == Prefs.LABELS_FOCUSED) {
            label.setVisibility(INVISIBLE);
            label.setAlpha(0f);
        } else {
            label.setVisibility(VISIBLE);
        }

        setOnFocusChangeListener((v, hasFocus) -> {
            focused = hasFocus;
            updateFocus();
        });
    }

    public AppEntry entry() {
        return entry;
    }

    /** True when the tile shows a name under the card. */
    public boolean labelSpace() {
        return labelSpace;
    }

    public void setEntry(AppEntry entry) {
        this.entry = entry;
        icon.setEntry(entry);
        if (label != null) {
            label.setText(entry.label);
        }
        setContentDescription(entry.label);
    }

    /** Marks the card as one the user pinned to the top of the home screen. */
    public void setFavourite(boolean favourite) {
        icon.setFavourite(favourite);
    }

    public void setFocusListener(FocusListener listener) {
        focusListener = listener;
    }

    private void updateFocus() {
        if (focused) {
            // Raise the tile with its z, not with bringToFront: bringing a view
            // to the front moves it to the end of its row, which breaks the
            // order the D-pad links were built from.
            setZ(Ui.dpf(getContext(), 8f));
            icon.setFocused(true);
            Motion.scale(icon, FOCUS_SCALE, Motion.DAMPING_CRITICAL, Motion.RESPONSE_FOCUS);
            if (label != null) {
                label.setVisibility(VISIBLE);
                Motion.spring(label, Motion.ALPHA, 1f, Motion.DAMPING_CRITICAL,
                        Motion.RESPONSE_FOCUS);
            }
            if (focusListener != null) {
                focusListener.onTileFocused(this, entry);
            }
        } else {
            icon.setFocused(false);
            Motion.scale(icon, 1f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_FOCUS);
            setZ(0f);
            if (label != null) {
                if (labelMode == Prefs.LABELS_FOCUSED) {
                    Motion.spring(label, Motion.ALPHA, 0f, Motion.DAMPING_CRITICAL,
                            Motion.RESPONSE_FOCUS, () -> label.setVisibility(INVISIBLE));
                } else if (labelMode == Prefs.LABELS_ALL) {
                    Motion.spring(label, Motion.ALPHA, 0.82f, Motion.DAMPING_CRITICAL,
                            Motion.RESPONSE_FOCUS);
                }
            }
        }
    }

    /**
     * The card yields while OK is held, then springs back when it is let go.
     * The framework clears the press when the focus leaves, so the card can
     * never be left shrunk.
     */
    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        float base = focused ? FOCUS_SCALE : 1f;
        Motion.scale(icon, pressed ? base * PRESS_SCALE : base,
                Motion.DAMPING_CRITICAL, Motion.RESPONSE_PRESS);
    }

    @Override
    protected void onDetachedFromWindow() {
        Motion.cancel(this);
        Motion.cancel(icon);
        if (label != null) {
            Motion.cancel(label);
        }
        super.onDetachedFromWindow();
    }
}
