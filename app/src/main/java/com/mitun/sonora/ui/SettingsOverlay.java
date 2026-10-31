package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mitun.sonora.Prefs;

import java.util.Locale;

/**
 * tvOS-style settings: a flat list, focus moves with the D-pad, and the value
 * sits on the right. Rows either toggle, cycle a list of choices, or run an
 * action.
 */
public final class SettingsOverlay extends FrameLayout {
    public interface Callbacks {
        void onSettingChanged(String key);

        void onAction(String key);

        void onClose();
    }

    private final Prefs prefs;
    private final Callbacks callbacks;
    private final LinearLayout list;
    private final ScrollView scroller;
    private final View dim;

    public SettingsOverlay(Context context, Prefs prefs, Callbacks callbacks) {
        super(context);
        this.prefs = prefs;
        this.callbacks = callbacks;
        setBackgroundColor(0xF0000000);

        dim = new View(context);
        dim.setBackgroundColor(0xE6000000);
        addView(dim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Ui.dp(context, 70), Ui.dp(context, 40),
                Ui.dp(context, 70), Ui.dp(context, 40));

        TextView title = new TextView(context);
        title.setText("Settings");
        title.setTextColor(Color.WHITE);
        title.setTextSize(34);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        column.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(context);
        subtitle.setText("Aerial · a tvOS-style home for your TV");
        subtitle.setTextColor(0xB3FFFFFF);
        subtitle.setTextSize(15);
        subtitle.setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 22));
        column.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroller = new ScrollView(context);
        scroller.setFocusable(false);
        scroller.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        column.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView hint = new TextView(context);
        hint.setText("OK to change · BACK to go back");
        hint.setTextColor(0x80FFFFFF);
        hint.setTextSize(13);
        hint.setPadding(0, Ui.dp(context, 16), 0, 0);
        column.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setVisibility(GONE);
    }

    /** Rebuild the visible rows from the current preferences. */
    public void rebuild() {
        list.removeAllViews();
        Context context = getContext();
        header(context, "Home Screen");
        row(context, "wallpaper", "Wallpaper", wallpaperName());
        row(context, "columns", "Apps per row", columnsName());
        row(context, "labels", "App names", labelName());
        row(context, "clock24", "24-hour clock", prefs.clock24() ? "On" : "Off");
        row(context, "show_date", "Show date", prefs.showDate() ? "On" : "Off");
        row(context, "show_seconds", "Show seconds", prefs.showSeconds() ? "On" : "Off");

        header(context, "Screen Saver");
        row(context, "ss_delay", "Start after", delayName());
        row(context, "ss_scene", "Each scene", sceneName());
        row(context, "ss_clock", "Clock", screensaverClockName());

        header(context, "Apps");
        row(context, "hidden", "Hidden apps", prefs.hidden().isEmpty() ? "None" : "Manage");
        row(context, "reset_order", "Reset app order", "");
        row(context, "refresh", "Reload apps", "");

        header(context, "System");
        row(context, "set_default", "Set as default launcher", "");
        row(context, "about", "About Aerial", "");
    }

    private void header(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text.toUpperCase(Locale.US));
        view.setTextColor(0x8CFFFFFF);
        view.setTextSize(12);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(0.12f);
        view.setPadding(Ui.dp(context, 22), Ui.dp(context, 22), 0, Ui.dp(context, 6));
        list.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void row(Context context, String key, String title, String value) {
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(Ui.dp(context, 22), Ui.dp(context, 12), Ui.dp(context, 22), Ui.dp(context, 12));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(Ui.dpf(context, 12));
        background.setColor(0x00FFFFFF);
        line.setBackground(background);
        line.setFocusable(true);
        line.setFocusableInTouchMode(false);
        line.setTag(key);

        TextView label = new TextView(context);
        label.setText(title);
        label.setTextColor(Color.WHITE);
        label.setTextSize(19);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        line.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(context);
        valueView.setText(value);
        valueView.setTextColor(0xB3FFFFFF);
        valueView.setTextSize(17);
        valueView.setGravity(Gravity.END);
        valueView.setMaxLines(1);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        line.addView(valueView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));

        line.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable bg = (GradientDrawable) v.getBackground();
            bg.setColor(focused ? 0x33FFFFFF : 0x00FFFFFF);
            v.setScaleX(focused ? 1f : 1f);
            label.setTextColor(focused ? Color.WHITE : 0xE6FFFFFF);
        });
        line.setOnClickListener(v -> callbacks.onSettingChanged(key));
        list.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (list.getChildCount() == 1) {
            line.requestFocus();
        }
    }

    public void focusFirst() {
        if (list.getChildCount() > 0) {
            list.getChildAt(0).requestFocus();
        }
    }

    private String wallpaperName() {
        if (prefs.wallpaper() < 0) {
            return "Photo";
        }
        return com.mitun.sonora.Scenes.ALL.get(prefs.wallpaper()).name;
    }

    private String columnsName() {
        int stored = prefs.columns();
        if (stored == 0) {
            // "Auto" - report the count the current screen actually gets.
            int widthDp = Math.round(getContext().getResources().getDisplayMetrics().widthPixels
                    / getContext().getResources().getDisplayMetrics().density);
            return "Auto (" + com.mitun.sonora.Grid.resolve(widthDp, 0).columns + ")";
        }
        return String.valueOf(stored);
    }

    private String labelName() {
        switch (prefs.labels()) {
            case Prefs.LABELS_FOCUSED:
                return "Focused only";
            case Prefs.LABELS_ALL:
                return "Always";
            default:
                return "Hidden";
        }
    }

    private String delayName() {
        int sec = prefs.screensaverDelaySec();
        if (sec <= 0) {
            return "Never";
        }
        if (sec % 3600 == 0) {
            return (sec / 3600) + " hr";
        }
        return (sec / 60) + " min";
    }

    private String sceneName() {
        return prefs.screensaverSceneSec() + " s";
    }

    private String screensaverClockName() {
        switch (prefs.screensaverClock()) {
            case 1:
                return "Always";
            case 2:
                return "Never";
            default:
                return "On tap";
        }
    }
}
