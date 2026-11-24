package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mitun.sonora.R;

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

        /** A different screen is now on top, so what stands beside it can follow. */
        void onTopChanged(String title);
    }

    private static final class Screen {
        final String title;
        final String subtitle;
        final List<SettingRow> rows = new ArrayList<>();
        View custom;
        View view;
        LinearLayout list;
        ScrollView scroller;
        /** Drawn without the standard list chrome, e.g. the settings index. */
        boolean fullBleed;
        /** The tvOS settings layout: title above, list on the right. */
        boolean split;
        /** The row that opened the screen above this one, so BACK returns to it. */
        View returnFocus;

        Screen(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    /** Measured from tvOS 26 Settings. */
    public static final float PAGE_TITLE_SP = 31f;
    public static final int PAGE_TITLE_TOP_DP = 24;
    public static final float PAGE_LIST_TOP = 0.175f;
    private static final float PAGE_NOTE_TOP = 0.76f;

    private final List<Screen> stack = new ArrayList<>();
    private final Listener listener;
    /** Bumped whenever the stack changes, so a late callback knows it is stale. */
    private int generation;

    public ScreenStack(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        // The activity draws the blurred wallpaper and the settings mark
        // behind this stack; only the screens that want a plain surface
        // paint over them.
        setBackgroundColor(Color.TRANSPARENT);
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public int depth() {
        return stack.size();
    }

    /** Replaces the whole stack with a screen whose content has its own layout. */
    public void showContent(String title, String subtitle, View content) {
        showContent(title, subtitle, content, false);
    }

    /**
     * Replaces the stack with a screen whose content has its own layout.
     *
     * @param fullBleed true when the content draws the whole screen itself and
     *                  needs neither the standard padding nor a title
     */
    public void showContent(String title, String subtitle, View content, boolean fullBleed) {
        stack.clear();
        removeAllViews();
        Screen screen = new Screen(title, subtitle);
        screen.custom = content;
        screen.fullBleed = fullBleed;
        add(screen);
    }

    private void push(String title, String subtitle, List<SettingRow> rows, boolean split) {
        Screen screen = new Screen(title, subtitle);
        screen.split = split;
        screen.rows.addAll(rows);
        add(screen);
    }

    /** A tvOS settings page: the same list, in the split layout. */
    public void pushSettings(String title, List<SettingRow> rows) {
        push(title, null, rows, true);
    }

    /** Adds a screen whose content is a view of its own, such as a list. */
    public void pushContent(String title, String subtitle, View content) {
        Screen screen = new Screen(title, subtitle);
        screen.custom = content;
        add(screen);
    }

    /** Adds a screen that lays out the whole page itself, title included. */
    public void pushFullBleed(String title, View content) {
        Screen screen = new Screen(title, null);
        screen.custom = content;
        screen.fullBleed = true;
        add(screen);
    }

    /**
     * Rebuilds a tvOS settings page with the values it now shows, keeping its
     * place in the stack. The focus stays on the row the user was on.
     */
    public void refreshSettings(String title, List<SettingRow> rows) {
        refresh(title, null, rows, true);
    }

    // The rebuild must keep the same kind of screen — list for list, custom
    // for custom. Whether the screen beneath shows or hides was decided for
    // the old pairing, and nothing here recomputes it.
    private void refresh(String title, String subtitle, List<SettingRow> rows, boolean split) {
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
        screen.split = split;
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
        refreshContent(title, subtitle, content, false);
    }

    /** As above, for custom content that draws the whole screen itself. */
    public void refreshContent(String title, String subtitle, View content, boolean fullBleed) {
        if (stack.isEmpty()) {
            return;
        }
        int index = stack.size() - 1;
        Screen old = stack.remove(index);
        removeView(old.view);
        Screen screen = new Screen(title, subtitle);
        screen.custom = content;
        screen.fullBleed = fullBleed;
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
        return refreshContentBelow(title, subtitle, content, false);
    }

    /** As above, for custom content that draws the whole screen itself. */
    public boolean refreshContentBelow(String title, String subtitle, View content,
                                       boolean fullBleed) {
        if (stack.size() < 2) {
            return false;
        }
        int index = stack.size() - 2;
        Screen old = stack.get(index);
        removeView(old.view);
        Screen screen = new Screen(title, subtitle);
        screen.custom = content;
        screen.fullBleed = fullBleed;
        screen.view = buildScreen(screen);
        Screen top = stack.get(stack.size() - 1);
        if (isSettings(screen) && isSettings(top)) {
            // The page above is transparent; the page under it has already
            // stepped out of sight and comes back on the way down.
            screen.view.setAlpha(0f);
            screen.view.setVisibility(View.INVISIBLE);
        } else {
            // The screen it replaces had stepped back for the page above it.
            screen.view.setTranslationX(-getWidth() * 0.22f);
        }
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
        final int gen = ++generation;
        final int width = getWidth();
        if (stack.isEmpty()) {
            slideOut(screen, width, () -> {
                removeView(screen.view);
                // A screen pushed while this one slid out makes this
                // callback stale; the stack is not empty any more.
                if (gen == generation && listener != null) {
                    listener.onEmpty();
                }
            });
        } else {
            final Screen under = stack.get(stack.size() - 1);
            if (listener != null) {
                listener.onTopChanged(under.title);
            }
            Motion.translateX(under.view, 0f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_MOVE);
            if (bothSettings(screen, under)) {
                // The settings pages share the wallpaper and the mark behind
                // them, so one has to give up its place on the way back in.
                under.view.setVisibility(View.VISIBLE);
                under.view.setAlpha(0f);
                Motion.spring(under.view, Motion.ALPHA, 1f, Motion.DAMPING_CRITICAL,
                        Motion.RESPONSE_MOVE);
                Motion.spring(screen.view, Motion.ALPHA, 0f, Motion.DAMPING_CRITICAL,
                        Motion.RESPONSE_MOVE);
            }
            // The focus went with the screen that was popped, so the screen
            // underneath is left with nothing focused and the D-pad goes
            // nowhere. It gets the focus back as it comes in.
            slideOut(screen, width, () -> {
                removeView(screen.view);
                if (gen == generation) {
                    restoreFocus(under);
                }
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
            Screen screen = stack.isEmpty() ? null : stack.get(stack.size() - 1);
            if (screen != null) {
                if (screen.list != null
                        && (code == KeyEvent.KEYCODE_DPAD_UP
                                || code == KeyEvent.KEYCODE_DPAD_DOWN
                                || code == KeyEvent.KEYCODE_DPAD_LEFT
                                || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                    if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                        // A page with nothing focusable, like one that is all
                        // notes and values, still has to move somewhere.
                        if (!Ui.moveListFocus(screen.list, code == KeyEvent.KEYCODE_DPAD_UP)
                                && screen.scroller != null) {
                            int dy = code == KeyEvent.KEYCODE_DPAD_UP
                                    ? -screen.scroller.getHeight()
                                    : screen.scroller.getHeight();
                            if (Motion.isReduced(getContext())) {
                                screen.scroller.scrollBy(0, dy);
                            } else {
                                screen.scroller.smoothScrollBy(0, dy);
                            }
                        }
                    }
                    // LEFT and RIGHT stop here too: the screen beneath is still
                    // visible at the edge, and a framework focus search would
                    // walk onto it.
                    return true;
                }
                if ((code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                        || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                        || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)
                        && (screen.view == null || screen.view.findFocus() == null)) {
                    // The top screen holds nothing focused, so the focus is
                    // on the screen it covers: a key passed on would drive a
                    // page the user cannot see, and OK would fire blind.
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * The framework may look for the next focus anywhere in the stack, but
     * only the top screen is a place focus may go: the page beneath is still
     * visible at the edge, and landing on it would drive a covered screen.
     */
    @Override
    public View focusSearch(View focused, int direction) {
        Screen screen = stack.isEmpty() ? null : stack.get(stack.size() - 1);
        if (screen == null || screen.view == null || !inside(screen.view, focused)) {
            return super.focusSearch(focused, direction);
        }
        View next = FocusFinder.getInstance().findNextFocus(
                (ViewGroup) screen.view, focused, direction);
        return next != null ? next : focused;
    }

    /** Whether {@code view} is {@code parent} or one of its descendants. */
    private static boolean inside(View parent, View view) {
        if (view == parent) {
            return true;
        }
        for (ViewParent p = view == null ? null : view.getParent();
                p != null; p = p.getParent()) {
            if (p == parent) {
                return true;
            }
        }
        return false;
    }

    private void add(Screen screen) {
        generation++;
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
            listener.onTopChanged(screen.title);
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
            // Built before the first layout, so there is nothing to animate. The page
            // underneath still has to step aside, or the two would show together.
            incoming.view.setTranslationX(0f);
            if (outgoing != null && bothSettings(incoming, outgoing)) {
                outgoing.view.setAlpha(0f);
                outgoing.view.setVisibility(View.INVISIBLE);
            }
            return;
        }
        incoming.view.setTranslationX(width);
        Motion.translateX(incoming.view, 0f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_PANEL);
        if (outgoing != null) {
            if (bothSettings(incoming, outgoing)) {
                // Both pages are transparent so the wallpaper and the mark
                // can show through. The one underneath steps out of the way
                // instead of sitting there beside the new list.
                Motion.spring(outgoing.view, Motion.ALPHA, 0f, Motion.DAMPING_CRITICAL,
                        Motion.RESPONSE_MOVE, () -> {
                            // BACK pressed mid-fade re-targets this alpha at
                            // 1 but keeps this callback, so it must only hide
                            // a screen that is still covered.
                            if (stack.indexOf(outgoing) != stack.size() - 1) {
                                outgoing.view.setVisibility(View.INVISIBLE);
                            }
                        });
            } else {
                Motion.translateX(outgoing.view, -width * 0.22f, Motion.DAMPING_CRITICAL,
                        Motion.RESPONSE_MOVE);
            }
        }
    }

    private static boolean bothSettings(Screen first, Screen second) {
        return isSettings(first) && isSettings(second);
    }

    private static boolean isSettings(Screen screen) {
        return screen != null && (screen.split || screen.fullBleed);
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
        if (screen.fullBleed && screen.custom != null) {
            FrameLayout page = new FrameLayout(context);
            page.setClipChildren(false);
            page.addView(screen.custom, new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            return page;
        }
        if (screen.split) {
            return buildSplitScreen(screen);
        }

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Ui.dp(context, 64), Ui.dp(context, 36),
                Ui.dp(context, 64), Ui.dp(context, 36));
        // These panels are their own surface: they cover the wallpaper and
        // the settings mark behind the stack.
        column.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                Theme.panel()));

        LinearLayout heading = new LinearLayout(context);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(context);
        title.setText(screen.title);
        title.setTextColor(Theme.ink());
        Type.apply(title, Type.DISPLAY);
        heading.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (screen.subtitle != null && screen.subtitle.length() > 0) {
            TextView subtitle = new TextView(context);
            subtitle.setText(screen.subtitle);
            subtitle.setTextColor(Theme.inkSecondary());
            Type.apply(subtitle, Type.CAPTION);
            subtitle.setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 22));
            column.addView(subtitle, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            title.setPadding(0, 0, 0, Ui.dp(context, 22));
        }

        if (screen.custom != null) {
            if (screen.custom instanceof ScrollView) {
                // A pushed page of prose, like About or the log, holds
                // nothing focusable inside. The scroller itself takes the
                // focus so the D-pad can scroll it; real children still win.
                screen.custom.setFocusable(true);
                ((ScrollView) screen.custom).setDescendantFocusability(
                        ViewGroup.FOCUS_AFTER_DESCENDANTS);
                screen.scroller = (ScrollView) screen.custom;
            }
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

    /**
     * The tvOS settings page: the title over the whole screen, the list on
     * the right. The wallpaper and the app mark behind the stack stay still
     * while this page slides in.
     */
    private View buildSplitScreen(Screen screen) {
        Context context = getContext();
        FrameLayout page = new FrameLayout(context);
        page.setClipChildren(false);
        page.setClipToPadding(false);

        int width = context.getResources().getDisplayMetrics().widthPixels;
        int listStart = Math.round(width * 0.552f);
        int listEnd = Math.round(width * 0.043f);

        TextView title = new TextView(context);
        title.setText(screen.title);
        title.setTextColor(Theme.ink());
        Type.apply(title, Type.DISPLAY);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, PAGE_TITLE_SP);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        titleParams.topMargin = Ui.dp(context, PAGE_TITLE_TOP_DP);
        page.addView(title, titleParams);

        ScrollView scroller = new ScrollView(context);
        scroller.setFocusable(false);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        screen.scroller = scroller;
        screen.list = new LinearLayout(context);
        screen.list.setOrientation(LinearLayout.VERTICAL);
        screen.list.setClipChildren(false);
        screen.list.setClipToPadding(false);
        scroller.addView(screen.list, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams listParams = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
                Gravity.TOP | Gravity.START);
        listParams.setMarginStart(listStart);
        listParams.setMarginEnd(listEnd);
        listParams.topMargin = Math.round(
                context.getResources().getDisplayMetrics().heightPixels * PAGE_LIST_TOP);
        listParams.bottomMargin = Ui.dp(context, 20);
        page.addView(scroller, listParams);

        // tvOS explains a page under the picture on the left, not in the list.
        StringBuilder notes = new StringBuilder();
        for (SettingRow row : screen.rows) {
            if (row.type() == SettingRow.NOTE) {
                notes.append(notes.length() > 0 ? "\n\n" : "").append(row.title());
                continue;
            }
            View view = row.view(context);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            params.bottomMargin = Ui.dp(context, 7);
            screen.list.addView(view, params);
        }
        if (notes.length() > 0) {
            TextView note = new TextView(context);
            note.setText(notes);
            note.setTextColor(Theme.inkSecondary());
            Type.apply(note, Type.CAPTION);
            note.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout.LayoutParams noteParams = new FrameLayout.LayoutParams(
                    Math.round(width * 0.427f), LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.START);
            noteParams.setMarginStart(Math.round(width * 0.06f));
            noteParams.topMargin = Math.round(
                    context.getResources().getDisplayMetrics().heightPixels * PAGE_NOTE_TOP);
            page.addView(note, noteParams);
        }
        return page;
    }
}
