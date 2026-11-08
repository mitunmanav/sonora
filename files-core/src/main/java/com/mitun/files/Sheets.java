package com.mitun.files;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Apple's menus and alerts: a floating rounded panel over a dimmed window, holding
 * the focus until it is answered. BACK always means Cancel.
 */
final class Sheets {
    interface Pick {
        void onPick(int index);
    }

    interface Text {
        void onText(String text);
    }

    /** One open sheet. */
    static final class Sheet extends FrameLayout {
        private final Runnable onCancel;
        private final LinearLayout panel;
        private final FrameLayout host;
        private TextView messageView;
        private boolean closed;

        Sheet(FrameLayout host, FilesTheme theme, int width, Runnable onCancel) {
            super(host.getContext());
            this.host = host;
            this.onCancel = onCancel;
            setBackgroundColor(theme.dim);
            setClickable(true);
            panel = new LinearLayout(getContext());
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setBackground(rounded(theme.sheet, FilesTheme.dp(getContext(), 14)));
            panel.setElevation(FilesTheme.dp(getContext(), 18));
            int pad = FilesTheme.dp(getContext(), 8);
            panel.setPadding(pad, pad, pad, pad);
            addView(panel, new LayoutParams(FilesTheme.dp(getContext(), width),
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            host.addView(this, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            panel.setAlpha(0f);
            panel.setScaleX(0.96f);
            panel.setScaleY(0.96f);
            panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(120).start();
        }

        LinearLayout panel() {
            return panel;
        }

        /** Replaces the message of an alert still open, for an answer read late. */
        void message(String text) {
            if (messageView != null) {
                messageView.setText(text);
            }
        }

        void close() {
            if (closed) {
                return;
            }
            closed = true;
            host.removeView(this);
        }

        void cancel() {
            if (!closed) {
                close();
                if (onCancel != null) {
                    onCancel.run();
                }
            }
        }

        boolean closed() {
            return closed;
        }

        /** Keeps the remote in the sheet. Up and down move through it; BACK cancels. */
        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    cancel();
                }
                return true;
            }
            if (super.dispatchKeyEvent(event)) {
                return true;
            }
            // Moves the focus itself. Returning true without moving it would stop the
            // window doing so too, and leave OK stuck on the first row.
            int direction = directionOf(event.getKeyCode());
            if (direction == 0) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                View focused = findFocus();
                View next = focused == null ? null : android.view.FocusFinder.getInstance()
                        .findNextFocus(this, focused, direction);
                if (next != null) {
                    next.requestFocus(direction);
                }
            }
            // Whatever was not used stays here rather than reaching the window behind.
            return true;
        }

