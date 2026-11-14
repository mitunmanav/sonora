package com.mitun.sonora.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** A tvOS-style list picker: focus moves with the D-pad, OK picks, BACK cancels. */
public final class ChoiceOverlay extends FrameLayout {
    public interface Callback {
        void onPicked(int index);
    }

    private final LinearLayout list;
    private final LinearLayout column;
    private final Callback callback;
    /** The options index each row stands for; rows and indices differ once a null is skipped. */
    private final List<Integer> optionIndex = new ArrayList<>();
    private int selected = -1;
    /** Once the dismiss fade starts the rows are gone for good: a second OK
     *  mid-fade must not answer the picker twice. */
    private boolean dismissed;

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
        setBackgroundColor(Theme.modal());

        column = new LinearLayout(context) {
            // Its fade would otherwise take a screen-sized offscreen buffer.
            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }
        };
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        column.setPadding(0, Ui.dp(context, ScreenStack.PAGE_TITLE_TOP_DP), 0, 0);

        // Laid out as a settings page, so a choice looks like the screen it came from.
        TextView heading = new TextView(context);
        heading.setText(title);
        heading.setTextColor(Theme.ink());
        Type.apply(heading, Type.DISPLAY);
        heading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, ScreenStack.PAGE_TITLE_SP);
        heading.setGravity(Gravity.CENTER);
        heading.setSingleLine(true);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        column.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        android.util.DisplayMetrics screen = context.getResources().getDisplayMetrics();
        ScrollView scroller = new ScrollView(context);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        scroller.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // The middle of the screen, a third of its width: a choice, not a page of its own.
        int side = Math.round(screen.widthPixels * 0.29f);
        LinearLayout.LayoutParams scrollerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollerParams.setMarginStart(side);
        scrollerParams.setMarginEnd(side);
        scrollerParams.topMargin = Ui.dp(context, 24);
        scrollerParams.bottomMargin = Ui.dp(context, 20);
        column.addView(scroller, scrollerParams);

        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        for (int i = 0; i < options.length; i++) {
            if (options[i] == null) {
                continue;
            }
            addOption(context, options[i], i == selected);
            optionIndex.add(i);
        }
        if (optionIndex.isEmpty()) {
            // A picker with no options would be a modal the remote cannot
            // use, so it says there is nothing to choose.
            addOption(context, "There is nothing to choose", false);
            list.getChildAt(list.getChildCount() - 1).setFocusable(false);
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
        dismissed = true;
        Motion.spring(column, Motion.ALPHA, 0f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_PANEL,
                after);
        Motion.spring(column, Motion.TRANSLATION_Y, Ui.dp(getContext(), 26),
                Motion.DAMPING_CRITICAL, Motion.RESPONSE_PANEL);
    }

    private void addOption(Context context, String text, boolean checked) {
        PressRow line = new PressRow(context, 1.02f);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0);
        line.setMinimumHeight(Ui.dp(context, 33));
        final RowDrawable bg = new RowDrawable(context);
        line.setBackground(bg);
        line.setFocusable(true);

        TextView label = new TextView(context);
        label.setText(text);
        label.setTextColor(Theme.inkSoft());
        Type.apply(label, Type.HEADING);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        line.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView check = new TextView(context);
        check.setText("\u2713");
        check.setTextColor(Theme.inkSoft());
        Type.apply(check, Type.HEADING);
        check.setVisibility(checked ? VISIBLE : INVISIBLE);
        line.addView(check, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // One property per row, so a focus change allocates nothing.
        final Motion.FocusValue focusValue = new Motion.FocusValue(value -> {
            bg.setFocus(value);
            int ink = Bitmaps.lerpColor(Theme.inkSoft(), Theme.INK_ON_FOCUS, value);
            label.setTextColor(ink);
            check.setTextColor(ink);
        });
        line.setOnFocusChangeListener((v, focused) -> {
            Motion.springCustom(v, 0, focused ? 1f : 0f, Motion.DAMPING_CRITICAL,
                    Motion.RESPONSE_FOCUS, focusValue);
            v.setElevation(focused ? Ui.dpf(getContext(), 8f) : 0f);
        });
        line.setOnClickListener(v -> {
            if (dismissed) {
                return;
            }
            int index = list.indexOfChild(v);
            if (index < 0 || index >= optionIndex.size()) {
                // The "nothing to choose" row is a message, not an option.
                return;
            }
            selected = optionIndex.get(index);
            if (callback != null) {
                callback.onPicked(selected);
            }
        });
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lineParams.bottomMargin = Ui.dp(context, 7);
        list.addView(line, lineParams);
    }

    public void focusSelected() {
        // The row that is ticked, or the first row when nothing is ticked:
        // a picker with no focus is a picker the remote cannot use.
        int index = optionIndex.indexOf(selected);
        if (index < 0) {
            index = 0;
        }
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
