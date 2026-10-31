package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
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
        label.setTextSize(13);
        label.setTypeface(Ui.regular());
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setAlpha(0.82f);
        LayoutParams labelParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                labelSpace > 0 ? labelSpace : 0);
        labelParams.topMargin = Ui.dp(context, 5);
        addView(label, labelParams);
        label.setVisibility(this.labelSpace ? VISIBLE : GONE);

        setOnFocusChangeListener((v, hasFocus) -> {
            focused = hasFocus;
            updateFocus();
        });
    }

    public AppEntry entry() {
        return entry;
    }

    public AppIconView iconView() {
        return icon;
    }

    public void setEntry(AppEntry entry) {
        this.entry = entry;
        icon.setEntry(entry);
        label.setText(entry.label);
        setContentDescription(entry.label);
    }

    public void setFocusListener(FocusListener listener) {
        focusListener = listener;
    }

    private void updateFocus() {
        if (focused) {
            bringToFront();
            setZ(Ui.dpf(getContext(), 8f));
            icon.setFocused(true);
            icon.animate().cancel();
            icon.animate().scaleX(1.10f).scaleY(1.10f)
                    .setDuration(300)
                    .setInterpolator(new OvershootInterpolator(1.1f))
                    .start();
            if (labelSpace && labelMode != Prefs.LABELS_OFF) {
                label.setVisibility(VISIBLE);
                label.animate().alpha(1f).setDuration(150).start();
            }
            if (focusListener != null) {
                focusListener.onTileFocused(this, entry);
            }
        } else {
            icon.setFocused(false);
            icon.animate().cancel();
            icon.animate().scaleX(1f).scaleY(1f)
                    .setDuration(180)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                    .start();
            setZ(0f);
            icon.setParallax(0f, 0f);
            if (labelSpace) {
                if (labelMode == Prefs.LABELS_FOCUSED) {
                    label.animate().alpha(0f).setDuration(120).withEndAction(
                            () -> label.setVisibility(INVISIBLE)).start();
                } else if (labelMode == Prefs.LABELS_ALL) {
                    label.animate().alpha(0.82f).setDuration(120).start();
                }
            }
        }
    }

    /** Nudge the artwork a little toward the direction of travel, then settle. */
    public void sway(float directionX, float directionY) {
        if (!focused) {
            return;
        }
        float dp = Ui.dpf(getContext(), 3f);
        icon.setParallax(directionX * dp, directionY * dp);
        icon.setCameraDistance(Ui.dpf(getContext(), 1800f));
        icon.setRotationX(-directionY * 2.4f);
        icon.setRotationY(directionX * 2.8f);
        icon.animate().rotationX(0f).rotationY(0f).setDuration(520)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2f))
                .start();
    }

    public void setLabelMode(int mode) {
        labelMode = mode;
        if (!labelSpace) {
            return;
        }
        if (mode == Prefs.LABELS_ALL) {
            label.setVisibility(VISIBLE);
            label.setAlpha(focused ? 1f : 0.82f);
        } else if (mode == Prefs.LABELS_FOCUSED) {
            label.setVisibility(focused ? VISIBLE : INVISIBLE);
            label.setAlpha(focused ? 1f : 0f);
        } else {
            label.setVisibility(GONE);
        }
    }
}
