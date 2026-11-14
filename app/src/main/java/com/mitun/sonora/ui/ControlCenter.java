package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mitun.sonora.Clock;
import com.mitun.sonora.Prefs;

import java.util.ArrayList;
import java.util.List;

/**
 * Control Center, laid out from tvOS 26: one large tile, four pills and a row of
 * round buttons in the top-right corner, with the home screen dimmed behind.
 *
 * Every tile does something this TV can really do. tvOS's Power Off and Sleep
 * Timer are not here because a launcher has no permission to do either.
 */
public final class ControlCenter extends FrameLayout {
    public static final int SAVER = 0;
    public static final int WIFI = 1;
    public static final int BLUETOOTH = 2;
    public static final int SOUND = 3;
    public static final int DISPLAY = 4;
    public static final int SEARCH = 5;
    public static final int APPS = 6;
    public static final int QUICK = 7;
    public static final int SETTINGS = 8;

    public interface Listener {
        void onPick(int action);

        void onDismiss();
    }

    private static final float FOCUS_SCALE = 1.06f;
    /** The small round buttons grow more: 95 to 106 pixels on tvOS. */
    private static final float ROUND_FOCUS_SCALE = 1.12f;
    private static final float PRESS_SCALE = 0.95f;
    /** The Wi-Fi mark when it is connected. */
    private static final int WIFI_ON = 0xFF2F9BFF;

