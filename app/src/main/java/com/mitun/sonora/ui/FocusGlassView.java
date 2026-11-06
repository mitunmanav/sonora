package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.mitun.sonora.DeviceTier;

/**
 * The focus glass: one slab that floats over the focused card and carries the
 * shadow, so the card itself never has to repaint when focus moves. tvOS
 * lifts the focused card; there is no rim or outline.
 *
 * Everything that changes while the focus travels — position, size, opacity —
 * is a RenderNode property, which the GPU applies without re-recording a single
 * draw. The slab is drawn once into a hardware layer and then only moved.
 */
public final class FocusGlassView extends View {
    /** Air around the card for the shadow to live in. */
    private static final float PAD_DP = 16f;
    /** Matches AppIconView's corner radius as a fraction of the card height. */
    private static final float RADIUS = 0.19f;

    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cardRect = new RectF();
    private final RectF shadowRect = new RectF();
    private final float[] shadowSpread = new float[3];
    private final float[] shadowDrop = new float[3];
    private final float pad;
    private final boolean shadow;

    public FocusGlassView(Context context) {
        super(context);
        pad = Ui.dp(context, PAD_DP);
        shadow = DeviceTier.cardShadow(DeviceTier.get(context));
        // tvOS's lift is a soft shadow that lives mostly under the card: a
        // small lateral spread and a drop that grows per layer, so nothing
        // leaks above the top edge.
        for (int i = 0; i < 3; i++) {
            shadowSpread[i] = Ui.dpf(context, 1.5f + i * 2f);
            shadowDrop[i] = Ui.dpf(context, 2f + i * 3.5f);
        }
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (shadow) {
            // The draw is static; caching it as a texture means focus travel is
            // pure compositing. On the lightest tier the slab draws nothing and
            // the card's own scale carries the focus.
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }
        setAlpha(0f);
    }

    /**
     * Puts the slab over a card, given the card's centre and drawn size in this
     * view's parent coordinates. With {@code animate} the travel is a spring on
     * translation only; without it the slab jumps, for scroll frames.
     */
    public void showAt(float centerX, float centerY, float cardWidth,
                       float cardHeight, boolean animate) {
        float w = cardWidth + pad * 2f;
        float h = cardHeight + pad * 2f;
        android.view.ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null && (lp.width != (int) w || lp.height != (int) h)) {
            lp.width = (int) w;
            lp.height = (int) h;
            setLayoutParams(lp);
        }
        setPivotX(w / 2f);
        setPivotY(h / 2f);
        final float tx = centerX - w / 2f;
        final float ty = centerY - h / 2f;
        if (animate) {
            // The real translation tracks, so a scroll frame's Motion.set
            // stops the same springs this started.
            Motion.spring(this, Motion.TRANSLATION_X, tx,
                    Motion.DAMPING_CRITICAL, Motion.RESPONSE_MOVE);
            Motion.spring(this, Motion.TRANSLATION_Y, ty,
                    Motion.DAMPING_CRITICAL, Motion.RESPONSE_MOVE);
        } else {
            Motion.set(this, Motion.TRANSLATION_X, tx);
            Motion.set(this, Motion.TRANSLATION_Y, ty);
        }
        setShown(true);
    }

    /** Shows or hides the slab. Alpha is a RenderNode property: free. */
    public void setShown(boolean shown) {
        Motion.spring(this, Motion.ALPHA, shown ? 1f : 0f,
                Motion.DAMPING_CRITICAL, Motion.RESPONSE_FOCUS);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        // The card sits inside the padding; everything outside it is air.
        cardRect.set(pad, pad, w - pad, h - pad);
        float radius = (h - pad * 2f) * RADIUS;
        if (shadow) {
            for (int i = 2; i >= 0; i--) {
                shadowPaint.setColor(Ui.alpha(Color.BLACK, 0.11f * (1f - i * 0.22f)));
                shadowRect.set(cardRect.left - shadowSpread[i],
                        cardRect.top - shadowSpread[i] + shadowDrop[i],
                        cardRect.right + shadowSpread[i],
                        cardRect.bottom + shadowSpread[i] + shadowDrop[i]);
                canvas.drawRoundRect(shadowRect, radius + shadowSpread[i],
                        radius + shadowSpread[i], shadowPaint);
            }
        }
    }
}
