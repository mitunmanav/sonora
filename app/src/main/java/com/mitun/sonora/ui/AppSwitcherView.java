package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mitun.sonora.AppEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * The app switcher, laid out from tvOS 26: a row of glass cards with the app
 * icons, the focused one lifted and lit. OK switches, BACK leaves, a long OK
 * closes the app.
 */
public final class AppSwitcherView extends FrameLayout {
    public interface Listener {
        void onPick(AppEntry entry);

        void onCloseApp(String packageName);

        void onDismiss();
    }

    private static final float FOCUS_SCALE = 1.07f;
    private static final float PRESS_SCALE = 0.95f;
    private static final int CARD_W = 150;
    private static final int CARD_H = 186;
    private static final int CARD_GAP = -20;

    private final Listener listener;
    private final LinearLayout row;
    private final List<Card> cards = new ArrayList<>();
    /** The blurred wallpaper the glass frosts, or null for dark glass. */
    private final Bitmap frost;
    private boolean glassBaked;

    public AppSwitcherView(Context context, List<AppEntry> apps, Bitmap frost,
                           Listener listener) {
        super(context);
        this.listener = listener;
        this.frost = frost;
        setBackgroundColor(Theme.dim());
        setAlpha(0f);
        setClipChildren(false);

        if (apps.isEmpty()) {
            LinearLayout empty = new LinearLayout(context);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            TextView title = text(context, "No recent apps", Type.CAPTION);
            title.setTextColor(Theme.ink());
            TextView hint = text(context, "Apps you open show up here", Type.MICRO);
            hint.setTextColor(Theme.inkSecondary());
            empty.addView(title);
            empty.addView(hint);
            addView(empty, new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            // With no card to hold the focus, the overlay takes it itself, so
            // UP or DOWN still lands on the dismiss path in focusSearch.
            setFocusable(true);
            row = null;
            return;
        }

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        // Only cards that fit on the screen exist. A card fully past the edge
        // would still take the focus and launch an app the user cannot see.
        int cardW = dp(CARD_W);
        int screenW = context.getResources().getDisplayMetrics().widthPixels;
        int fit = Math.max(1, (screenW - cardW) / (cardW + dp(CARD_GAP)) + 1);
        List<AppEntry> shown = apps.subList(0, Math.min(apps.size(), fit));
        for (int i = 0; i < shown.size(); i++) {
            row.addView(new Card(context, shown.get(i)), cardParams(i));
        }
        addView(row, new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    /**
     * Frosts each card with the part of the wallpaper behind it. This has to
     * wait for the layout, because the slice depends on where the card landed.
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (glassBaked || frost == null || frost.isRecycled() || getWidth() == 0 || row == null) {
            return;
        }
        glassBaked = true;
        for (Card card : cards) {
            card.bakeGlass();
        }
    }

    /** Brings the cards in, one after another, and puts the focus on the first. */
    public void open() {
        Motion.spring(this, Motion.ALPHA, 1f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_FOCUS);
        if (row == null) {
            requestFocus();
            return;
        }
        row.setTranslationY(dp(14));
        Motion.spring(row, Motion.TRANSLATION_Y, 0f, Motion.DAMPING_CRITICAL,
                Motion.RESPONSE_PANEL);
        for (int i = 0; i < cards.size(); i++) {
            final Card card = cards.get(i);
            card.setAlpha(0f);
            card.setScaleX(0.92f);
            card.setScaleY(0.92f);
            card.postDelayed(() -> {
                if (!card.isAttachedToWindow()) {
                    return;
                }
                Motion.spring(card, Motion.ALPHA, 1f, Motion.DAMPING_CRITICAL,
                        Motion.RESPONSE_FOCUS);
                Motion.scale(card, 1f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_FOCUS);
            }, 40L * i);
        }
        cards.get(0).requestFocus();
    }

    /** Takes the cards away, and runs the work once they have gone. */
    public void close(Runnable onGone) {
        Motion.spring(this, Motion.ALPHA, 0f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_FOCUS,
                onGone);
    }

    /** UP or DOWN leaves the row. */
    @Override
    public View focusSearch(View focused, int direction) {
        return Ui.focusSearchContained(this, focused, direction,
                View.FOCUS_UP | View.FOCUS_DOWN, listener::onDismiss);
    }

    private LinearLayout.LayoutParams cardParams(int index) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(CARD_W), dp(CARD_H));
        params.setMarginStart(index == 0 ? 0 : dp(CARD_GAP));
        return params;
    }

    private int dp(float value) {
        return Ui.dp(getContext(), value);
    }

    private TextView text(Context context, String value, int style) {
        TextView view = new TextView(context);
        view.setText(value);
        Type.apply(view, style);
        view.setSingleLine(true);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.setIncludeFontPadding(false);
        return view;
    }

    /** One app: dark glass at rest, white under the focus. */
    private final class Card extends FrameLayout {
        private final GlassDrawable material;
        private final AppEntry entry;
        private final TextView label;
        /** One property per card, so a focus change allocates nothing. */
        private final Motion.FocusValue focusValue = new Motion.FocusValue(value -> {
            Card.this.material.setFocus(value);
            Card.this.label.setTextColor(Ui.mix(Theme.ink(), Ui.INK_ON_LIT, value));
        });

        Card(Context context, AppEntry app) {
            super(context);
            entry = app;
            setFocusable(true);
            setClickable(true);
            setLongClickable(true);
            setContentDescription(app.label);
            cards.add(this);
            material = new GlassDrawable(context, 26f, 0.9f);
            setBackground(material);

            ImageView icon = new ImageView(context);
            if (app.icon != null && !app.icon.isRecycled()) {
                icon.setImageBitmap(app.icon);
            } else {
                // An app that gave no icon still gets a card, in its colour.
                icon.setImageDrawable(Ui.rounded(Bitmaps.tileColor(app.accent),
                        Ui.dpf(context, 12f)));
            }
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LayoutParams iconParams = new LayoutParams(dp(52), dp(52),
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            iconParams.topMargin = dp(30);
            addView(icon, iconParams);

            label = text(context, app.label, Type.CAPTION);
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
            label.setGravity(Gravity.CENTER);
            LayoutParams labelParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            labelParams.bottomMargin = dp(14);
            labelParams.setMarginStart(dp(8));
            labelParams.setMarginEnd(dp(8));
            addView(label, labelParams);
            setInk(0f);

            setOnFocusChangeListener((v, hasFocus) -> {
                Motion.scale(this, hasFocus ? FOCUS_SCALE : 1f, Motion.DAMPING_CRITICAL,
                        Motion.RESPONSE_FOCUS);
                setElevation(Ui.dpf(context, hasFocus ? 14f : 0f));
                Motion.springCustom(this, 0, hasFocus ? 1f : 0f, Motion.DAMPING_CRITICAL,
                        Motion.RESPONSE_FOCUS, focusValue);
            });
            setOnClickListener(v -> listener.onPick(entry));
            setOnLongClickListener(v -> {
                listener.onCloseApp(entry.packageName);
                // The card that closed may be the focused one; the focus
                // moves to its neighbour first so it is not left nowhere.
                int index = cards.indexOf(this);
                Card next = index >= 0 && index + 1 < cards.size() ? cards.get(index + 1)
                        : index > 0 ? cards.get(index - 1) : null;
                if (next != null && next != this) {
                    next.requestFocus();
                }
                Motion.spring(this, Motion.ALPHA, 0f, Motion.DAMPING_CRITICAL,
                        Motion.RESPONSE_FOCUS, () -> {
                            row.removeView(this);
                            cards.remove(this);
                            if (cards.isEmpty()) {
                                listener.onDismiss();
                            }
                        });
                return true;
            });
        }

        /** Frost is one shared texture, so opening this panel makes no bitmaps. */
        void bakeGlass() {
            material.setFrost(frost, AppSwitcherView.this.getWidth(),
                    AppSwitcherView.this.getHeight(), HeroBackgroundView.ZOOM, Ui.regionIn(AppSwitcherView.this, this),
                    Theme.glassVeil());
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            Motion.press(this, pressed, isFocused() ? FOCUS_SCALE : 1f, PRESS_SCALE);
        }

        private void setInk(float value) {
            label.setTextColor(Ui.mix(Theme.ink(), Ui.INK_ON_LIT, value));
        }
    }
}
