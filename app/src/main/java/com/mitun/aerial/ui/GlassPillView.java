package com.mitun.aerial.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mitun.aerial.Prefs;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** The tvOS-style top-right time / profile pill. Here the profile opens Settings. */
public final class GlassPillView extends FrameLayout {
    private final TextView time;
    private final TextView date;
    private final Prefs prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, prefs.showSeconds() ? 1000 : 15000);
        }
    };

    public GlassPillView(Context context, Prefs prefs) {
        super(context);
        this.prefs = prefs;
        setFocusable(true);
        setClickable(true);
        setBackground(new GlassDrawable(context, 27f, 1.15f));
        setElevation(Ui.dpf(context, 8f));
        int padH = Ui.dp(context, 14);
        int padV = Ui.dp(context, 7);
        setPadding(padH, padV, padH, padV);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setGravity(Gravity.END);
        time = new TextView(context);
        time.setTextColor(Ui.INK);
        time.setTextSize(17);
        time.setTypeface(Ui.medium());
        time.setGravity(Gravity.END);
        time.setIncludeFontPadding(false);
        text.addView(time);
        date = new TextView(context);
        date.setTextColor(Ui.INK_TERTIARY);
        date.setTextSize(10);
        date.setTypeface(Ui.regular());
        date.setGravity(Gravity.END);
        date.setIncludeFontPadding(false);
        text.addView(date);

        row.addView(text, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        FrameLayout avatar = new FrameLayout(context);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(0xFFF1F3F6);
        avatar.setBackground(oval);
        ImageView gear = new ImageView(context);
        gear.setImageBitmap(Ui.gear(context, Ui.dp(context, 64), false));
        gear.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int g = Ui.dp(context, 17);
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(g, g, Gravity.CENTER);
        avatar.addView(gear, gp);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                Ui.dp(context, 31), Ui.dp(context, 31));
        ap.leftMargin = Ui.dp(context, 10);
        row.addView(avatar, ap);

        addView(row, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        setContentDescription("Settings");

        setOnFocusChangeListener((v, hasFocus) -> {
            animate().scaleX(hasFocus ? 1.055f : 1f)
                    .scaleY(hasFocus ? 1.055f : 1f)
                    .setDuration(hasFocus ? 220 : 160)
                    .start();
            setElevation(Ui.dpf(context, hasFocus ? 18f : 8f));
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

    public void setOnOpenSettings(Runnable runnable) {
        onOpenSettings = runnable;
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
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

}
