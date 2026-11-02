package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mitun.sonora.AppEntry;
import com.mitun.sonora.Prefs;

/** A focusable app tile: the wide icon plus an optional name. */
public final class AppTileView extends LinearLayout {
    public interface FocusListener {
        void onTileFocused(AppTileView tile, AppEntry entry);
    }

    /** How far the card leans, in degrees, per step of travel. */
    private static final float LEAN_X = 2.8f;
    private static final float LEAN_Y = 2.4f;

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

        label = new TextView(context);
        label.setTextColor(Color.WHITE);
        Type.apply(label, Type.CAPTION);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setAlpha(0.82f);
        label.setMinHeight(labelSpace);
        LayoutParams labelParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                labelSpace > 0 ? LayoutParams.WRAP_CONTENT : 0);
        labelParams.topMargin = Ui.dp(context, 5);
        addView(label, labelParams);
        // A label only shows for the focused tile in that mode, so the ones
        // that are not focused have to start hidden rather than fade out
        // after the tile has been focused once.
        if (labelMode == Prefs.LABELS_FOCUSED) {
            label.setVisibility(INVISIBLE);
            label.setAlpha(0f);
        } else if (labelMode == Prefs.LABELS_OFF) {
            label.setVisibility(GONE);
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
        label.setText(entry.label);
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
            Motion.scale(icon, 1.10f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_FOCUS);
            if (labelSpace && labelMode != Prefs.LABELS_OFF) {
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
            if (labelSpace) {
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
     * Leans the card in the direction of travel, then lets it settle back.
     *
     * The lean is a spring, so moving on before it has settled continues from
     * where the card is instead of restarting the movement.
     */
    public void lean(float directionX, float directionY) {
        if (directionX == 0f && directionY == 0f) {
            return;
        }
        if (Motion.isReduced(getContext())) {
            // Reduced motion keeps the feedback but drops the travel: the
            // card does not swing across the screen.
            return;
        }
        icon.setCameraDistance(Ui.dpf(getContext(), 1800f));
        Motion.nudge(icon, Motion.ROTATION_Y, directionX * LEAN_X);
        Motion.nudge(icon, Motion.ROTATION_X, -directionY * LEAN_Y);
        Motion.spring(icon, Motion.ROTATION_Y, 0f, Motion.DAMPING_CRITICAL,
                Motion.RESPONSE_FOCUS);
        Motion.spring(icon, Motion.ROTATION_X, 0f, Motion.DAMPING_CRITICAL,
                Motion.RESPONSE_FOCUS);
        // The artwork inside the card shifts with it, which is what gives the
        // card its depth.
        float dp = Ui.dpf(getContext(), 3f);
        icon.nudgeParallax(directionX * dp, directionY * dp);
    }

    @Override
    protected void onDetachedFromWindow() {
        Motion.cancel(this);
        Motion.cancel(icon);
        Motion.cancel(label);
        super.onDetachedFromWindow();
    }
}
