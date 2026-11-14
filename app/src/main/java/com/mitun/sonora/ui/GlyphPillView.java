package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * A round glass button with a glyph in it, for the top row of the home screen.
 *
 * It is the same material as the round buttons in Control Center: dark glass
 * at rest, lit under the focus. The frost and backdrop calls mirror
 * {@link GlassPillView} so the home screen's glass bake can feed it.
 */
public final class GlyphPillView extends FrameLayout {
    private static final float FOCUS_SCALE = 1.08f;
    private static final float PRESS_SCALE = 0.92f;

    private final GlassDrawable material;
    private final Glyph glyph;
    private final int restInk;
    /** One property per pill, so a focus change allocates nothing. */
    private final Motion.FocusValue focusValue = new Motion.FocusValue(value -> {
        GlyphPillView.this.material.setFocus(value);
        GlyphPillView.this.glyph.setInk(Ui.mix(GlyphPillView.this.restInk, Ui.INK_ON_LIT, value));
    });

    public GlyphPillView(Context context, int mark, String label) {
        this(context, mark, label, Theme.ink());
    }

    public GlyphPillView(Context context, int mark, String label, int ink) {
        super(context);
        restInk = ink;
        setFocusable(true);
        setClickable(true);
        setContentDescription(label);
        material = GlassDrawable.circle(context, 0.9f);
        setBackground(material);
        glyph = new Glyph(mark);
        glyph.setInk(ink);
        ImageView image = new ImageView(context);
        image.setImageDrawable(glyph);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(image, new LayoutParams(Ui.dp(context, 20), Ui.dp(context, 20),
                Gravity.CENTER));

        setOnFocusChangeListener((v, hasFocus) -> {
            Motion.scale(this, hasFocus ? FOCUS_SCALE : 1f, Motion.DAMPING_CRITICAL,
                    Motion.RESPONSE_FOCUS);
            setElevation(Ui.dpf(context, hasFocus ? 10f : 0f));
            Motion.springCustom(this, 0, hasFocus ? 1f : 0f, Motion.DAMPING_CRITICAL,
                    Motion.RESPONSE_FOCUS, focusValue);
        });
    }

    /** Frosts the pill with its share of the blurred wallpaper. */
    public void setFrost(Bitmap picture, int screenWidth, int screenHeight, float zoom,
                         Rect region) {
        material.setFrost(picture, screenWidth, screenHeight, zoom, region,
                Theme.glassVeil());
    }

    /** Shows the wallpaper through the pill, bent like the rest of the glass. */
    public void setBackdrop(Bitmap lens) {
        material.setBackdrop(lens, Theme.glassVeil());
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        Motion.press(this, pressed, isFocused() ? FOCUS_SCALE : 1f, PRESS_SCALE);
    }

    @Override
    protected void onDetachedFromWindow() {
        Motion.cancel(this);
        super.onDetachedFromWindow();
    }
}
