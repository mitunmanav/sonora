package com.mitun.sonora.tools;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mitun.sonora.ui.Motion;
import com.mitun.sonora.ui.PressRow;
import com.mitun.sonora.ui.RowDrawable;
import com.mitun.sonora.ui.ScreenStack;
import com.mitun.sonora.ui.Theme;
import com.mitun.sonora.ui.Type;
import com.mitun.sonora.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings index, laid out the way tvOS Settings is: the page name above,
 * the list on the right, and the app mark on the left. The mark is drawn by
 * the activity behind this view, so it stays still while pages slide past it.
 */
final class SettingsHomeView extends FrameLayout {
    static final class Detail {
        final String label;
        final String value;

        Detail(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    static final class Category {
        final String title;
        final String summary;
        final Detail[] details;
        final Runnable action;

        Category(String title, String summary, Detail detail, Runnable action) {
            this(title, summary, new Detail[]{detail}, action);
        }

        Category(String title, String summary, Detail first, Detail second,
                 Runnable action) {
            this(title, summary, new Detail[]{first, second}, action);
        }

        Category(String title, String summary, Detail first, Detail second, Detail third,
                 Runnable action) {
            this(title, summary, new Detail[]{first, second, third}, action);
        }

        Category(String title, String summary, Detail[] details, Runnable action) {
            this.title = title;
            this.summary = summary;
            this.details = details;
            this.action = action;
        }
    }

    private final List<Category> categories;
    private final List<View> navRows = new ArrayList<>();
    private int selectedIndex;

    SettingsHomeView(Context context, List<Category> categories, int initialSelection) {
        super(context);
        this.categories = categories;
        selectedIndex = clamp(initialSelection, categories.size());
        setFocusable(true);
        setFocusableInTouchMode(false);
        setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);
        setOnFocusChangeListener((view, focused) -> {
            if (focused && !navRows.isEmpty()) {
                navRows.get(selectedIndex).requestFocus();
            }
        });

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int listStart = Math.round(screenWidth * 0.552f);
        int listEnd = Math.round(screenWidth * 0.043f);

        TextView title = new TextView(context);
        title.setText("Settings");
        title.setTextColor(Theme.ink());
        Type.apply(title, Type.DISPLAY);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, ScreenStack.PAGE_TITLE_SP);
        title.setGravity(Gravity.CENTER);
        LayoutParams titleParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        titleParams.topMargin = Ui.dp(context, ScreenStack.PAGE_TITLE_TOP_DP);
        addView(title, titleParams);

        ScrollView navScroll = new ScrollView(context);
        navScroll.setFocusable(false);
        navScroll.setVerticalScrollBarEnabled(false);
        navScroll.setClipChildren(false);
        navScroll.setClipToPadding(false);
        LinearLayout nav = new LinearLayout(context);
        nav.setOrientation(LinearLayout.VERTICAL);
        nav.setClipChildren(false);
        nav.setClipToPadding(false);
        navScroll.addView(nav, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LayoutParams navParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.TOP | Gravity.START);
        navParams.setMarginStart(listStart);
        navParams.setMarginEnd(listEnd);
        navParams.topMargin = Math.round(context.getResources().getDisplayMetrics().heightPixels
                * ScreenStack.PAGE_LIST_TOP);
        navParams.bottomMargin = Ui.dp(context, 20);
        addView(navScroll, navParams);

        for (int i = 0; i < categories.size(); i++) {
            View row = buildRow(context, categories.get(i), i);
            navRows.add(row);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = Ui.dp(context, 7);
            nav.addView(row, rowParams);
        }

        if (navRows.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("No settings available");
            empty.setTextColor(Theme.inkSecondary());
            Type.apply(empty, Type.BODY);
            nav.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            for (int i = 0; i < navRows.size(); i++) {
                View row = navRows.get(i);
                row.setNextFocusLeftId(row.getId());
                row.setNextFocusRightId(row.getId());
                row.setNextFocusUpId(navRows.get(Math.max(0, i - 1)).getId());
                row.setNextFocusDownId(navRows.get(Math.min(navRows.size() - 1, i + 1)).getId());
            }
        }
    }

    private View buildRow(Context context, Category category, int index) {
        PressRow row = new PressRow(context, 1.02f);
        row.setId(View.generateViewId());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0);
        row.setMinimumHeight(Ui.dp(context, 33));
        row.setFocusable(true);
        row.setFocusableInTouchMode(false);
        row.setClickable(true);
        RowDrawable material = new RowDrawable(context);
        row.setBackground(material);

        TextView label = new TextView(context);
        label.setText(category.title);
        label.setTextColor(Theme.inkSoft());
        Type.apply(label, Type.HEADING);
        label.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        boolean rtl = context.getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL;
        TextView chevron = new TextView(context);
        chevron.setText(rtl ? "‹" : "›");
        chevron.setTextColor(Theme.inkTertiary());
        chevron.setTextSize(22f);
        chevron.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chevronParams.setMarginStart(Ui.dp(context, 10));
        row.addView(chevron, chevronParams);

        StringBuilder description = new StringBuilder(category.title)
                .append(". ").append(category.summary);
        for (Detail detail : category.details) {
            description.append(". ").append(detail.label).append(": ").append(detail.value);
        }
        row.setContentDescription(description.toString());

        final float[] focus = {0f};
        row.setOnFocusChangeListener((view, focused) -> {
            selectedIndex = index;
            RowDrawable bg = (RowDrawable) view.getBackground();
            Motion.springCustom(view, 0, focused ? 1f : 0f, Motion.DAMPING_CRITICAL,
                    Motion.RESPONSE_FOCUS, new Motion.FloatProperty() {
                        @Override
                        public float get() {
                            return focus[0];
                        }

                        @Override
                        public void set(float value) {
                            focus[0] = value;
                            bg.setFocus(value);
                            label.setTextColor(Ui.lerpColor(Theme.inkSoft(), Theme.INK_ON_FOCUS, value));
                            chevron.setTextColor(Ui.lerpColor(Theme.inkTertiary(), 0x99101C26, value));
                        }
                    });
            view.setElevation(focused ? Ui.dpf(context, 8f) : 0f);
        });
        row.setOnClickListener(view -> category.action.run());
        return row;
    }

    /** The category the user is on, so a rebuilt screen can keep it. */
    int selectedCategory() {
        return selectedIndex;
    }

    private static int clamp(int index, int size) {
        if (size <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(size - 1, index));
    }
}
