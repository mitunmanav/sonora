package com.mitun.sonora.tools;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mitun.sonora.R;
import com.mitun.sonora.ui.GlassDrawable;
import com.mitun.sonora.ui.Motion;
import com.mitun.sonora.ui.Type;
import com.mitun.sonora.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/** The settings index: a remote-driven list beside Sonora's settings identity. */
final class SettingsHomeView extends LinearLayout {
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

    private static final int TEXT = 0xFFF4F8FB;
    private static final int MUTED = 0xFFADBBC5;
    private static final int INK = 0xFF101C26;
    private static final int ACCENT = 0xFF8FD9F3;

    private final List<Category> categories;
    private final List<View> navRows = new ArrayList<>();
    private final TextView selectedSummary;
    private final TextView selectedDetails;
    private final boolean rtl;
    private int selectedIndex;

    SettingsHomeView(Context context, List<Category> categories, int initialSelection) {
        super(context);
        this.categories = categories;
        selectedIndex = clamp(initialSelection, categories.size());
        rtl = context.getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL;
        setOrientation(HORIZONTAL);
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

        LinearLayout brand = new LinearLayout(context);
        brand.setOrientation(VERTICAL);
        brand.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        brand.setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 28), 0);
        brand.setClipChildren(false);
        brand.setClipToPadding(false);

        ImageView mark = new ImageView(context);
        mark.setImageResource(R.drawable.sonora_mark);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        float density = context.getResources().getDisplayMetrics().density;
        int screenWidthDp = Math.round(context.getResources().getDisplayMetrics().widthPixels
                / density);
        int markSize = Math.min(168, Math.max(116, Math.round(screenWidthDp * 0.17f)));
        brand.addView(mark, new LinearLayout.LayoutParams(
                Ui.dp(context, markSize), Ui.dp(context, markSize)));

        TextView wordmark = new TextView(context);
        wordmark.setText("Sonora");
        wordmark.setTextColor(TEXT);
        Type.apply(wordmark, Type.TITLE);
        LinearLayout.LayoutParams wordmarkParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wordmarkParams.topMargin = Ui.dp(context, 12);
        brand.addView(wordmark, wordmarkParams);

        TextView section = new TextView(context);
        section.setText("SETTINGS");
        section.setTextColor(ACCENT);
        Type.apply(section, Type.MICRO);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sectionParams.topMargin = Ui.dp(context, 5);
        brand.addView(section, sectionParams);

        View separator = new View(context);
        separator.setBackgroundColor(0x334B6473);
        LinearLayout.LayoutParams separatorParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 54), Ui.dp(context, 1));
        separatorParams.topMargin = Ui.dp(context, 28);
        separatorParams.bottomMargin = Ui.dp(context, 20);
        brand.addView(separator, separatorParams);

        selectedSummary = new TextView(context);
        selectedSummary.setTextColor(0xFFE1EAF0);
        Type.apply(selectedSummary, Type.BODY);
        selectedSummary.setMaxLines(3);
        selectedSummary.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        brand.addView(selectedSummary, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        selectedDetails = new TextView(context);
        selectedDetails.setTextColor(MUTED);
        Type.apply(selectedDetails, Type.CAPTION);
        selectedDetails.setMaxLines(2);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailsParams.topMargin = Ui.dp(context, 14);
        brand.addView(selectedDetails, detailsParams);

        ScrollView navScroll = new ScrollView(context);
        navScroll.setFocusable(false);
        navScroll.setVerticalScrollBarEnabled(false);
        navScroll.setFillViewport(true);
        navScroll.setClipChildren(false);
        navScroll.setClipToPadding(false);
        LinearLayout nav = new LinearLayout(context);
        nav.setOrientation(VERTICAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setClipChildren(false);
        nav.setClipToPadding(false);
        navScroll.addView(nav, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View spacer = new View(context);
        LayoutParams spacerParams = new LayoutParams(0, 1, 0.055f);

        LayoutParams brandParams = new LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 0.425f);
        LayoutParams navParams = new LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 0.52f);
        if (rtl) {
            addView(navScroll, navParams);
            addView(spacer, spacerParams);
            addView(brand, brandParams);
        } else {
            addView(brand, brandParams);
            addView(spacer, spacerParams);
            addView(navScroll, navParams);
        }

        for (int i = 0; i < categories.size(); i++) {
            if (i == categories.size() - 1 && i > 0) {
                View rule = new View(context);
                rule.setBackgroundColor(0x263E5666);
                LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 1));
                ruleParams.setMargins(Ui.dp(context, 20), Ui.dp(context, 10),
                        Ui.dp(context, 20), Ui.dp(context, 10));
                nav.addView(rule, ruleParams);
            }

            View row = buildRow(context, categories.get(i), i);
            navRows.add(row);
            nav.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        if (navRows.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("No settings available");
            empty.setTextColor(MUTED);
            Type.apply(empty, Type.BODY);
            nav.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            selectedSummary.setText("There are no settings to show.");
            selectedDetails.setText("");
        } else {
            for (int i = 0; i < navRows.size(); i++) {
                View row = navRows.get(i);
                row.setNextFocusLeftId(row.getId());
                row.setNextFocusRightId(row.getId());
                row.setNextFocusUpId(navRows.get(Math.max(0, i - 1)).getId());
                row.setNextFocusDownId(navRows.get(Math.min(navRows.size() - 1, i + 1)).getId());
            }
            showCategory(selectedIndex);
        }
    }

    private View buildRow(Context context, Category category, int index) {
        LinearLayout row = new LinearLayout(context);
        row.setId(View.generateViewId());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        row.setPadding(Ui.dp(context, 22), Ui.dp(context, 9),
                Ui.dp(context, 20), Ui.dp(context, 9));
        // Eight categories have to fit a 540dp TV without scrolling: the list
        // is the whole screen, and a last row below the fold looks like a bug.
        row.setMinimumHeight(Ui.dp(context, 46));
        row.setFocusable(true);
        row.setFocusableInTouchMode(false);
        row.setClickable(true);
        row.setBackground(new GlassDrawable(context, 26f, 1f));
        row.getBackground().setAlpha(0);

        TextView label = new TextView(context);
        label.setText(category.title);
        label.setTextColor(TEXT);
        Type.apply(label, Type.HEADING);
        label.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        label.setMaxLines(1);
        row.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView chevron = new TextView(context);
        chevron.setText(rtl ? "‹" : "›");
        chevron.setTextColor(0xFF8296A5);
        chevron.setTextSize(26f);
        chevron.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chevronParams.setMarginStart(Ui.dp(context, 18));
        row.addView(chevron, chevronParams);

        StringBuilder description = new StringBuilder(category.title)
                .append(". ").append(category.summary);
        for (Detail detail : category.details) {
            description.append(". ").append(detail.label).append(": ").append(detail.value);
        }
        row.setContentDescription(description.toString());
        row.setOnFocusChangeListener((view, focused) -> {
            selectedIndex = index;
            GlassDrawable glass = (GlassDrawable) view.getBackground();
            glass.setAlpha(focused ? 255 : 0);
            glass.setFocus(focused ? 1f : 0f);
            label.setTextColor(focused ? INK : TEXT);
            chevron.setTextColor(focused ? 0xFF506571 : 0xFF8296A5);
            view.setElevation(focused ? Ui.dpf(context, 12f) : 0f);
            Motion.scale(view, focused ? 1.035f : 1f, 1f, 0.2f);
            if (focused) {
                showCategory(index);
            }
        });
        row.setOnClickListener(view -> category.action.run());
        return row;
    }

    /** The category the user is on, so a rebuilt screen can keep it. */
    int selectedCategory() {
        return selectedIndex;
    }

    private void showCategory(int index) {
        if (index < 0 || index >= categories.size()) {
            return;
        }
        selectedIndex = index;
        Category category = categories.get(index);
        selectedSummary.setText(category.summary);
        StringBuilder details = new StringBuilder();
        for (Detail detail : category.details) {
            if (details.length() > 0) {
                details.append('\n');
            }
            details.append(detail.label).append("  ·  ").append(detail.value);
        }
        selectedDetails.setText(details.toString());
        StringBuilder spoken = new StringBuilder(category.title).append(". ")
                .append(category.summary);
        for (Detail detail : category.details) {
            spoken.append(". ").append(detail.label).append(": ").append(detail.value);
        }
        selectedSummary.setContentDescription(spoken.toString());
    }

    private static int clamp(int index, int size) {
        if (size <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(size - 1, index));
    }
}
