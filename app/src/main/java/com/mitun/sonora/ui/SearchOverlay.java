package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mitun.sonora.AppEntry;
import com.mitun.sonora.AppSearch;

import java.util.ArrayList;
import java.util.List;

/**
 * The search screen: a field at the top, the matching apps under it.
 *
 * The search is local. It looks at the names of the apps on this TV and at
 * nothing else: no network, no history, nothing sent anywhere.
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
    private final Listener listener;
    private final List<AppEntry> apps;
    private final int cardWidth;
    private final int cardHeight;

    public SearchOverlay(Context context, List<AppEntry> apps, int cardWidth, int cardHeight,
                         Listener listener) {
        super(context);
        this.apps = apps;
        this.cardWidth = cardWidth;
        this.cardHeight = cardHeight;
        this.listener = listener;

        // A modal task pushes the screen behind it back.
        setBackgroundColor(0xF7000000);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Ui.dp(context, 60), Ui.dp(context, 60), Ui.dp(context, 60),
                Ui.dp(context, 40));

        // The field: a glass pill with a magnifying glass in it.
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackground(new GlassDrawable(context, 26f, 1f));
        pill.setPadding(Ui.dp(context, 20), Ui.dp(context, 8), Ui.dp(context, 20), Ui.dp(context, 8));

        ImageView glyph = new ImageView(context);
        Drawable magnifier = context.getDrawable(android.R.drawable.ic_menu_search);
        if (magnifier != null) {
            magnifier = magnifier.mutate();
            magnifier.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
            glyph.setImageDrawable(magnifier);
        }
        int glyphSize = Ui.dp(context, 20);
        LinearLayout.LayoutParams glyphParams = new LinearLayout.LayoutParams(glyphSize, glyphSize);
        glyphParams.gravity = Gravity.CENTER_VERTICAL;
        glyphParams.rightMargin = Ui.dp(context, 14);
        pill.addView(glyph, glyphParams);

        field = new EditText(context);
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(0x99FFFFFF);
        Type.apply(field, Type.HEADING);
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

        column.addView(pill, new LinearLayout.LayoutParams(
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

        showResults("");
    }

    /** Shows the field, and the keyboard with it. */
    public void start() {
        field.post(() -> {
            field.requestFocus();
            InputMethodManager manager = (InputMethodManager)
                    getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) {
                manager.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    /** Puts the keyboard away. */
    public void close() {
        InputMethodManager manager = (InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(field.getWindowToken(), 0);
        }
        field.clearFocus();
    }

    private void showResults(String query) {
        results.removeAllViews();
        int shown = 0;
        for (AppEntry entry : AppSearch.filter(apps, query)) {
            if (shown++ >= MAX_RESULTS) {
                break;
            }
            results.addView(resultCard(entry), new LinearLayout.LayoutParams(
                    cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    /** One result: the app's card with its name under it. */
    private View resultCard(final AppEntry entry) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setFocusable(true);
        card.setFocusableInTouchMode(false);
        card.setClickable(true);
        card.setContentDescription(entry.label);

        AppIconView icon = new AppIconView(getContext());
        icon.setEntry(entry);
        card.addView(icon, new LinearLayout.LayoutParams(cardWidth, cardHeight));

        TextView label = new TextView(getContext());
        label.setText(entry.label);
        label.setTextColor(Color.WHITE);
        label.setAlpha(0.82f);
        Type.apply(label, Type.CAPTION);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(label, new LinearLayout.LayoutParams(cardWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        card.setOnFocusChangeListener((v, focused) -> {
            if (focused) {
                Motion.scale(icon, 1.10f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_FOCUS);
                Motion.spring(label, Motion.ALPHA, 1f, Motion.DAMPING_CRITICAL,
                        Motion.RESPONSE_FOCUS);
            } else {
                Motion.scale(icon, 1f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_FOCUS);
                Motion.spring(label, Motion.ALPHA, 0.82f, Motion.DAMPING_CRITICAL,
                        Motion.RESPONSE_FOCUS);
            }
        });
        card.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPicked(entry);
            }
        });
        return card;
    }

    private void focusFirstResult() {
        if (results.getChildCount() == 0) {
            return;
        }
        View first = results.getChildAt(0);
        first.requestFocus();
        scrollTo(first);
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
                if (!field.isFocused()) {
                    field.requestFocus();
                    return true;
                }
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (!field.isFocused()) {
                    return true;
                }
                focusFirstResult();
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
        if (target < 0 || target >= results.getChildCount()) {
            return true;
        }
        View next = results.getChildAt(target);
        next.requestFocus();
        scrollTo(next);
        return true;
    }

    private void scrollTo(View child) {
        resultScroller.requestChildRectangleOnScreen(child,
                new Rect(0, 0, child.getWidth(), child.getHeight()), true);
    }
}
