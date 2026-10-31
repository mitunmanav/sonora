package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * One row of a tvOS-style list. Rows either show a value, run an action, or
 * push another screen. Keeping this in one place means the settings list, the
 * app manager and the debug panel all look and behave the same.
 */
public final class SettingRow {
    public static final int HEADER = 0;
    public static final int VALUE = 1;
    public static final int ACTION = 2;
    public static final int TOGGLE = 3;
    public static final int PUSH = 4;
    /** A destructive action: drawn in the warning colour. */
    public static final int DANGER = 5;
    /** A value that opens a picker when OK is pressed. */
    public static final int CHOICE = 6;
    /** A line of explanation. It is never focused and never takes an action. */
    public static final int NOTE = 7;
    /** A row that can be ticked. The tick is the whole answer. */
    public static final int CHECK = 8;

    private final String title;
    private final int type;
    private final Runnable action;
    private String value = "";

    private SettingRow(String title, int type, Runnable action) {
        this.title = title;
        this.type = type;
        this.action = action;
    }

    public static SettingRow header(String title) {
        return new SettingRow(title, HEADER, null);
    }

    public static SettingRow value(String title, String value) {
        SettingRow row = new SettingRow(title, VALUE, null);
        row.value = value;
        return row;
    }

    public static SettingRow action(String title, Runnable action) {
        return new SettingRow(title, ACTION, action);
    }

    public static SettingRow toggle(String title, boolean on, Runnable action) {
        SettingRow row = new SettingRow(title, TOGGLE, action);
        row.value = on ? "On" : "Off";
        return row;
    }

    public static SettingRow push(String title, String value, Runnable action) {
        SettingRow row = new SettingRow(title, PUSH, action);
        row.value = value;
        return row;
    }

    /** A value that opens a picker. No chevron: it picks, it does not navigate. */
    public static SettingRow choice(String title, String value, Runnable action) {
        SettingRow row = new SettingRow(title, CHOICE, action);
        row.value = value;
        return row;
    }

    public static SettingRow danger(String title, Runnable action) {
        return new SettingRow(title, DANGER, action);
    }

    /** A line of prose under a row. */
    public static SettingRow note(String text) {
        SettingRow row = new SettingRow(text, NOTE, null);
        row.value = text;
        return row;
    }

    /** A row with a tick in it. */
    public static SettingRow check(String title, boolean checked, Runnable action) {
        SettingRow row = new SettingRow(title, CHECK, action);
        row.value = checked ? "✓" : "";
        return row;
    }

    public String title() {
        return title;
    }

    public String value() {
        return value;
    }

    public int type() {
        return type;
    }

    /** Builds the platform view for this row. */
    public View view(Context context) {
        if (type == HEADER) {
            TextView view = new TextView(context);
            view.setText(title.toUpperCase(java.util.Locale.US));
            view.setTextColor(0x8CFFFFFF);
            Type.apply(view, Type.MICRO);
            view.setPadding(Ui.dp(context, 22), Ui.dp(context, 22), 0, Ui.dp(context, 6));
            return view;
        }

        if (type == NOTE) {
            TextView view = new TextView(context);
            view.setText(title);
            view.setTextColor(0x8CFFFFFF);
            Type.apply(view, Type.CAPTION);
            view.setPadding(Ui.dp(context, 22), Ui.dp(context, 2), Ui.dp(context, 22), Ui.dp(context, 10));
            view.setFocusable(false);
            return view;
        }

        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(Ui.dp(context, 22), Ui.dp(context, 12), Ui.dp(context, 22), Ui.dp(context, 12));
        // The row carries the same material as the rest of the app: a
        // translucent surface with a light top edge. It is invisible until
        // the row has the focus, then it dims what is behind it.
        GlassDrawable material = new GlassDrawable(context, 12f, 1f);
        material.setAlpha(0);
        line.setBackground(material);
        line.setFocusable(true);
        line.setFocusableInTouchMode(false);
        line.setContentDescription(title);

        TextView label = new TextView(context);
        label.setText(title);
        label.setTextColor(type == DANGER ? 0xFFFF6B6B : Color.WHITE);
        Type.apply(label, Type.HEADING);
        line.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(context);
        valueView.setText(value);
        valueView.setTextColor(0xB3FFFFFF);
        Type.apply(valueView, Type.BODY);
        valueView.setGravity(Gravity.END);
        valueView.setMaxLines(1);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        line.addView(valueView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));

        if (type == PUSH) {
            TextView chevron = new TextView(context);
            chevron.setText("›");
            chevron.setTextColor(0x80FFFFFF);
            chevron.setTextSize(22);
            chevron.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            // The chevron needs air, or it reads as part of the text beside it.
            chevronParams.leftMargin = Ui.dp(context, 14);
            line.addView(chevron, chevronParams);
        }

        final TextView valueText = valueView;
        final TextView labelText = label;
        line.setOnFocusChangeListener((v, focused) -> {
            GlassDrawable bg = (GlassDrawable) v.getBackground();
            bg.setAlpha(focused ? 255 : 0);
            if (type != DANGER) {
                labelText.setTextColor(focused ? Color.WHITE : 0xE6FFFFFF);
            }
            valueText.setTextColor(focused ? 0xE6FFFFFF : 0xB3FFFFFF);
        });
        if (action != null) {
            line.setOnClickListener(v -> action.run());
        } else {
            line.setFocusable(false);
            valueText.setTextColor(0x8CFFFFFF);
        }
        return line;
    }
}
