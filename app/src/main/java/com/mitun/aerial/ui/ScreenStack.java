package com.mitun.aerial.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * A stack of tvOS-style list screens. Pushing a screen slides a new list in
 * over the old one; BACK pops it. Everything the app shows that is a list -
 * settings, the app manager, the debug panel - is a screen in this stack, so
 * they all navigate the same way.
 */
public final class ScreenStack extends FrameLayout {
    public interface Listener {
        /** BACK was pressed and the stack is now empty. */
        void onEmpty();

        /** A screen was pushed. */
        void onPushed();
    }

    private static final class Screen {
        final String title;
        final String subtitle;
        final List<SettingRow> rows = new ArrayList<>();
        View view;
        LinearLayout list;

        Screen(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private final List<Screen> stack = new ArrayList<>();
    private final Listener listener;

    public ScreenStack(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setBackgroundColor(0xF0000000);
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public int depth() {
        return stack.size();
    }

    /** Replaces the whole stack with one screen. */
    public void show(String title, String subtitle, List<SettingRow> rows) {
        stack.clear();
        push(title, subtitle, rows);
    }

    /** Adds a screen on top of the current one. */
    public void push(String title, String subtitle, List<SettingRow> rows) {
        Screen screen = new Screen(title, subtitle);
        screen.rows.addAll(rows);
        screen.view = buildScreen(screen);
        addView(screen.view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        stack.add(screen);
        focusTop();
        if (listener != null) {
            listener.onPushed();
        }
    }

    /** Rebuilds the top screen in place, keeping its position in the stack. */
    public void refresh() {
        if (stack.isEmpty()) {
            return;
        }
        Screen screen = stack.get(stack.size() - 1);
        int index = stack.size() - 1;
        removeView(screen.view);
        screen.rows.clear();
        stack.remove(index);
        stack.add(index, screen);
    }

    /** Pops the top screen. Returns false when the stack was already empty. */
    public boolean pop() {
        if (stack.isEmpty()) {
            return false;
        }
        Screen screen = stack.remove(stack.size() - 1);
        removeView(screen.view);
        if (stack.isEmpty()) {
            if (listener != null) {
                listener.onEmpty();
            }
        } else {
            focusTop();
        }
        return true;
    }

    public void focusTop() {
        if (stack.isEmpty()) {
            return;
        }
        Screen screen = stack.get(stack.size() - 1);
        if (screen.list.getChildCount() > 0) {
            screen.list.getChildAt(0).requestFocus();
        }
    }

    private View buildScreen(Screen screen) {
        Context context = getContext();
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Ui.dp(context, 70), Ui.dp(context, 40),
                Ui.dp(context, 70), Ui.dp(context, 40));

        TextView title = new TextView(context);
        title.setText(screen.title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(34);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        column.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (screen.subtitle != null && screen.subtitle.length() > 0) {
            TextView subtitle = new TextView(context);
            subtitle.setText(screen.subtitle);
            subtitle.setTextColor(0xB3FFFFFF);
            subtitle.setTextSize(15);
            subtitle.setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 22));
            column.addView(subtitle, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            title.setPadding(0, 0, 0, Ui.dp(context, 22));
        }

        ScrollView scroller = new ScrollView(context);
        scroller.setFocusable(false);
        scroller.setVerticalScrollBarEnabled(false);
        screen.list = new LinearLayout(context);
        screen.list.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(screen.list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView hint = new TextView(context);
        hint.setText("OK to select · BACK to go back");
        hint.setTextColor(0x80FFFFFF);
        hint.setTextSize(13);
        hint.setPadding(0, Ui.dp(context, 16), 0, 0);
        column.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (SettingRow row : screen.rows) {
            View view = row.view(context);
            screen.list.addView(view, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return column;
    }
}
