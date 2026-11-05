package com.mitun.sonora.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mitun.sonora.AppEntry;
import com.mitun.sonora.AppSearch;
import com.mitun.sonora.Grid;
import com.mitun.sonora.SearchKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * The search screen, laid out as tvOS lays it out: the page title, what has been
 * typed, one line of keys, and the results below in the same grid as the home screen.
 *
 * The search is local. It looks at the names of the apps on this TV and at nothing
 * else: no network, no history, nothing sent anywhere.
 */
public final class SearchOverlay extends FrameLayout {
    public interface Listener {
        /** The user picked an app. */
        void onPicked(AppEntry entry);

        /** The search is over. */
        void onClosed();
    }

    /** Two screens of cards at most; the note says when more matched. */
    private static final int MAX_ROWS = 4;

    private final BackdropView backdrop;
    private final LinearLayout column;
    private final TextView queryView;
    private final TextView heading;
    private final TextView note;
    private final SearchKeyboard keyboard;
    private final ScrollView scroller;
    private final LinearLayout results;
    private final Listener listener;
    private final List<AppEntry> apps;
    private final List<ResultCard> cards = new ArrayList<>();
    private final int cardWidth;
    private final int cardHeight;
    private final int gap;
    private final int columns;
    private final int maxResults;
    private String query = "";
    private int shown;

    public SearchOverlay(Context context, List<AppEntry> apps, int cardWidth, int cardHeight,
                         int gap, int columns, int marginPx, Bitmap blurred, Listener listener) {
        super(context);
        this.apps = apps;
        this.listener = listener;
        this.cardWidth = cardWidth;
        this.cardHeight = cardHeight;
        this.gap = gap;
        this.columns = Math.max(1, columns);
        maxResults = this.columns * MAX_ROWS;

        backdrop = new BackdropView(context, blurred);
        addView(backdrop, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

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

        TextView title = new TextView(context);
        title.setText("Search");
        title.setTextColor(Theme.ink());
        Type.apply(title, Type.DISPLAY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, ScreenStack.PAGE_TITLE_SP);
        title.setGravity(Gravity.CENTER);
        column.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        queryView = new TextView(context);
        Type.apply(queryView, Type.TITLE);
        queryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f);
        queryView.setGravity(Gravity.CENTER);
        queryView.setSingleLine(true);
        queryView.setEllipsize(TextUtils.TruncateAt.START);
        queryView.setPadding(marginPx, Ui.dp(context, 14), marginPx, Ui.dp(context, 8));
        column.addView(queryView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        keyboard = new SearchKeyboard(context, hasAssistant(context), new SearchKeyboard.Listener() {
            @Override
            public void onKey(String key) {
                onKeyPressed(key);
            }

            @Override
            public void onLeaveDown() {
                focusResult(0);
            }
        });
        column.addView(keyboard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 44)));

