package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** A tvOS-style list picker: focus moves with the D-pad, OK picks, BACK cancels. */
public final class ChoiceOverlay extends FrameLayout {
    public interface Callback {
        void onPicked(int index);
    }

    private final LinearLayout list;
    private final Callback callback;
    private int selected = -1;

    public ChoiceOverlay(Context context, String title, String[] options, int selectedIndex,
                         Callback callback) {
        super(context);
        this.callback = callback;
        setBackgroundColor(0xE6000000);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Ui.dp(context, 60), Ui.dp(context, 40), Ui.dp(context, 60), Ui.dp(context, 40));

        TextView heading = new TextView(context);
        heading.setText(title);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(30);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setPadding(0, 0, 0, Ui.dp(context, 18));
        column.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroller = new ScrollView(context);
        scroller.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        for (int i = 0; i < options.length; i++) {
            addOption(context, options[i], i == selectedIndex);
        }
    }

    private void addOption(Context context, String text, boolean checked) {
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(Ui.dp(context, 22), Ui.dp(context, 12), Ui.dp(context, 22), Ui.dp(context, 12));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(Ui.dpf(context, 12));
        background.setColor(0x00FFFFFF);
        line.setBackground(background);
        line.setFocusable(true);

        TextView label = new TextView(context);
        label.setText(text);
        label.setTextColor(Color.WHITE);
        label.setTextSize(19);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        line.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView check = new TextView(context);
        check.setText("✓");
        check.setTextColor(Color.WHITE);
        check.setTextSize(19);
        check.setVisibility(checked ? VISIBLE : INVISIBLE);
        line.addView(check, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        line.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable bg = (GradientDrawable) v.getBackground();
            bg.setColor(focused ? 0x33FFFFFF : 0x00FFFFFF);
        });
        line.setOnClickListener(v -> {
            selected = list.indexOfChild(v);
            if (callback != null) {
                callback.onPicked(selected);
            }
        });
        list.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void focusSelected() {
        int index = selected >= 0 ? selected : 0;
        if (index < list.getChildCount()) {
            list.getChildAt(index).requestFocus();
        }
    }

    /** Keeps the D-pad inside the picker instead of walking into the grid. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                Ui.moveListFocus(list, code == KeyEvent.KEYCODE_DPAD_UP);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
