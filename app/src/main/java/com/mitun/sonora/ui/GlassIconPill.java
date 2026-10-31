package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * A small round glass button with one icon in it.
 *
 * The home screen has three of them at the top right: search, the control
 * centre and the clock next to them. A circle, not a pill: that is the shape
 * tvOS and Google TV both use for the buttons in the top row.
 *
 * The focus state is the one Google TV's launcher uses, because it cannot be
 * missed from across the room: the glass catches the light and turns bright,
 * and the mark on it turns dark so it never fights the surface it sits on.
 */
public final class GlassIconPill extends FrameLayout {
    private final GlassDrawable material;
    private final ImageView image;
    private Runnable onOpen;
    private float focus;

    public GlassIconPill(Context context, Bitmap icon, String description) {
        super(context);
        setFocusable(true);
        setClickable(true);
        // A button is a light material: it draws the eye to the thing that can
        // be pressed.
        material = GlassDrawable.circle(context, 0.9f);
        setBackground(material);
        // The shadow the elevation casts has to follow the circle, so the
        // outline is the circle.
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        setClipToOutline(true);
        setElevation(Ui.dpf(context, 8f));
        int pad = Ui.dp(context, 9);
        setPadding(pad, pad, pad, pad);

        image = new ImageView(context);
        image.setImageBitmap(icon);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int size = Math.max(1, icon.getWidth());
        addView(image, new LayoutParams(size, size, Gravity.CENTER));
        setContentDescription(description);
        tint(Ui.INK_ON_GLASS);

        setOnFocusChangeListener((v, hasFocus) -> {
            // A spring, so moving the focus on before the pill has settled
            // continues from where it is instead of starting over.
            Motion.scale(this, hasFocus ? 1.075f : 1f, Motion.DAMPING_CRITICAL,
                    Motion.RESPONSE_FOCUS);
            setElevation(Ui.dpf(context, hasFocus ? 18f : 8f));
            // The glass catches the light. The glow is the alpha of a gradient
            // that was built once, so all this costs is a redraw.
            Motion.springCustom(this, 0, hasFocus ? 1f : 0f, Motion.DAMPING_CRITICAL,
                    Motion.RESPONSE_FOCUS, focusValue());
        });
        setOnClickListener(v -> {
            if (onOpen != null) {
                onOpen.run();
            }
        });
    }

    public void setOnOpen(Runnable onOpen) {
        this.onOpen = onOpen;
    }

    private void tint(int colour) {
        image.setColorFilter(new PorterDuffColorFilter(colour, PorterDuff.Mode.SRC_IN));
    }

    private Motion.FloatProperty focusValue() {
        return new Motion.FloatProperty() {
            @Override
            public float get() {
                return focus;
            }

            @Override
            public void set(float value) {
                focus = value;
                material.setFocus(value);
                // The mark turns dark as the glass lights up: white on a lit
                // lens would be white on white.
                tint(Ui.mix(Ui.INK_ON_GLASS, Ui.INK_ON_LIT, value));
            }
        };
    }
}
