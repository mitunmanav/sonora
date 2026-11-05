package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.View;

import com.mitun.sonora.SearchKeys;

/**
 * The tvOS search keyboard: one line of keys, moved along with the remote.
 *
 * One view draws every key, where a view per key would be thirty more to measure,
 * and it asks for no system keyboard, which on a 1 GB TV is a whole process.
 */
public final class SearchKeyboard extends View {
    public interface Listener {
        void onKey(String key);

        /** DOWN was pressed: the focus goes to the results. */
        void onLeaveDown();
    }

    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pill = new RectF();
    private final Glyph delete = new Glyph(Glyph.DELETE);
    private final Glyph voice = new Glyph(Glyph.MIC);
    private final Listener listener;
    private final boolean hasVoice;
    private final float keyPitch;
    private final float pillSize;
    private final float glyphSize;
    private final float spaceWidth;
    private final float spaceWeight;
    private final float pageGap;

    private String[] keys;
    private boolean digits;
    private int index = 1;
    /** Where the pill is, in keys. It travels between them. */
    private float pillAt = 1f;
    private float pillLight;

    private final Motion.FloatProperty pillTravel = new Motion.FloatProperty() {
        @Override
        public float get() {
            return pillAt;
        }

        @Override
        public void set(float value) {
            pillAt = value;
            invalidate();
        }
    };

    public SearchKeyboard(Context context, boolean hasVoice, Listener listener) {
        super(context);
        this.listener = listener;
        this.hasVoice = hasVoice;
        keys = SearchKeys.keys(false, hasVoice);
        setFocusable(true);
        setFocusableInTouchMode(false);
        keyPitch = Ui.dpf(context, 29f);
        pillSize = Ui.dpf(context, 35f);
        pageGap = Ui.dpf(context, 9f);
        glyphSize = Ui.dpf(context, 19f);
        spaceWidth = Ui.dpf(context, 11f);
        spaceWeight = Ui.dpf(context, 1.6f);
        Type.apply(keyPaint, Type.HEADING, context);
        keyPaint.setTextAlign(Paint.Align.CENTER);
        keyPaint.setStrokeCap(Paint.Cap.ROUND);
        setContentDescription("Keyboard");
    }

    /** The middle of a key, measured so that the whole line sits in the middle. */
    private float keyX(float key) {
        float x = getWidth() / 2f + (key - (keys.length - 1) / 2f) * keyPitch;
        // The page key is wider than a letter, so it stands a little apart.
        return key < 1f ? x - (1f - key) * pageGap : x;
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(gainFocus, direction, previous);
        pillLight = gainFocus ? 1f : 0f;
        invalidate();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                move(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                move(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                listener.onLeaveDown();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                press();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private void move(int step) {
        int target = SearchKeys.move(index, step, keys.length);
        if (target != index) {
            index = target;
            Motion.springCustom(this, 0, target, Motion.DAMPING_CRITICAL, Motion.RESPONSE_PRESS,
                    pillTravel);
        }
    }

    private void press() {
        String key = keys[index];
        if (SearchKeys.PAGE.equals(key)) {
            digits = !digits;
            keys = SearchKeys.keys(digits, hasVoice);
            index = 0;
            Motion.cancel(this);
            pillAt = 0f;
            invalidate();
        }
        listener.onKey(key);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getWidth() == 0) {
            return;
        }
        float cy = getHeight() / 2f;
        float lit = pillLight;
        if (lit > 0f) {
            float x = keyX(pillAt);
            pill.set(x - pillSize / 2f, cy - pillSize / 2f, x + pillSize / 2f, cy + pillSize / 2f);
            pillPaint.setColor(Color.WHITE);
            canvas.drawRoundRect(pill, pillSize / 2f, pillSize / 2f, pillPaint);
        }
        float baseline = cy - (keyPaint.descent() + keyPaint.ascent()) / 2f;
        for (int i = 0; i < keys.length; i++) {
            float x = keyX(i);
            boolean on = lit > 0f && Math.abs(i - pillAt) < 0.5f;
            int ink = on ? Theme.INK_ON_FOCUS : Theme.inkSoft();
            String key = keys[i];
            if (SearchKeys.types(key)) {
                keyPaint.setColor(ink);
                canvas.drawText(key, x, baseline, keyPaint);
            } else if (SearchKeys.DELETE.equals(key)) {
                drawGlyph(canvas, delete, x, cy, ink);
            } else if (SearchKeys.VOICE.equals(key)) {
                drawGlyph(canvas, voice, x, cy, ink);
            } else if (SearchKeys.SPACE.equals(key)) {
                keyPaint.setColor(ink);
                keyPaint.setStrokeWidth(spaceWeight);
                canvas.drawLine(x - spaceWidth / 2f, cy + glyphSize / 4f,
                        x + spaceWidth / 2f, cy + glyphSize / 4f, keyPaint);
            } else {
                keyPaint.setColor(ink);
                float size = keyPaint.getTextSize();
                keyPaint.setTextSize(size * 0.62f);
                canvas.drawText(digits ? "abc" : "123", x,
                        cy - (keyPaint.descent() + keyPaint.ascent()) / 2f, keyPaint);
                keyPaint.setTextSize(size);
            }
        }
    }

    private void drawGlyph(Canvas canvas, Glyph glyph, float x, float cy, int ink) {
        glyph.setInk(ink);
        glyph.setBounds(Math.round(x - glyphSize / 2f), Math.round(cy - glyphSize / 2f),
                Math.round(x + glyphSize / 2f), Math.round(cy + glyphSize / 2f));
        glyph.draw(canvas);
    }

    @Override
    protected void onDetachedFromWindow() {
        Motion.cancel(this);
        super.onDetachedFromWindow();
    }
}