        heading = new TextView(context);
        heading.setTextColor(Theme.inkSecondary());
        Type.apply(heading, Type.HEADING);
        heading.setPadding(marginPx, Ui.dp(context, 20), marginPx, Ui.dp(context, 14));
        column.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroller = new ScrollView(context);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setFocusable(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        // A focused card grows past its row, so the list leaves room for it.
        scroller.setPadding(marginPx, Ui.dp(context, 8), marginPx, Ui.dp(context, 28));
        results = new LinearLayout(context);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setClipChildren(false);
        results.setClipToPadding(false);
        scroller.addView(results, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        note = new TextView(context);
        note.setTextColor(Theme.inkSecondary());
        Type.apply(note, Type.BODY);
        note.setGravity(Gravity.CENTER);
        note.setVisibility(GONE);
        LayoutParams noteParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        noteParams.bottomMargin = Ui.dp(context, 16);

        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(note, noteParams);

        showResults();

        backdrop.setAlpha(0f);
        column.setAlpha(0f);
        column.setTranslationY(Ui.dp(context, 14));
        Motion.spring(backdrop, Motion.ALPHA, 1f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_PANEL);
        Motion.spring(column, Motion.ALPHA, 1f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_PANEL);
        Motion.spring(column, Motion.TRANSLATION_Y, 0f, Motion.DAMPING_CRITICAL,
                Motion.RESPONSE_PANEL);
    }

    /** Puts the focus on the keyboard. */
    public void start() {
        keyboard.post(keyboard::requestFocus);
    }

    /** Takes the screen away, and runs the work once it has gone. */
    public void dismiss(Runnable after) {
        Motion.spring(backdrop, Motion.ALPHA, 0f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_PANEL);
        Motion.spring(column, Motion.ALPHA, 0f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_PANEL,
                after);
        Motion.spring(column, Motion.TRANSLATION_Y, Ui.dp(getContext(), 14),
                Motion.DAMPING_CRITICAL, Motion.RESPONSE_PANEL);
    }

    private void onKeyPressed(String key) {
        if (SearchKeys.VOICE.equals(key)) {
            openAssistant();
            return;
        }
        String next = SearchKeys.apply(query, key);
        if (!next.equals(query)) {
            query = next;
            showResults();
        }
    }

    /**
     * Asks the TV's own assistant to search. This is the intent Google TV's search
     * widget sends. The key only exists on a TV where something answers it.
     */
    private void openAssistant() {
        Intent assist = new Intent(Intent.ACTION_ASSIST);
        assist.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        assist.putExtra("search_type", 1);
        try {
            getContext().startActivity(assist);
        } catch (RuntimeException e) {
            Ui.toast(getContext(), "Voice search is not available on this TV");
        }
    }

    private static boolean hasAssistant(Context context) {
        try {
            return new Intent(Intent.ACTION_ASSIST)
                    .resolveActivity(context.getPackageManager()) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void showResults() {
        boolean empty = query.trim().isEmpty();
        queryView.setText(query.isEmpty() ? "Search apps" : query);
        queryView.setTextColor(query.isEmpty() ? Theme.inkTertiary() : Theme.ink());

        List<AppEntry> matches = AppSearch.filter(apps, query);
        int focusedIndex = focusedCard();
        String focusedName = focusedIndex >= 0 ? cards.get(focusedIndex).entry().packageName : null;

        shown = Math.min(matches.size(), maxResults);
        ensureCards(shown);
        for (int i = 0; i < cards.size(); i++) {
            ResultCard card = cards.get(i);
            if (i < shown) {
                card.bind(matches.get(i));
                card.setVisibility(VISIBLE);
            } else {
                card.setVisibility(INVISIBLE);
            }
        }
        for (int row = 0; row < results.getChildCount(); row++) {
            results.getChildAt(row).setVisibility(row * columns < shown ? VISIBLE : GONE);
        }

        heading.setText(empty ? "All Apps" : "Apps");
        heading.setVisibility(shown > 0 ? VISIBLE : INVISIBLE);
        if (shown == 0) {
            // A TV with no apps is not the same as a search that found nothing.
            note.setText(empty ? "There are no apps on this TV to search."
                    : "No apps match “" + query.trim() + "”");
            note.setVisibility(VISIBLE);
        } else if (matches.size() > shown) {
            note.setText("Showing " + shown + " of " + matches.size() + ". Type more to narrow it.");
            note.setVisibility(VISIBLE);
        } else {
            note.setVisibility(GONE);
        }

        if (focusedName != null) {
            int keep = AppSearch.focusedIndex(focusedName, matches);
            if (keep >= 0 && keep < shown) {
                cards.get(keep).requestFocus();
            } else {
                keyboard.requestFocus();
            }
        }
    }

    /** Cards are made as they are first needed, then re-bound as the query changes. */
    private void ensureCards(int count) {
        Context context = getContext();
        while (cards.size() < count) {
            int index = cards.size();
            LinearLayout row;
            if (index % columns == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.bottomMargin = Ui.dp(context, 20);
                results.addView(row, rowParams);
            } else {
                row = (LinearLayout) results.getChildAt(results.getChildCount() - 1);
            }
            ResultCard card = new ResultCard(context, cardWidth, cardHeight, entry -> {
                if (listener != null) {
                    listener.onPicked(entry);
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(cardWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(gap);
            row.addView(card, params);
            cards.add(card);
        }
    }

    private int focusedCard() {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).isFocused()) {
                return i;
            }
        }
        return -1;
    }

    private void focusResult(int index) {
        if (index >= 0 && index < shown) {
            cards.get(index).requestFocus();
        }
    }

    /**
     * Moves through the results here, so the D-pad cannot walk out onto the home
     * screen behind, and takes characters from a real keyboard if one is plugged in.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_DEL) {
            onKeyPressed(SearchKeys.DELETE);
            return true;
        }
        if (code == KeyEvent.KEYCODE_SPACE) {
            onKeyPressed(SearchKeys.SPACE);
            return true;
        }
        int unicode = event.getUnicodeChar();
        if (unicode > 0 && Character.isLetterOrDigit(unicode)) {
            onKeyPressed(new String(Character.toChars(Character.toLowerCase(unicode))));
            return true;
        }
        int focused = focusedCard();
        if (focused >= 0 && (code == KeyEvent.KEYCODE_DPAD_LEFT
                || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_UP
                || code == KeyEvent.KEYCODE_DPAD_DOWN)) {
            int target = Grid.moveTarget(focused, columns, shown, code);
            if (target >= 0) {
                focusResult(target);
            } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                keyboard.requestFocus();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {
        Motion.cancel(backdrop);
        Motion.cancel(column);
        super.onDetachedFromWindow();
    }

    /** One result: the app's card with its name under it. */
    private static final class ResultCard extends LinearLayout {
        private static final float FOCUS_SCALE = 1.10f;
        /** The focused card yields under OK, then springs back. */
        private static final float PRESS_SCALE = 0.95f;

        private final AppIconView icon;
        private final TextView label;
        private AppEntry entry;

        ResultCard(Context context, int width, int height, OnPick pick) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setFocusable(true);
            setFocusableInTouchMode(false);
            setClickable(true);
            setClipChildren(false);
            setClipToPadding(false);

            icon = new AppIconView(context);
            addView(icon, new LayoutParams(width, height));

            label = new TextView(context);
            label.setTextColor(Theme.inkSecondary());
            Type.apply(label, Type.CAPTION);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LayoutParams labelParams = new LayoutParams(width, LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = Ui.dp(context, 10);
            addView(label, labelParams);

            setOnFocusChangeListener((v, focused) -> {
                setZ(focused ? Ui.dpf(context, 8f) : 0f);
                icon.setFocused(focused);
                Motion.scale(icon, focused ? FOCUS_SCALE : 1f, Motion.DAMPING_CRITICAL,
                        Motion.RESPONSE_FOCUS);
                label.setTextColor(focused ? Theme.ink() : Theme.inkSecondary());
            });
            setOnClickListener(v -> pick.onPick(entry));
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            float base = isFocused() ? FOCUS_SCALE : 1f;
            Motion.scale(icon, pressed ? base * PRESS_SCALE : base,
                    Motion.DAMPING_CRITICAL, Motion.RESPONSE_PRESS);
        }

        void bind(AppEntry entry) {
            this.entry = entry;
            icon.setEntry(entry);
            label.setText(entry.label);
            setContentDescription(entry.label);
        }

        AppEntry entry() {
            return entry;
        }

        @Override
        protected void onDetachedFromWindow() {
            Motion.cancel(icon);
            super.onDetachedFromWindow();
        }
    }

    /** Picks a result. */
    private interface OnPick {
        void onPick(AppEntry entry);
    }
}
