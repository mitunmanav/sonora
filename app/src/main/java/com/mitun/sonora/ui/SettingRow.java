package com.mitun.sonora.ui;

import android.content.Context;
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
        this.title = title == null ? "" : title;
        this.type = type;
        this.action = action;
    }

    public static SettingRow header(String title) {
        return new SettingRow(title, HEADER, null);
    }

    public static SettingRow value(String title, String value) {
        SettingRow row = new SettingRow(title, VALUE, null);
        row.value = value == null ? "" : value;
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
        row.value = value == null ? "" : value;
        return row;
    }

    /** A value that opens a picker. No chevron: it picks, it does not navigate. */
    public static SettingRow choice(String title, String value, Runnable action) {
        SettingRow row = new SettingRow(title, CHOICE, action);
        row.value = value == null ? "" : value;
        return row;
    }

    public static SettingRow danger(String title, Runnable action) {
        return new SettingRow(title, DANGER, action);
    }

    /** A line of prose under a row. */
    public static SettingRow note(String text) {
        SettingRow row = new SettingRow(text, NOTE, null);
        row.value = row.title();
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
            view.setTextColor(Theme.inkTertiary());
            Type.apply(view, Type.MICRO);
            view.setPadding(Ui.dp(context, 14), Ui.dp(context, 18), 0, Ui.dp(context, 6));
            return view;
        }

        if (type == NOTE) {
            TextView view = new TextView(context);
            view.setText(title);
            view.setTextColor(Theme.inkTertiary());
            Type.apply(view, Type.CAPTION);
            view.setPadding(Ui.dp(context, 14), Ui.dp(context, 2), Ui.dp(context, 14), Ui.dp(context, 10));
            view.setFocusable(false);
            return view;
        }

        PressRow line = new PressRow(context, 1.02f);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0);
        // The row is a pane of light glass at rest and an opaque white card
        // under the focus, the same material as the settings index.
        RowDrawable material = new RowDrawable(context);
        line.setBackground(material);
        line.setMinimumHeight(Ui.dp(context, 33));
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
        label.setTextColor(type == DANGER ? Theme.danger() : Theme.ink());
        Type.apply(label, Type.HEADING);
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        line.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(context);
        valueView.setText(value);
        valueView.setTextColor(Theme.inkSecondary());
        Type.apply(valueView, Type.BODY);
        valueView.setGravity(Gravity.END);
        valueView.setMaxLines(1);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        // A value is a technical thing: an address, a package name, a path.
        // It stays in reading order even in a right-to-left language.
        valueView.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
        // The settings list is narrower than a phone list: a value may not
        // eat its label. A read-only value is a technical string and keeps
        // the room it needs.
        if (type != VALUE) {
            valueView.setMaxWidth(Math.round(
                    context.getResources().getDisplayMetrics().widthPixels * 0.22f));
        }
        line.addView(valueView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView chevron = null;
        if (type == PUSH) {
            chevron = new TextView(context);
            // The arrow points the way the row opens, which is the other way
            // round in a right-to-left language.
            boolean rtl = context.getResources().getConfiguration().getLayoutDirection()
                    == View.LAYOUT_DIRECTION_RTL;
            chevron.setText(rtl ? "‹" : "›");
            chevron.setTextColor(Theme.inkTertiary());
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
        final int labelOff = type == DANGER ? Theme.danger() : Theme.inkSoft();
        final int labelOn = type == DANGER ? Theme.DANGER_ON_FOCUS : Theme.INK_ON_FOCUS;
        final int valueOff = Theme.inkSecondary();
        final int valueOn = Theme.INK_ON_FOCUS_SOFT;
        final int chevronOff = Theme.inkTertiary();
        final int chevronOn = 0x99101C26;
        // One property per row, so a focus change allocates nothing.
        final Motion.FocusValue focusValue = new Motion.FocusValue(value -> {
            material.setFocus(value);
            labelText.setTextColor(Bitmaps.lerpColor(labelOff, labelOn, value));
            valueText.setTextColor(Bitmaps.lerpColor(valueOff, valueOn, value));
            if (chevronText != null) {
                chevronText.setTextColor(Bitmaps.lerpColor(chevronOff, chevronOn, value));
            }
        });
        line.setOnFocusChangeListener((view, focused) -> {
            Motion.springCustom(view, 0, focused ? 1f : 0f, Motion.DAMPING_CRITICAL,
                    Motion.RESPONSE_FOCUS, focusValue);
            // Lift the row, so its small focus scale is not covered by the
            // row below it. The scale itself is the row's (PressRow).
            view.setElevation(focused ? Ui.dpf(context, 8f) : 0f);
        });
        if (action != null) {
            line.setOnClickListener(v -> action.run());
        } else {
            line.setFocusable(false);
            valueText.setTextColor(Theme.inkTertiary());
        }
        return line;
    }
}
