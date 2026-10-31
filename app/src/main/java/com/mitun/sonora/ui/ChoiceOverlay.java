package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Color;
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
        // The focus has to start on the option that is ticked. Opening on the
        // first one and OK-ing it would silently change the setting.
        this.selected = selectedIndex;
        // Heavy dim: a modal task pushes the screen behind it back.
        setBackgroundColor(0xF7000000);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        // Clear of the app title along the top, so the two never overlap.
        column.setPadding(Ui.dp(context, 60), Ui.dp(context, 120), Ui.dp(context, 60), Ui.dp(context, 40));

        TextView heading = new TextView(context);
        heading.setText(title);
        heading.setTextColor(Color.WHITE);
        Type.apply(heading, Type.DISPLAY);
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
        // The same material as the rows behind it, so the picker belongs to
        // the screen it came from.
        GlassDrawable material = new GlassDrawable(context, 12f, 1f);
        material.setAlpha(0);
        line.setBackground(material);
        line.setFocusable(true);

        TextView label = new TextView(context);
        label.setText(text);
        label.setTextColor(Color.WHITE);
        Type.apply(label, Type.HEADING);
        line.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView check = new TextView(context);
        check.setText("✓");
        check.setTextColor(Color.WHITE);
        Type.apply(check, Type.HEADING);
        check.setVisibility(checked ? VISIBLE : INVISIBLE);
        line.addView(check, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        line.setOnFocusChangeListener((v, focused) -> {
            GlassDrawable bg = (GlassDrawable) v.getBackground();
            bg.setAlpha(focused ? 255 : 0);
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
