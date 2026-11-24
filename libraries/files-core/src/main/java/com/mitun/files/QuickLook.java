package com.mitun.files;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Quick Look: a picture or a text file on the whole screen, without leaving the
 * folder. Left and right walk through the pictures around it.
 */
final class QuickLook extends FrameLayout {
    /** A big log file is shown from its start rather than read whole into memory. */
    private static final int MAX_TEXT = 128 * 1024;

    private final List<FileLogic.Entry> entries;
    private final Runnable onClose;
    private final ImageView image;
    private final ScrollView textScroller;
    private final TextView text;
    private final TextView caption;
    private final int maxSide;
    private final boolean low;
    /** One decoder, so a held-down direction key cannot start a decode storm. */
    private final ExecutorService decoder = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "files-quicklook");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private int index;
    /** Written on the screen's thread, read on the decoder's. */
    private volatile int generation;

    QuickLook(Context context, FilesTheme theme, List<FileLogic.Entry> entries, int start,
              boolean low, Runnable onClose) {
        super(context);
        this.entries = entries;
        this.index = start;
        this.onClose = onClose;
        this.low = low;
        setBackgroundColor(theme.light ? 0xFFF5F5F7 : 0xFF000000);
        setFocusable(true);
        setFocusableInTouchMode(true);
        maxSide = Math.max(context.getResources().getDisplayMetrics().widthPixels,
                context.getResources().getDisplayMetrics().heightPixels) / (low ? 2 : 1);

        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        text = new TextView(context);
        text.setTextColor(theme.ink);
        text.setTextSize(15f);
        text.setLineSpacing(FilesTheme.dp(context, 4), 1f);
        int pad = FilesTheme.dp(context, 48);
        text.setPadding(pad, FilesTheme.dp(context, 24), pad, FilesTheme.dp(context, 56));
        textScroller = new ScrollView(context);
        textScroller.setFocusable(false);
        textScroller.addView(text);
        addView(textScroller, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        caption = new TextView(context);
        caption.setTextColor(0xFFFFFFFF);
        caption.setTextSize(13f);
        caption.setSingleLine(true);
        caption.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        caption.setBackground(Sheets.rounded(0xB3000000, FilesTheme.dp(context, 12)));
        int padH = FilesTheme.dp(context, 14);
        caption.setPadding(padH, FilesTheme.dp(context, 5), padH, FilesTheme.dp(context, 5));
        LayoutParams captionParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        captionParams.bottomMargin = FilesTheme.dp(context, 16);
        addView(caption, captionParams);
        show();
    }

    private void show() {
        final FileLogic.Entry entry = entries.get(index);
        final int ticket = ++generation;
        caption.setText(entry.name + "  ·  " + FileLogic.formatSize(entry.size));
        final boolean picture = entry.kind == FileLogic.IMAGE;
        // The last picture goes at once rather than waiting under the new caption.
        image.setImageDrawable(null);
        image.setVisibility(picture ? VISIBLE : GONE);
        textScroller.setVisibility(picture ? GONE : VISIBLE);
        try {
            decoder.execute(() -> {
                // Out of date already, so it never even reaches the disk.
                if (ticket != generation) {
                    return;
                }
                final Bitmap bitmap = picture ? decode(entry.file) : null;
                final String content = picture ? null : readText(entry.file);
                post(() -> {
                    if (ticket != generation) {
                        return;
                    }
                    if (picture) {
                        image.setImageBitmap(bitmap);
                        if (bitmap == null) {
                            caption.setText(entry.name + "  ·  This picture cannot be shown");
                        }
                    } else {
                        text.setText(content);
                        textScroller.scrollTo(0, 0);
                    }
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Closed while the press was still landing.
        }
    }

    private Bitmap decode(File file) {
        // A pipe or socket wearing a picture's name would hold the decode open.
        if (!file.isFile()) {
            return null;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), bounds);
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = low ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private static String readText(File file) {
        // A pipe or socket wearing a text file's name would hold the read open.
        if (!file.isFile()) {
            return "This file cannot be read.";
        }
        byte[] buffer = new byte[MAX_TEXT];
        int total = 0;
        try (FileInputStream in = new FileInputStream(file)) {
            int read;
            while (total < MAX_TEXT && (read = in.read(buffer, total, MAX_TEXT - total)) > 0) {
                total += read;
            }
        } catch (IOException | SecurityException e) {
            return "This file cannot be read.";
        }
        String content = new String(buffer, 0, total, StandardCharsets.UTF_8);
        return file.length() > MAX_TEXT
                ? content + "\n\n… Showing the first " + FileLogic.formatSize(MAX_TEXT) + "."
                : content;
    }

    /** The next picture that way, skipping anything that is not one. */
    private void step(int direction) {
        for (int i = index + direction; i >= 0 && i < entries.size(); i += direction) {
            if (entries.get(i).kind == FileLogic.IMAGE) {
                index = i;
                show();
                return;
            }
        }
    }

    void close() {
        generation++;
        decoder.shutdownNow();
        image.setImageDrawable(null);
        onClose.run();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                close();
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            boolean picture = entries.get(index).kind == FileLogic.IMAGE;
            if (picture && code == KeyEvent.KEYCODE_DPAD_LEFT) {
                step(-1);
                return true;
            }
            if (picture && code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                step(1);
                return true;
            }
            if (!picture && code == KeyEvent.KEYCODE_DPAD_UP) {
                textScroller.smoothScrollBy(0, -getHeight() / 2);
                return true;
            }
            if (!picture && code == KeyEvent.KEYCODE_DPAD_DOWN) {
                textScroller.smoothScrollBy(0, getHeight() / 2);
                return true;
            }
        }
        // Directions and OK stay here so the focus cannot wander into the window
        // behind; anything else, volume included, still reaches the system.
        return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_CENTER;
    }
}
