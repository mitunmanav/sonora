package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.KeyEvent;
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
 *
 * A screen is either a list of {@link SettingRow}s or a custom view, for the
 * few panels that need a real list of their own (the app manager).
 *
 * The push and the pop travel the same path: a screen that came in from the
 * right leaves to the right, so the way back is the way it came.
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
        View custom;
        View view;
        LinearLayout list;
        ScrollView scroller;

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
        removeAllViews();
        push(title, subtitle, rows);
    }

    /** Adds a screen on top of the current one. */
    public void push(String title, String subtitle, List<SettingRow> rows) {
        Screen screen = new Screen(title, subtitle);
        screen.rows.addAll(rows);
        add(screen);
    }

    /** Adds a screen whose content is a view of its own, such as a list. */
    public void pushContent(String title, String subtitle, View content) {
        Screen screen = new Screen(title, subtitle);
        screen.custom = content;
        add(screen);
    }

    /**
     * Replaces the top screen with a freshly built one, keeping its place in
     * the stack. Use it when a value shown in the screen has changed.
     */
    public void refresh(String title, String subtitle, List<SettingRow> rows) {
        if (stack.isEmpty()) {
            return;
        }
        int index = stack.size() - 1;
        Screen old = stack.remove(index);
        removeView(old.view);
        Screen screen = new Screen(title, subtitle);
        screen.rows.addAll(rows);
        screen.view = buildScreen(screen);
        addView(screen.view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        stack.add(index, screen);
        focusTop();
    }

    /** Pops the top screen. Returns false when the stack was already empty. */
    public boolean pop() {
        if (stack.isEmpty()) {
            return false;
        }
        final Screen screen = stack.remove(stack.size() - 1);
        final int width = getWidth();
        if (stack.isEmpty()) {
            slideOut(screen, width, () -> {
                removeView(screen.view);
                if (listener != null) {
                    listener.onEmpty();
                }
            });
        } else {
            final Screen under = stack.get(stack.size() - 1);
            Motion.translateX(under.view, 0f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_MOVE);
            slideOut(screen, width, () -> removeView(screen.view));
        }
        return true;
    }

    public void focusTop() {
        if (stack.isEmpty()) {
            return;
        }
        Screen screen = stack.get(stack.size() - 1);
        if (screen.list != null) {
            // The first child is usually a heading, which cannot take focus.
            View target = firstFocusable(screen.list);
            if (target != null) {
                target.requestFocus();
            }
        } else if (screen.custom != null) {
            screen.custom.requestFocus();
        }
    }

    private static View firstFocusable(LinearLayout list) {
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child.isFocusable() && child.getVisibility() == View.VISIBLE) {
                return child;
            }
        }
        return null;
    }

    /**
     * Keeps the D-pad inside the panel. Without this the arrow keys walk out
     * of the list and into whatever is behind it.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                    || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                Screen screen = stack.isEmpty() ? null : stack.get(stack.size() - 1);
                if (screen != null && screen.list != null) {
                    if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                        Ui.moveListFocus(screen.list, code == KeyEvent.KEYCODE_DPAD_UP);
                    }
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void add(Screen screen) {
        screen.view = buildScreen(screen);
        addView(screen.view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        Screen previous = stack.isEmpty() ? null : stack.get(stack.size() - 1);
        stack.add(screen);
        slideIn(screen, previous);
        focusTop();
        if (screen.scroller != null) {
            // A new screen starts at its first line, not wherever the focus
            // pull would have scrolled it to.
            screen.scroller.post(() -> screen.scroller.scrollTo(0, 0));
        }
        if (listener != null) {
            listener.onPushed();
        }
    }

    /** A screen arrives from the right; the one under it steps back. */
    private void slideIn(Screen incoming, Screen outgoing) {
        int width = getWidth();
        if (width <= 0) {
            incoming.view.setTranslationX(0f);
            return;
        }
        incoming.view.setTranslationX(width);
        Motion.translateX(incoming.view, 0f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_PANEL);
        if (outgoing != null) {
            Motion.translateX(outgoing.view, -width * 0.22f, Motion.DAMPING_CRITICAL,
                    Motion.RESPONSE_MOVE);
        }
    }

    /** A screen leaves by the path it came in on. */
    private void slideOut(final Screen screen, int width, final Runnable onDone) {
        if (width <= 0) {
            onDone.run();
            return;
        }
        Motion.spring(screen.view, Motion.TRANSLATION_X, width, Motion.DAMPING_CRITICAL,
                Motion.RESPONSE_PANEL, onDone);
    }

    private View buildScreen(Screen screen) {
        Context context = getContext();
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Ui.dp(context, 70), Ui.dp(context, 40),
                Ui.dp(context, 70), Ui.dp(context, 40));
        // Each screen covers the one below it, so a screen sliding in does
        // not show the screen it is replacing through it.
        column.setBackgroundColor(0xFF000000);

        TextView title = new TextView(context);
        title.setText(screen.title);
        title.setTextColor(Color.WHITE);
        Type.apply(title, Type.DISPLAY);
        column.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (screen.subtitle != null && screen.subtitle.length() > 0) {
            TextView subtitle = new TextView(context);
            subtitle.setText(screen.subtitle);
            subtitle.setTextColor(0xB3FFFFFF);
            Type.apply(subtitle, Type.CAPTION);
            subtitle.setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 22));
            column.addView(subtitle, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            title.setPadding(0, 0, 0, Ui.dp(context, 22));
        }

        if (screen.custom != null) {
            column.addView(screen.custom, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            ScrollView scroller = new ScrollView(context);
            scroller.setFocusable(false);
            scroller.setVerticalScrollBarEnabled(false);
            screen.scroller = scroller;
            screen.list = new LinearLayout(context);
            screen.list.setOrientation(LinearLayout.VERTICAL);
            scroller.addView(screen.list, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            column.addView(scroller, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }

        TextView hint = new TextView(context);
        hint.setText("OK to select · BACK to go back");
        hint.setTextColor(0x80FFFFFF);
        Type.apply(hint, Type.CAPTION);
        hint.setPadding(0, Ui.dp(context, 16), 0, 0);
        column.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (screen.custom == null) {
            for (SettingRow row : screen.rows) {
                View view = row.view(context);
                screen.list.addView(view, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
        return column;
    }
}