        private static int directionOf(int code) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    return View.FOCUS_UP;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    return View.FOCUS_DOWN;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return View.FOCUS_LEFT;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return View.FOCUS_RIGHT;
                default:
                    return 0;
            }
        }

        @Override
        public View focusSearch(View focused, int direction) {
            View next = android.view.FocusFinder.getInstance().findNextFocus(this, focused, direction);
            return next != null ? next : focused;
        }
    }

    private Sheets() {
    }

    static GradientDrawable rounded(int colour, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(colour);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    /**
     * A menu.
     *
     * @param checked  the row with a tick, or -1
     * @param danger   the row in red, or -1
     */
    static Sheet menu(FrameLayout host, FilesTheme theme, String title, String[] rows, int checked,
                      int danger, Runnable onCancel, Pick pick) {
        Sheet sheet = new Sheet(host, theme, 300, onCancel);
        Context context = host.getContext();
        if (title != null) {
            TextView heading = new TextView(context);
            heading.setText(title);
            heading.setTextColor(theme.inkSecondary);
            heading.setTextSize(12.5f);
            heading.setSingleLine(true);
            heading.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            int pad = FilesTheme.dp(context, 10);
            heading.setPadding(pad, FilesTheme.dp(context, 4), pad, FilesTheme.dp(context, 6));
            sheet.panel().addView(heading);
        }
        ScrollView scroller = new ScrollView(context);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setFocusable(false);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(list);
        sheet.panel().addView(scroller);
        View first = null;
        View focus = null;
        for (int i = 0; i < rows.length; i++) {
            final int index = i;
            View row = menuRow(context, theme, rows[i], i == checked, i == danger, () -> {
                sheet.close();
                pick.onPick(index);
            });
            list.addView(row);
            first = first == null ? row : first;
            focus = i == checked ? row : focus;
        }
        final View target = focus != null ? focus : first;
        if (target != null) {
            target.post(target::requestFocus);
        }
        return sheet;
    }

    private static View menuRow(Context context, FilesTheme theme, String text, boolean checked,
                                boolean danger, Runnable action) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        int padH = FilesTheme.dp(context, 10);
        row.setPadding(padH, 0, padH, 0);
        row.setMinimumHeight(FilesTheme.dp(context, 32));

        TextView tick = new TextView(context);
        tick.setText(checked ? "✓" : "");
        tick.setTextSize(14f);
        tick.setTextColor(theme.ink);
        row.addView(tick, new LinearLayout.LayoutParams(FilesTheme.dp(context, 20),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView label = new TextView(context);
        label.setText(text);
        label.setTextSize(14.5f);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        int rest = danger ? theme.danger : theme.ink;
        label.setTextColor(rest);
        row.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final GradientDrawable lit = rounded(theme.accent, FilesTheme.dp(context, 7));
        row.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(focused ? lit : null);
            label.setTextColor(focused ? 0xFFFFFFFF : rest);
            tick.setTextColor(focused ? 0xFFFFFFFF : theme.ink);
        });
        row.setOnClickListener(v -> action.run());
        return row;
    }

    /**
     * An alert: a title, a line of explanation and its buttons side by side.
     *
     * @param focus the button that starts focused. For anything that destroys, Cancel.
     */
    static Sheet alert(FrameLayout host, FilesTheme theme, String title, String message,
                       String[] buttons, int focus, int danger, Runnable onCancel, Pick pick) {
        Sheet sheet = new Sheet(host, theme, 360, onCancel);
        addAlertText(sheet, theme, title, message);
        LinearLayout row = buttonRow(host.getContext());
        View target = null;
        for (int i = 0; i < buttons.length; i++) {
            final int index = i;
            View button = button(host.getContext(), theme, buttons[i], i == danger, () -> {
                sheet.close();
                pick.onPick(index);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    FilesTheme.dp(host.getContext(), 32), 1f);
            if (i > 0) {
                params.setMarginStart(FilesTheme.dp(host.getContext(), 8));
            }
            row.addView(button, params);
            target = i == focus ? button : target;
        }
        sheet.panel().addView(row);
        if (target != null) {
            final View view = target;
            view.post(view::requestFocus);
        }
        return sheet;
    }

    /** An alert that asks for a name, using the TV's own keyboard. */
    static Sheet input(FrameLayout host, FilesTheme theme, String title, String initial,
                       String confirm, Runnable onCancel, Text onText) {
        // By the time a cancel runs, the sheet — and the field's window token —
        // is gone, so the keyboard is hidden through the host still attached.
        Sheet sheet = new Sheet(host, theme, 380, () -> {
            hideKeyboard(host);
            if (onCancel != null) {
                onCancel.run();
            }
        });
        Context context = host.getContext();
        addAlertText(sheet, theme, title, null);
        EditText field = new EditText(context);
        field.setText(initial);
        field.setTextColor(theme.ink);
        field.setTextSize(15f);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setBackground(rounded(theme.hover, FilesTheme.dp(context, 7)));
        int pad = FilesTheme.dp(context, 10);
        field.setPadding(pad, pad / 2, pad, pad / 2);
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fieldParams.setMargins(pad, 0, pad, pad);
        sheet.panel().addView(field, fieldParams);

        Runnable finish = () -> {
            // An editor action queued before a Cancel can land after it; a
            // closed sheet answers for nothing.
            if (sheet.closed()) {
                return;
            }
            hideKeyboard(field);
            String text = field.getText().toString().trim();
            sheet.close();
            onText.onText(text);
        };
        field.setOnEditorActionListener((v, actionId, event) -> {
            finish.run();
            return true;
        });
        LinearLayout row = buttonRow(context);
        View cancel = button(context, theme, "Cancel", false, () -> {
            hideKeyboard(field);
            sheet.cancel();
        });
        View done = button(context, theme, confirm, false, finish);
        row.addView(cancel, new LinearLayout.LayoutParams(0, FilesTheme.dp(context, 32), 1f));
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(0,
                FilesTheme.dp(context, 32), 1f);
        doneParams.setMarginStart(FilesTheme.dp(context, 8));
        row.addView(done, doneParams);
        sheet.panel().addView(row);
        field.post(() -> {
            field.requestFocus();
            // A name keeps its extension: the part before it is what gets selected.
            // Only after the focus, which on its own puts the cursor at the end.
            int dot = initial.lastIndexOf('.');
            field.setSelection(0, dot > 0 ? dot : initial.length());
            InputMethodManager manager = (InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) {
                manager.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        return sheet;
    }

    private static void hideKeyboard(View field) {
        InputMethodManager manager = (InputMethodManager)
                field.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(field.getWindowToken(), 0);
        }
    }

    /** A long job's progress. Cancel stays focused for as long as it runs. */
    static final class Progress {
        final Sheet sheet;
        private final TextView line;
        private final Bar bar;

        Progress(FrameLayout host, FilesTheme theme, String title, Runnable onCancel) {
            sheet = new Sheet(host, theme, 380, onCancel);
            Context context = host.getContext();
            addAlertText(sheet, theme, title, null);
            int pad = FilesTheme.dp(context, 12);
            line = new TextView(context);
            line.setTextColor(theme.inkSecondary);
            line.setTextSize(12.5f);
            line.setSingleLine(true);
            line.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            line.setGravity(Gravity.CENTER);
            line.setPadding(pad, 0, pad, FilesTheme.dp(context, 8));
            sheet.panel().addView(line);
            bar = new Bar(context, theme);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, FilesTheme.dp(context, 6));
            barParams.setMargins(pad, 0, pad, pad);
            sheet.panel().addView(bar, barParams);
            LinearLayout row = buttonRow(context);
            View cancel = button(context, theme, "Stop", false, sheet::cancel);
            row.addView(cancel, new LinearLayout.LayoutParams(0, FilesTheme.dp(context, 32), 1f));
            sheet.panel().addView(row);
            cancel.post(cancel::requestFocus);
        }

        void update(long done, long total, String name) {
            line.setText(name);
            bar.setProgress(total > 0L ? done / (float) total : 0f);
        }
    }

    private static final class Bar extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final FilesTheme theme;
        private float progress;

        Bar(Context context, FilesTheme theme) {
            super(context);
            this.theme = theme;
        }

        void setProgress(float value) {
            progress = Math.max(0f, Math.min(1f, value));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float r = getHeight() / 2f;
            paint.setColor(theme.hover);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), r, r, paint);
            paint.setColor(theme.accent);
            if (progress > 0f) {
                canvas.drawRoundRect(0, 0, Math.max(getHeight(), getWidth() * progress),
                        getHeight(), r, r, paint);
            }
        }
    }

    private static void addAlertText(Sheet sheet, FilesTheme theme, String title, String message) {
        Context context = sheet.getContext();
        int pad = FilesTheme.dp(context, 12);
        TextView heading = new TextView(context);
        heading.setText(title);
        heading.setTextColor(theme.ink);
        heading.setTextSize(16f);
        heading.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        heading.setGravity(Gravity.CENTER);
        heading.setSingleLine(true);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.setPadding(pad, pad, pad, FilesTheme.dp(context, 6));
        sheet.panel().addView(heading);
        if (message != null) {
            TextView body = new TextView(context);
            body.setText(message);
            body.setTextColor(theme.inkSecondary);
            body.setTextSize(13f);
            body.setGravity(Gravity.CENTER);
            body.setPadding(pad, 0, pad, pad);
            sheet.panel().addView(body);
            sheet.messageView = body;
        }
    }

    private static LinearLayout buttonRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int pad = FilesTheme.dp(context, 4);
        row.setPadding(pad, pad, pad, pad);
        return row;
    }

    private static View button(Context context, FilesTheme theme, String text, boolean danger,
                               Runnable action) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(14f);
        button.setFocusable(true);
        button.setClickable(true);
        button.setSingleLine(true);
        final int rest = danger ? theme.danger : theme.ink;
        button.setTextColor(rest);
        float radius = FilesTheme.dp(context, 7);
        final GradientDrawable off = rounded(theme.hover, radius);
        final GradientDrawable on = rounded(danger ? theme.danger : theme.accent, radius);
        button.setBackground(off);
        button.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(focused ? on : off);
            button.setTextColor(focused ? 0xFFFFFFFF : rest);
        });
        button.setOnClickListener(v -> action.run());
        return button;
    }
}
