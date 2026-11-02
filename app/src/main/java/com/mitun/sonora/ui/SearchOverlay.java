package com.mitun.sonora.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mitun.sonora.AppEntry;
import com.mitun.sonora.AppSearch;

import java.util.List;

/**
 * The search screen: a field at the top, the matching apps under it.
 *
 * The search is local. It looks at the names of the apps on this TV and at
 * nothing else: no network, no history, nothing sent anywhere.
 *
 * The shape is tvOS's: the field sits at the top and stays there, the text in
 * it is large enough to read from the sofa, and the results are the same
 * cards the home screen shows, so the thing you find here is the thing you
 * saw there.
 */
public final class SearchOverlay extends FrameLayout {
    public interface Listener {
        /** The user picked an app. */
        void onPicked(AppEntry entry);

        /** The search is over. */
        void onClosed();
    }

    /** How many results are shown at once. */
    private static final int MAX_RESULTS = 12;

    private final EditText field;
    private final LinearLayout results;
    private final HorizontalScrollView resultScroller;
    private final ResultCard[] cards;
    private final TextView note;
    private final LinearLayout column;
    private final GlassIconPill mic;
    private final Listener listener;
    private final List<AppEntry> apps;
    private final boolean noKeyboard;

    public SearchOverlay(Context context, List<AppEntry> apps, int cardWidth, int cardHeight,
                         int marginPx, Listener listener) {
        super(context);
        this.apps = apps;
        this.listener = listener;

        // A modal task pushes the screen behind it back.
        setBackgroundColor(0xF7000000);

        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setPadding(marginPx, Ui.dp(context, 34), marginPx, Ui.dp(context, 40));

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setClipChildren(false);

        // The field: a glass pill with the magnifying glass that means search
        // on the left, the way tvOS puts it. The pill catches the light under
        // focus, so it reads as a button you can press.
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setClipChildren(false);
        final GlassDrawable pillMaterial = new GlassDrawable(context, 26f, 1f);
        pill.setBackground(pillMaterial);
        pill.setPadding(Ui.dp(context, 20), Ui.dp(context, 6), Ui.dp(context, 20), Ui.dp(context, 6));
        pill.setFocusable(true);
        pill.setFocusableInTouchMode(false);
        // The glass catches the light under focus, so the field reads as a
        // button you can press. The tint of the mark and the hint follow the
        // glass once both exist (wired below after they are created).
        pill.setOnFocusChangeListener((v, focused) -> {
            pillMaterial.setAlpha(focused ? 255 : 0);
            pillMaterial.setFocus(focused ? 1f : 0f);
            v.setElevation(focused ? Ui.dpf(context, 8f) : 0f);
        });

        ImageView glyph = new ImageView(context);
        glyph.setImageBitmap(Ui.magnifier(context, Ui.dp(context, 22)));
        int glyphSize = Ui.dp(context, 22);
        LinearLayout.LayoutParams glyphParams = new LinearLayout.LayoutParams(glyphSize, glyphSize);
        glyphParams.gravity = Gravity.CENTER_VERTICAL;
        glyphParams.setMarginEnd(Ui.dp(context, 14));
        pill.addView(glyph, glyphParams);

        field = new EditText(context);
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(0x8CFFFFFF);
        // tvOS made the text in the search field bigger so that you can see
        // what you typed from the sofa.
        Type.apply(field, Type.TITLE);
        field.setSingleLine(true);
        field.setBackground(null);
        field.setHint("Search apps");
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setOnEditorActionListener((v, actionId, event) -> {
            // The keyboard's search key hands the focus to the results.
            focusFirstResult();
            return true;
        });
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                showResults(s.toString());
            }
        });
        pill.addView(field, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        // The glass and the ink materialize together under focus: the pill
        // alpha, rim light, the mark tint and the hint all spring, so a fast
        // D-pad move never snaps the field into view.
        final float[] pillFocus = {0f};
        final ImageView pillGlyph = glyph;
        final TextView pillField = field;
        pill.setOnFocusChangeListener((v, focused) -> {
            Motion.springCustom(v, 0, focused ? 1f : 0f, Motion.DAMPING_CRITICAL,
                    Motion.RESPONSE_FOCUS, new Motion.FloatProperty() {
                        @Override
                        public float get() {
                            return pillFocus[0];
                        }

                        @Override
                        public void set(float value) {
                            pillFocus[0] = value;
                            pillMaterial.setAlpha(Math.round(255f * value));
                            pillMaterial.setFocus(value);
                            int ink = Ui.lerpColor(0x99F5F6F8, 0xFF0A0D12, value);
                            pillGlyph.setColorFilter(new PorterDuffColorFilter(ink, PorterDuff.Mode.SRC_IN));
                            pillField.setHintTextColor(Ui.lerpColor(0x8CFFFFFF, 0xCC101C26, value));
                        }
                    });
            v.setElevation(focused ? Ui.dpf(context, 8f) : 0f);
        });
        top.addView(pill, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // The microphone: the TV's own assistant, asked to search out loud.
        mic = new GlassIconPill(context, Ui.mic(context, Ui.dp(context, 22)), "Search by voice");
        mic.setOnOpen(this::openAssistant);
        LinearLayout.LayoutParams micParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        micParams.setMarginStart(Ui.dp(context, 12));
        top.addView(mic, micParams);
        column.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        note = new TextView(context);
        note.setTextColor(0xB3FFFFFF);
        Type.apply(note, Type.CAPTION);
        note.setVisibility(GONE);
        note.setPadding(Ui.dp(context, 4), Ui.dp(context, 10), Ui.dp(context, 4), 0);
        column.addView(note, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        resultScroller = new HorizontalScrollView(context);
        resultScroller.setHorizontalScrollBarEnabled(false);
        resultScroller.setClipChildren(false);
        resultScroller.setPadding(0, Ui.dp(context, 18), 0, 0);
        results = new LinearLayout(context);
        results.setOrientation(LinearLayout.HORIZONTAL);
        results.setClipChildren(false);
        resultScroller.addView(results, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(resultScroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // The cards are made once and re-bound as the query changes. Building
        // them again on every keystroke would throw away a dozen views a
        // letter, and this screen is used on a TV with little to spare.
        cards = new ResultCard[MAX_RESULTS];
        for (int i = 0; i < MAX_RESULTS; i++) {
            ResultCard card = new ResultCard(context, cardWidth, cardHeight, entry -> {
                if (listener != null) {
                    listener.onPicked(entry);
                }
            });
            cards[i] = card;
            results.addView(card, new LinearLayout.LayoutParams(
                    cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        noKeyboard = !hasKeyboard();
        showResults("");

        // The screen arrives: it grows into place rather than appearing all at
        // once, and it leaves the same way.
        column.setPivotX(0f);
        column.setPivotY(0f);
        column.setAlpha(0f);
        column.setTranslationY(Ui.dp(context, 18));
        column.setScaleX(0.97f);
        column.setScaleY(0.97f);
        Motion.spring(column, Motion.ALPHA, 1f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_PANEL);
        Motion.spring(column, Motion.TRANSLATION_Y, 0f, Motion.DAMPING_CRITICAL,
                Motion.RESPONSE_PANEL);
        Motion.spring(column, Motion.SCALE, 1f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_PANEL);
    }

    /** Shows the field, and the keyboard with it. */
    public void start() {
        field.post(() -> {
            if (noKeyboard) {
                // No keyboard on this TV. Say so, and let the remote do the
                // work, rather than leaving a field that swallows it.
                focusFirstResult();
                return;
            }
            focusField();
        });
    }

    /** Puts the keyboard away. */
    public void close() {
        InputMethodManager manager = manager();
        if (manager != null) {
            manager.hideSoftInputFromWindow(field.getWindowToken(), 0);
        }
        field.clearFocus();
    }

    /**
     * Takes the screen away, then runs the work that was asked for.
     *
     * It leaves the way it came, so the eye can follow it back.
     */
    public void dismiss(Runnable after) {
        close();
        Motion.spring(column, Motion.ALPHA, 0f, Motion.DAMPING_CRITICAL,
                Motion.RESPONSE_PANEL, after);
        Motion.spring(column, Motion.TRANSLATION_Y, Ui.dp(getContext(), 18),
                Motion.DAMPING_CRITICAL, Motion.RESPONSE_PANEL);
        Motion.spring(column, Motion.SCALE, 0.97f, Motion.DAMPING_CRITICAL,
                Motion.RESPONSE_PANEL);
    }

    /**
     * Asks the TV's own assistant to search.
     *
     * This is the intent Google TV's search widget sends: the action is
     * ASSIST, and the extra says which way the person means to search. When
     * this TV has no assistant at all, the keyboard takes over instead of the
     * screen giving up with a message.
     */
    private void openAssistant() {
        Intent assist = new Intent(Intent.ACTION_ASSIST);
        assist.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        assist.putExtra("search_type", 1);
        try {
            getContext().startActivity(assist);
        } catch (RuntimeException e) {
            focusField();
            raiseKeyboard();
        }
    }

    private void showResults(String query) {
        List<AppEntry> matches = AppSearch.filter(apps, query);

        // The card that had the focus, so it can keep it if it is still here.
        String focused = null;
        for (ResultCard card : cards) {
            if (card.isFocused() && card.entry() != null) {
                focused = card.entry().packageName;
                break;
            }
        }

        int shown = Math.min(matches.size(), cards.length);
        for (int i = 0; i < cards.length; i++) {
            if (i < shown) {
                cards[i].bind(matches.get(i));
                cards[i].setVisibility(VISIBLE);
            } else {
                cards[i].setVisibility(GONE);
            }
        }

        if (shown == 0) {
            // Nothing to show, and the reason why: a TV with no apps at all
            // is not the same as a search that found nothing.
            note.setText(query.trim().isEmpty()
                    ? "There are no apps on this TV to search."
                    : "No apps match \"" + query.trim() + "\"");
            note.setVisibility(VISIBLE);
        } else if (noKeyboard) {
            note.setText("This TV has no keyboard. Use the remote to browse the apps.");
            note.setVisibility(VISIBLE);
        } else if (matches.size() > shown) {
            // The results are capped, so the user is told what is not there.
            note.setText("Showing " + shown + " of " + matches.size() + " matches");
            note.setVisibility(VISIBLE);
        } else {
            note.setVisibility(GONE);
        }

        if (focused != null) {
            int keep = AppSearch.focusedIndex(focused, matches);
            if (keep >= 0 && keep < shown) {
                cards[keep].requestFocus();
                scrollTo(cards[keep]);
                return;
            }
            focusFirstResult();
        }
    }

    private void focusField() {
        field.requestFocus();
        if (!noKeyboard) {
            raiseKeyboard();
        }
    }

    private void raiseKeyboard() {
        InputMethodManager manager = manager();
        if (manager != null) {
            manager.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private InputMethodManager manager() {
        return (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    /** True when this TV has a keyboard it can show. */
    private boolean hasKeyboard() {
        InputMethodManager manager = manager();
        if (manager == null) {
            return false;
        }
        List<InputMethodInfo> list = manager.getEnabledInputMethodList();
        return list != null && !list.isEmpty();
    }

    private void focusFirstResult() {
        for (ResultCard card : cards) {
            if (card.getVisibility() == VISIBLE) {
                card.requestFocus();
                scrollTo(card);
                return;
            }
        }
    }

    /** Keeps the D-pad inside the search instead of walking into the grid. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (moveResult(code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)) {
                    return true;
                }
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP) {
                // Up always comes back to the field, unless there is no
                // keyboard to type in - then it would be a dead end.
                if (noKeyboard) {
                    focusFirstResult();
                } else {
                    focusField();
                }
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (field.isFocused() || mic.isFocused()) {
                    focusFirstResult();
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /** Moves along the row of results. Returns true when it had the focus. */
    private boolean moveResult(int direction) {
        // getFocusedChild, not findFocus: the list holds the focus, and the
        // card inside it is the one that is focused.
        View focused = results.getFocusedChild();
        int index = results.indexOfChild(focused);
        if (index < 0) {
            return false;
        }
        int target = index + direction;
        while (target >= 0 && target < cards.length) {
            if (cards[target].getVisibility() == VISIBLE) {
                cards[target].requestFocus();
                scrollTo(cards[target]);
                return true;
            }
            target += direction;
        }
        return true;
    }

    private void scrollTo(View child) {
        resultScroller.requestChildRectangleOnScreen(child,
                new Rect(0, 0, child.getWidth(), child.getHeight()), true);
    }

    /** One result: the app's card with its name under it. */
    private static final class ResultCard extends LinearLayout {
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

            icon = new AppIconView(context);
            addView(icon, new LayoutParams(width, height));

            label = new TextView(context);
            label.setTextColor(Color.WHITE);
            label.setAlpha(0.82f);
            Type.apply(label, Type.CAPTION);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            addView(label, new LayoutParams(width, LayoutParams.WRAP_CONTENT));

            setOnFocusChangeListener((v, focused) -> {
                Motion.scale(icon, focused ? 1.10f : 1f, Motion.DAMPING_CRITICAL,
                        Motion.RESPONSE_FOCUS);
                Motion.spring(label, Motion.ALPHA, focused ? 1f : 0.82f,
                        Motion.DAMPING_CRITICAL, Motion.RESPONSE_FOCUS);
            });
            setOnClickListener(v -> pick.onPick(entry));
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
    }

    /** Picks a result. */
    private interface OnPick {
        void onPick(AppEntry entry);
    }
}
