package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
        /** The row that opened the screen above this one, so BACK returns to it. */
        View returnFocus;

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

    /** Replaces the stack with a screen whose content has its own layout. */
    public void showContent(String title, String subtitle, View content) {
        stack.clear();
        removeAllViews();
        Screen screen = new Screen(title, subtitle);
        screen.custom = content;
        add(screen);
    }

    /**
     * Pushes a screen, or shows it as the only one when the stack is empty.
     * A panel reached from a list keeps the list behind it, so BACK goes back
     * to where the user came from.
     */
    public void showOrPush(String title, String subtitle, List<SettingRow> rows) {
        if (stack.isEmpty()) {
            show(title, subtitle, rows);
        } else {
            push(title, subtitle, rows);
        }
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
     *
     * The focus stays on the row the user was on. Rebuilding a screen and
     * throwing the focus back to the top of it is what makes a settings list
     * feel like it is fighting the user.
     */
    public void refresh(String title, String subtitle, List<SettingRow> rows) {
        if (stack.isEmpty()) {
            return;
        }
        int index = stack.size() - 1;
        Screen old = stack.remove(index);
        int focusedIndex = -1;
        int scrollY = 0;
        if (old.list != null) {
            View focused = old.list.getFocusedChild();
            if (focused != null) {
                focusedIndex = old.list.indexOfChild(focused);
            }
        }
        if (old.scroller != null) {
            scrollY = old.scroller.getScrollY();
        }
        removeView(old.view);
        Screen screen = new Screen(title, subtitle);
        screen.rows.addAll(rows);
        screen.view = buildScreen(screen);
        addView(screen.view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        stack.add(index, screen);
        if (focusedIndex >= 0 && screen.list != null
                && focusedIndex < screen.list.getChildCount()
                && screen.list.getChildAt(focusedIndex).isFocusable()) {
            screen.list.getChildAt(focusedIndex).requestFocus();
        } else {
            focusTop();
        }
        if (screen.scroller != null) {
            final int y = scrollY;
            screen.scroller.post(() -> screen.scroller.scrollTo(0, y));
        }
    }

    /** Rebuilds the top custom screen while leaving the screens beneath it intact. */
    public void refreshContent(String title, String subtitle, View content) {
        if (stack.isEmpty()) {
            return;
        }
        int index = stack.size() - 1;
        Screen old = stack.remove(index);
        removeView(old.view);
        Screen screen = new Screen(title, subtitle);
        screen.custom = content;
        screen.view = buildScreen(screen);
        addView(screen.view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        stack.add(index, screen);
        focusTop();
    }

    /**
     * Rebuilds the custom screen under the top one, leaving the top screen and
     * its focus alone. Used to keep the settings index current while a page
     * opened from it changes the values it shows.
     */
    public boolean refreshContentBelow(String title, String subtitle, View content) {
        if (stack.size() < 2) {
            return false;
        }
        int index = stack.size() - 2;
        Screen old = stack.get(index);
        removeView(old.view);
        Screen screen = new Screen(title, subtitle);
        screen.custom = content;
        screen.view = buildScreen(screen);
        // The screen it replaces had stepped back for the page above it.
        screen.view.setTranslationX(-getWidth() * 0.22f);
        addView(screen.view, index, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        stack.set(index, screen);
        return true;
    }

    /** The title of the screen on top, or an empty string when there is none. */
    public String topTitle() {
        if (stack.isEmpty()) {
            return "";
        }
        return stack.get(stack.size() - 1).title;
    }

    /** The custom content of the screen on top, or null when it is a list. */
    public View topContent() {
        if (stack.isEmpty()) {
            return null;
        }
        return stack.get(stack.size() - 1).custom;
    }

    /** The custom content of the screen just below the top one, or null. */
    public View topContentBelow() {
        return stack.size() < 2 ? null : stack.get(stack.size() - 2).custom;
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
            // The focus went with the screen that was popped, so the screen
            // underneath is left with nothing focused and the D-pad goes
            // nowhere. It gets the focus back as it comes in.
            slideOut(screen, width, () -> {
                removeView(screen.view);
                restoreFocus(under);
            });
        }
        return true;
    }

    public void focusTop() {
        if (stack.isEmpty()) {
            return;
        }
        Screen screen = stack.get(stack.size() - 1);
        // Coming back from another app or a system settings screen must not
        // move the focus: the screen already knows where the user was.
        View already = screen.view == null ? null : screen.view.findFocus();
        if (already != null && already.isFocusable()) {
            return;
        }
        if (screen.list != null) {
            // The first child is usually a heading, which cannot take focus.
            View target = firstFocusable(screen.list);
            if (target != null) {
                requestFocus(target);
            }
        } else if (screen.custom != null) {
            requestFocus(screen.custom);
        }
    }

    /**
     * Gives the focus to a view, and asks again once the screen is laid out.
     *
     * A window that has not been given the focus cannot take it, and a view
     * that has not been laid out cannot be asked. Both are true when a screen
     * is built while the activity is still starting, which is why the request
     * is repeated: without it the screen opens with nothing focused and the
     * D-pad goes nowhere.
     */
    private static void requestFocus(final View target) {
        target.requestFocus();
        target.post(() -> {
            if (!target.isFocused() && target.isFocusable()) {
                target.requestFocus();
            }
        });
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
        Screen previous = stack.isEmpty() ? null : stack.get(stack.size() - 1);
        if (previous != null) {
            // Remember the row that opened this screen while it still has the
            // focus; by the time BACK runs, the focus has moved to the child.
            previous.returnFocus = previous.view == null ? null : previous.view.findFocus();
        }
        addView(screen.view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
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

    /** Gives the focus back to the row that opened the screen just popped. */
    private void restoreFocus(Screen screen) {
        View target = screen.returnFocus;
        screen.returnFocus = null;
        if (target != null && target.isFocusable() && target.isAttachedToWindow()) {
            requestFocus(target);
        } else {
            focusTop();
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
        boolean settingsIndex = "Settings".equals(screen.title) && screen.custom != null;
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Ui.dp(context, settingsIndex ? 80 : 64),
                Ui.dp(context, settingsIndex ? 56 : 36),
                Ui.dp(context, settingsIndex ? 80 : 64), Ui.dp(context, 36));
        // Each screen covers the one below it, so a screen sliding in does
        // not show the screen it is replacing through it.
        column.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF08131D, 0xFF050A10}));

        if (!settingsIndex) {
            LinearLayout heading = new LinearLayout(context);
            heading.setOrientation(LinearLayout.HORIZONTAL);
            heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView title = new TextView(context);
            title.setText(screen.title);
            title.setTextColor(Color.WHITE);
            Type.apply(title, Type.DISPLAY);
            heading.addView(title, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            column.addView(heading, new LinearLayout.LayoutParams(
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
