package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * The material of one row in a tvOS-style list.
 *
 * At rest it is a faint translucent pill with no rim, as tvOS 26 draws it. When
 * it takes the focus it turns into an opaque white pill for dark ink. Only alpha
 * changes while the focus moves, so focusing a row costs a redraw and nothing
 * else.
 */
public final class RowDrawable extends Drawable {
    private final Paint rest = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int alpha = 255;
    private float focus;

    public RowDrawable(Context context) {
        rest.setColor(Theme.rowRest());
        lit.setColor(0xFFFFFFFF);
    }

    /** How much light the row has caught: nothing at rest, all of it on focus. */
    public void setFocus(float focus) {
        float f = Math.max(0f, Math.min(1f, focus));
        if (f == this.focus) {
            return;
        }
        this.focus = f;
        applyAlphas();
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(android.graphics.Rect bounds) {
        super.onBoundsChange(bounds);
        rect.set(bounds);
        applyAlphas();
    }

    private void applyAlphas() {
        rest.setAlpha(Math.round((Theme.rowRest() >>> 24) * (alpha / 255f)));
        lit.setAlpha(Math.round(alpha * focus));
    }

    @Override
    public void draw(Canvas canvas) {
        float r = rect.height() / 2f;
        canvas.drawRoundRect(rect, r, r, rest);
        if (focus > 0f) {
            canvas.drawRoundRect(rect, r, r, lit);
        }
    }

    /** The shape the row's elevation casts its shadow from. */
    @Override
    public void getOutline(android.graphics.Outline outline) {
        outline.setRoundRect(getBounds(), getBounds().height() / 2f);
        outline.setAlpha(1f);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        applyAlphas();
        invalidateSelf();
    }

    @Override
    public void setColorFilter(android.graphics.ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }
}
