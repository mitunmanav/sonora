package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mitun.sonora.Prefs;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** The tvOS-style top-right time / profile pill. Here the profile opens Settings. */
public final class GlassPillView extends FrameLayout {
    private static final float FOCUS_SCALE = 1.055f;
    /** The lit pill yields under OK, then springs back. */
    private static final float PRESS_SCALE = 0.95f;
    private static final float TEXT_SP = 14f;

    private final TextView time;
    private final TextView date;
    private final GlassDrawable material;
    private final Prefs prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private float focus;
    private final Motion.FloatProperty focusValue = new Motion.FloatProperty() {
        @Override
        public float get() {
            return focus;
        }

        @Override
        public void set(float value) {
            focus = value;
            material.setFocus(value);
            // The clock turns dark as the glass lights up: white on a lit
            // lens would be white on white.
            time.setTextColor(Ui.mix(Ui.INK, Ui.INK_ON_LIT, value));
            date.setTextColor(Ui.mix(Ui.INK, Ui.INK_ON_LIT, value));
        }
    };
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            long now = System.currentTimeMillis();
            // On the next change of the display, not on a poll.
            handler.postDelayed(this, prefs.showSeconds()
                    ? 1000 - now % 1000 : 60000 - now % 60000);
        }
    };

    public GlassPillView(Context context, Prefs prefs) {
        super(context);
        this.prefs = prefs;
        setFocusable(true);
        setClickable(true);
        // Measured from tvOS 26: a single line, 5.3% of the screen tall.
        material = new GlassDrawable(context, 15f, 1f);
        setBackground(material);
        // A readout: time on glass, with no button in it that does nothing.
        setMinimumHeight(Ui.dp(context, 28));
        setPadding(Ui.dp(context, 13), 0, Ui.dp(context, 13), 0);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.HORIZONTAL);
        text.setGravity(Gravity.CENTER_VERTICAL);
        // The date, when it is on, shares the line: tvOS's pill is one line.
        date = new TextView(context);
        date.setTextColor(Ui.INK);
        Type.apply(date, Type.BODY);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP);
        date.setIncludeFontPadding(false);
        date.setPadding(0, 0, Ui.dp(context, 8), 0);
        text.addView(date);
        time = new TextView(context);
        time.setTextColor(Ui.INK);
        Type.apply(time, Type.BODY);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP);
        time.setIncludeFontPadding(false);
        // Digits of one width, so the pill keeps its size as the minutes change
        // and its glass never has to be baked again.
        time.setFontFeatureSettings("tnum");
        text.addView(time);

        row.addView(text, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        addView(row, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        setContentDescription("Settings");

        setOnFocusChangeListener((v, hasFocus) -> {
            // A spring, so moving the focus on before the pill has settled
            // continues from where it is instead of starting over.
            Motion.scale(this, hasFocus ? FOCUS_SCALE : 1f, Motion.DAMPING_CRITICAL,
                    Motion.RESPONSE_FOCUS);
            // The glass catches the light, and the clock turns dark with it.
            Motion.springCustom(this, 0, hasFocus ? 1f : 0f, Motion.DAMPING_CRITICAL,
                    Motion.RESPONSE_FOCUS, focusValue);
        });
        setOnClickListener(v -> {
            if (onOpenSettings != null) {
                onOpenSettings.run();
            }
        });
        refresh();
        updateDateVisibility();
    }

    private Runnable onOpenSettings;
    /** Shows the wallpaper through the pill: see {@link GlassLens}. */
    public void setBackdrop(Bitmap lens) {
        material.setBackdrop(lens, 0x38000000);
    }

    /** Frosts the pill with its share of the blurred wallpaper instead. */
    public void setFrost(Bitmap picture, int screenWidth, int screenHeight, float zoom,
                         android.graphics.Rect region) {
        material.setFrost(picture, screenWidth, screenHeight, zoom, region, 0x38000000);
    }

    public void setOnOpenSettings(Runnable runnable) {
        onOpenSettings = runnable;
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        float base = isFocused() ? FOCUS_SCALE : 1f;
        Motion.scale(this, pressed ? base * PRESS_SCALE : base,
                Motion.DAMPING_CRITICAL, Motion.RESPONSE_PRESS);
    }

    /** What the pill says, for Control Center to repeat while it covers the pill. */
    public String clockText() {
        return prefs.showDate() ? date.getText() + "  " + time.getText()
                : time.getText().toString();
    }

    public void updateDateVisibility() {
        date.setVisibility(prefs.showDate() ? VISIBLE : GONE);
    }

    public void refresh() {
        boolean h24 = prefs.clock24();
        boolean seconds = prefs.showSeconds();
        String pattern = h24
                ? (seconds ? "HH:mm:ss" : "HH:mm")
                : (seconds ? "h:mm:ss a" : "h:mm a");
        time.setText(new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date()));
        date.setText(new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(new Date()));
        updateDateVisibility();
        // Screen readers should hear the clock and what opening it does, not
        // just the word "Settings".
        String when = time.getText().toString();
        if (prefs.showDate()) {
            when += ", " + date.getText();
        }
        setContentDescription(when + ". Settings");
    }

    /** Nothing ticks while an app is on screen over the launcher. */
    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        handler.removeCallbacks(tick);
        if (visibility == VISIBLE) {
            handler.post(tick);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        Motion.cancel(this);
        super.onDetachedFromWindow();
    }
}