    private final Listener listener;
    private final LinearLayout panel;
    private final View first;
    private final TextView time;
    private final List<Tile> tiles = new ArrayList<>();
    /** The blurred wallpaper the glass frosts, or null for dark glass. */
    private final Bitmap frost;
    private final Prefs prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Once a minute while the panel is open, so the clock row stays true. */
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            time.setText(clockText());
            long now = System.currentTimeMillis();
            handler.postDelayed(this, prefs.showSeconds()
                    ? 1000 - now % 1000 : 60000 - now % 60000);
        }
    };
    private boolean glassBaked;

    public ControlCenter(Context context, String wifiName, Bitmap frost,
                         Listener listener) {
        super(context);
        this.listener = listener;
        this.frost = frost;
        prefs = new Prefs(context);
        setBackgroundColor(Theme.dim());
        setAlpha(0f);
        setClipChildren(false);

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        LayoutParams panelParams = new LayoutParams(dp(215), LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        panelParams.topMargin = dp(26);
        panelParams.setMarginEnd(dp(35));
        addView(panel, panelParams);

        time = new TextView(context);
        time.setText(clockText());
        time.setTextColor(Theme.inkSecondary());
        Type.apply(time, Type.CAPTION);
        time.setIncludeFontPadding(false);
        time.setGravity(Gravity.CENTER_VERTICAL);
        time.setPadding(dp(2), 0, 0, 0);
        panel.addView(time, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(28)));

        // The large tile, with two pills stacked beside it.
        LinearLayout top = row(context);
        top.setPadding(0, dp(12), 0, 0);
        Tile saver = new Tile(context, SAVER, Glyph.SAVER, "Screen Saver", null, Tile.LARGE);
        top.addView(saver, sized(dp(103), dp(103), 0));
        LinearLayout side = new LinearLayout(context);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setClipChildren(false);
        side.addView(new Tile(context, WIFI, Glyph.WIFI, "Wi-Fi", wifiName, Tile.PILL),
                new LinearLayout.LayoutParams(dp(103), dp(47)));
        LinearLayout.LayoutParams lowerParams = new LinearLayout.LayoutParams(dp(103), dp(47));
        lowerParams.topMargin = dp(9);
        side.addView(new Tile(context, BLUETOOTH, Glyph.BLUETOOTH, "Bluetooth", null, Tile.PILL),
                lowerParams);
        LinearLayout.LayoutParams sideParams = sized(dp(103), LayoutParams.WRAP_CONTENT, dp(9));
        top.addView(side, sideParams);
        panel.addView(top);

        LinearLayout middle = row(context);
        middle.setPadding(0, dp(9), 0, 0);
        middle.addView(new Tile(context, SOUND, Glyph.SOUND, "Sound", null, Tile.PILL),
                sized(dp(103), dp(47), 0));
        middle.addView(new Tile(context, DISPLAY, Glyph.DISPLAY, "Display", null, Tile.PILL),
                sized(dp(103), dp(47), dp(9)));
        panel.addView(middle);

        LinearLayout bottom = row(context);
        bottom.setPadding(0, dp(9), 0, 0);
        bottom.addView(new Tile(context, SEARCH, Glyph.SEARCH, "Search", null, Tile.ROUND),
                sized(dp(47), dp(47), 0));
        bottom.addView(new Tile(context, APPS, Glyph.APPS, "Apps", null, Tile.ROUND),
                sized(dp(47), dp(47), dp(9)));
        bottom.addView(new Tile(context, QUICK, Glyph.SLIDERS, "Quick settings", null, Tile.ROUND),
                sized(dp(47), dp(47), dp(9)));
        bottom.addView(new Tile(context, SETTINGS, Glyph.GEAR, "Settings", null, Tile.ROUND),
                sized(dp(47), dp(47), dp(9)));
        panel.addView(bottom);

        first = saver;
    }

    /**
     * Frosts each tile with the part of the wallpaper behind it. This has to
     * wait for the layout, because the slice depends on where the tile landed.
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (glassBaked || frost == null || frost.isRecycled() || getWidth() == 0) {
            return;
        }
        glassBaked = true;
        for (Tile tile : tiles) {
            tile.bakeGlass();
        }
    }

    private String clockText() {
        return Clock.line(prefs);
    }

    /** Brings the panel in and puts the focus on the large tile. */
    public void open() {
        Motion.spring(this, Motion.ALPHA, 1f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_FOCUS);
        panel.setTranslationY(-dp(14));
        Motion.spring(panel, Motion.TRANSLATION_Y, 0f, Motion.DAMPING_CRITICAL,
                Motion.RESPONSE_PANEL);
        time.setText(clockText());
        handler.post(clockTick);
        first.requestFocus();
    }

    /** Takes the panel away, and runs the work once it has gone. */
    public void close(Runnable onGone) {
        Motion.spring(panel, Motion.TRANSLATION_Y, -dp(14), Motion.DAMPING_CRITICAL,
                Motion.RESPONSE_PANEL);
        Motion.spring(this, Motion.ALPHA, 0f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_FOCUS,
                onGone);
    }

    /** DOWN from the last row leaves the panel. */
    @Override
    public View focusSearch(View focused, int direction) {
        return Ui.focusSearchContained(this, focused, direction, View.FOCUS_DOWN,
                listener::onDismiss);
    }

    @Override
    protected void onDetachedFromWindow() {
        // The clock stops ticking the moment the panel is off screen.
        handler.removeCallbacks(clockTick);
        super.onDetachedFromWindow();
    }

    private LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        return row;
    }

    private static LinearLayout.LayoutParams sized(int width, int height, int startMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMarginStart(startMargin);
        return params;
    }

    private int dp(float value) {
        return Ui.dp(getContext(), value);
    }

    /** One control: dark glass at rest, white under the focus. */
    private final class Tile extends FrameLayout {
        static final int LARGE = 0;
        static final int PILL = 1;
        static final int ROUND = 2;

        private final GlassDrawable material;
        private final Glyph glyph;
        private final TextView label;
        private final TextView detail;
        private final int restInk;
        private final float focusScale;
        /** One property per tile, so a focus change allocates nothing. */
        private final Motion.FocusValue focusValue = new Motion.FocusValue(value -> {
            Tile.this.material.setFocus(value);
            setInk(value);
        });

        Tile(Context context, int action, int mark, String title, String subtitle, int shape) {
            super(context);
            setFocusable(true);
            setClickable(true);
            setContentDescription(subtitle == null ? title : title + ", " + subtitle);
            tiles.add(this);
            focusScale = shape == ROUND ? ROUND_FOCUS_SCALE : FOCUS_SCALE;
            restInk = action == WIFI && "Connected".equals(subtitle) ? WIFI_ON : Theme.ink();
            material = shape == ROUND ? GlassDrawable.circle(context, 0.9f)
                    : new GlassDrawable(context, shape == LARGE ? 25f : 23.5f, 0.9f);
            setBackground(material);
            glyph = new Glyph(mark);
            ImageView image = new ImageView(context);
            image.setImageDrawable(glyph);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);

            if (shape == ROUND) {
                addView(image, new LayoutParams(dp(23), dp(23), Gravity.CENTER));
                label = null;
                detail = null;
            } else if (shape == LARGE) {
                LayoutParams imageParams = new LayoutParams(dp(47), dp(47),
                        Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                imageParams.topMargin = dp(20);
                addView(image, imageParams);
                label = text(context, title, Type.CAPTION);
                LayoutParams labelParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                labelParams.bottomMargin = dp(10);
                addView(label, labelParams);
                detail = null;
            } else {
                LayoutParams imageParams = new LayoutParams(dp(22), dp(22),
                        Gravity.START | Gravity.CENTER_VERTICAL);
                imageParams.setMarginStart(dp(13));
                addView(image, imageParams);
                LinearLayout lines = new LinearLayout(context);
                lines.setOrientation(LinearLayout.VERTICAL);
                label = text(context, title, Type.CAPTION);
                label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f);
                label.setLetterSpacing(0f);
                lines.addView(label);
                if (subtitle != null && subtitle.length() > 0) {
                    detail = text(context, subtitle, Type.MICRO);
                    detail.setLetterSpacing(0f);
                    detail.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f);
                    lines.addView(detail);
                } else {
                    detail = null;
                }
                LayoutParams linesParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL);
                linesParams.setMarginStart(dp(42));
                linesParams.setMarginEnd(dp(2));
                addView(lines, linesParams);
            }
            setInk(0f);

            setOnFocusChangeListener((v, hasFocus) -> {
                Motion.scale(this, hasFocus ? focusScale : 1f, Motion.DAMPING_CRITICAL,
                        Motion.RESPONSE_FOCUS);
                setElevation(Ui.dpf(context, hasFocus ? 12f : 0f));
                Motion.springCustom(this, 0, hasFocus ? 1f : 0f, Motion.DAMPING_CRITICAL,
                        Motion.RESPONSE_FOCUS, focusValue);
            });
            setOnClickListener(v -> listener.onPick(action));
        }

        /** Frost is one shared texture, so opening this panel makes no bitmaps. */
        void bakeGlass() {
            material.setFrost(frost, ControlCenter.this.getWidth(),
                    ControlCenter.this.getHeight(), HeroBackgroundView.ZOOM, Ui.regionIn(ControlCenter.this, this),
                    Theme.glassVeil());
        }

        private void setInk(float value) {
            int ink = Ui.mix(Theme.ink(), Ui.INK_ON_LIT, value);
            glyph.setInk(Ui.mix(restInk, Ui.INK_ON_LIT, value));
            if (label != null) {
                label.setTextColor(ink);
            }
            if (detail != null) {
                detail.setTextColor(Ui.mix(Theme.inkSecondary(), 0xB30A0D12, value));
            }
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

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            Motion.press(this, pressed, isFocused() ? focusScale : 1f, PRESS_SCALE);
        }
    }
}
