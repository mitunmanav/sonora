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
        String spoken = title;
        if (type == TOGGLE) {
            spoken += ", " + value;
        } else if (type == CHECK) {
            spoken += value.length() == 0 ? ", not selected" : ", selected";
        } else if ((type == VALUE || type == CHOICE || type == PUSH) && value.length() > 0) {
            spoken += ", " + value;
        }
        line.setContentDescription(spoken);

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
        // A value is a technical thing: an address, a package name, a path.
        // It stays in reading order even in a right-to-left language.
        valueView.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
        line.addView(valueView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));

        TextView chevron = null;
        if (type == PUSH) {
            chevron = new TextView(context);
            // The arrow points the way the row opens, which is the other way
            // round in a right-to-left language.
            boolean rtl = context.getResources().getConfiguration().getLayoutDirection()
                    == View.LAYOUT_DIRECTION_RTL;
            chevron.setText(rtl ? "‹" : "›");
            chevron.setTextColor(0x80FFFFFF);
            chevron.setTextSize(22);
            chevron.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            // The chevron needs air, or it reads as part of the text beside it.
            // A start margin is mirrored with the row; a left margin is not.
            chevronParams.setMarginStart(Ui.dp(context, 14));
            line.addView(chevron, chevronParams);
        }

        final TextView valueText = valueView;
        final TextView labelText = label;
        final TextView chevronText = chevron;
        final int labelOff = type == DANGER ? 0xFFFF6B6B : 0xE6FFFFFF;
        final int labelOn = type == DANGER ? 0xFF8C1D18 : 0xFF101C26;
        final int valueOff = 0xB3FFFFFF;
        final int valueOn = 0xCC101C26;
        final int chevronOff = 0x80FFFFFF;
        final int chevronOn = 0x99101C26;
        final float[] focus = {0f};
        line.setOnFocusChangeListener((view, focused) -> {
            final GlassDrawable bg = (GlassDrawable) view.getBackground();
            Motion.springCustom(view, 0, focused ? 1f : 0f, Motion.DAMPING_CRITICAL,
                    Motion.RESPONSE_FOCUS, new Motion.FloatProperty() {
                        @Override
                        public float get() {
                            return focus[0];
                        }

                        @Override
                        public void set(float value) {
                            focus[0] = value;
                            bg.setAlpha(Math.round(255f * value));
                            bg.setFocus(value);
                            labelText.setTextColor(Ui.lerpColor(labelOff, labelOn, value));
                            valueText.setTextColor(Ui.lerpColor(valueOff, valueOn, value));
                            if (chevronText != null) {
                                chevronText.setTextColor(Ui.lerpColor(chevronOff, chevronOn, value));
                            }
                        }
                    });
            // Lift the row, so its small focus scale is not covered by the
            // row below it.
            view.setElevation(focused ? Ui.dpf(context, 8f) : 0f);
            Motion.scale(view, focused ? 1.02f : 1f, Motion.DAMPING_CRITICAL,
                    Motion.RESPONSE_FOCUS);
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
