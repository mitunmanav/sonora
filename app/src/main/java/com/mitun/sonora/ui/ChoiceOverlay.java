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
    private final LinearLayout column;
    private final Callback callback;
    /** How many rows are real options, as opposed to a message. */
    private int optionCount;
    private int selected = -1;

    public ChoiceOverlay(Context context, String title, String[] options, int selectedIndex,
                         Callback callback) {
        super(context);
        this.callback = callback;
        // The focus has to start on the option that is ticked. Opening on the
        // first one and OK-ing it would silently change the setting. A value
        // that is not on the list (a wallpaper that is a photo, a stale
        // preference) ticks nothing, and the focus falls on the first row.
        this.selected = (selectedIndex >= 0 && selectedIndex < options.length)
                ? selectedIndex : -1;
        // Heavy dim: a modal task pushes the screen behind it back.
        setBackgroundColor(0xF7000000);

        column = new LinearLayout(context);
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

        optionCount = options.length;
        for (int i = 0; i < options.length; i++) {
            addOption(context, options[i], i == selected);
        }
        if (options.length == 0) {
            // A picker with no options would be a modal the remote cannot
            // use, so it says there is nothing to choose.
            addOption(context, "There is nothing to choose", false);
        }

        // The picker arrives: it grows out of the place it came from rather
        // than appearing all at once. A spring carries the movement, so it
        // can be interrupted.
        column.setAlpha(0f);
        column.setTranslationY(Ui.dp(context, 26));
        Motion.spring(column, Motion.ALPHA, 1f, Motion.DAMPING_SPRINGY, Motion.RESPONSE_PANEL);
        Motion.spring(column, Motion.TRANSLATION_Y, 0f, Motion.DAMPING_SPRINGY,
                Motion.RESPONSE_PANEL);
    }

    /**
     * Takes the picker away, then runs the work that was asked for.
     *
     * The picker leaves the way it came: down and out. The caller removes the
     * view when the movement is done.
     */
    public void dismiss(Runnable after) {
        Motion.spring(column, Motion.ALPHA, 0f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_PANEL,
                after);
        Motion.spring(column, Motion.TRANSLATION_Y, Ui.dp(getContext(), 26),
                Motion.DAMPING_CRITICAL, Motion.RESPONSE_PANEL);
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

        final TextView labelText = label;
        final TextView checkText = check;
        final GlassDrawable bg = (GlassDrawable) line.getBackground();
        // The glass and the ink materialize together: alpha, rim light and ink
        // color spring into focus, so a fast D-pad move never snaps the row.
        final float[] focus = {0f};
        line.setOnFocusChangeListener((v, focused) -> {
            Motion.springCustom(v, 0, focused ? 1f : 0f, Motion.DAMPING_CRITICAL,
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
                            int ink = Ui.lerpColor(Color.WHITE, 0xFF101C26, value);
                            labelText.setTextColor(ink);
                            checkText.setTextColor(ink);
                        }
                    });
            v.setElevation(focused ? Ui.dpf(getContext(), 8f) : 0f);
        });
        line.setOnClickListener(v -> {
            int index = list.indexOfChild(v);
            if (index < 0 || index >= optionCount) {
                // The "nothing to choose" row is a message, not an option.
                return;
            }
            selected = index;
            if (callback != null) {
                callback.onPicked(selected);
            }
        });
        list.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void focusSelected() {
        // The row that is ticked, or the first row when nothing is ticked:
        // a picker with no focus is a picker the remote cannot use.
        int index = (selected >= 0 && selected < list.getChildCount()) ? selected : 0;
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
