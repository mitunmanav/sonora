package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * A small glass pill with one icon in it.
 *
 * The home screen has two of them at the top right: the clock, which opens
 * the settings, and this one, which opens the control centre.
 */
public final class GlassIconPill extends FrameLayout {
    private Runnable onOpen;

    public GlassIconPill(Context context, Bitmap icon, String description) {
        super(context);
        setFocusable(true);
        setClickable(true);
        setBackground(new GlassDrawable(context, 27f, 1.15f));
        setElevation(Ui.dpf(context, 8f));
        int pad = Ui.dp(context, 9);
        setPadding(pad, pad, pad, pad);

        ImageView image = new ImageView(context);
        image.setImageBitmap(icon);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(image, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        setContentDescription(description);

        setOnFocusChangeListener((v, hasFocus) -> {
            // A spring, so moving the focus on before the pill has settled
            // continues from where it is instead of starting over.
            Motion.scale(this, hasFocus ? 1.055f : 1f, Motion.DAMPING_CRITICAL,
                    Motion.RESPONSE_FOCUS);
            setElevation(Ui.dpf(context, hasFocus ? 18f : 8f));
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
}
