package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import com.mitun.sonora.AppEntry;

/**
 * The app "icon": a tvOS-shaped 5:3 card, showing the app's Android TV banner when
 * one exists and otherwise a generated card.
 *
 * Focus is a scale on the view, which the GPU does without a redraw. The rim
 * glow and the shadow belong to {@link FocusGlassView}, which floats over the
 * focused card so this view never has to repaint when focus moves.
 *
 * Every draw is a round rectangle with a shader. A clip path here would ask the GPU
 * for a mask per card on every frame the row moves.
 */
public final class AppIconView extends View {
    /** The corner radius as a fraction of the card height, measured from tvOS. */
    private static final float RADIUS = 0.19f;
    private static final float EDGE_REST = 0.16f;
    private static final float EDGE_LIT = 0.58f;

    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path starPath = new Path();
    private final RectF rect = new RectF();
    private final RectF edgeRect = new RectF();
    private final RectF iconRect = new RectF();
    private final float starDrop;

    private AppEntry entry;
    private Glyph gear;
    private CharSequence cardLabel;
    private boolean favourite;

    public AppIconView(Context context) {
        super(context);
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(Math.max(1f, Ui.dpf(context, 0.8f)));
        edgePaint.setColor(Ui.alpha(Color.WHITE, EDGE_REST));
        starPaint.setColor(Color.WHITE);
        starShadowPaint.setColor(0x66000000);
        starDrop = Ui.dpf(context, 1.5f);
        labelPaint.setTypeface(Ui.medium());
        labelPaint.setColor(0xFFF5F6F8);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setEntry(AppEntry entry) {
        this.entry = entry;
        prepare();
        invalidate();
    }

    /** Marks the card as one the user pinned to the top of the home screen. */
    public void setFavourite(boolean favourite) {
        if (this.favourite != favourite) {
            this.favourite = favourite;
            invalidate();
        }
    }

    /**
     * A one-shot rim for hosts that do not use {@link FocusGlassView}, like the
     * search results. The home grid leaves this alone: its rim and shadow come
     * from the floating glass, which costs no redraw.
     */
    public void setFocused(boolean focused) {
        edgePaint.setColor(Ui.alpha(Color.WHITE,
                focused ? EDGE_LIT : EDGE_REST));
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rect.set(0, 0, w, h);
        float inset = edgePaint.getStrokeWidth() / 2f;
        edgeRect.set(inset, inset, w - inset, h - inset);
        float radius = h * 0.085f;
        buildStar(w - radius - h * 0.10f, radius + h * 0.10f, radius);
        prepare();
    }

    /** Sets up everything the card draws, so onDraw allocates nothing. */
    private void prepare() {
        int w = getWidth();
        int h = getHeight();
        if (entry == null || w == 0 || h == 0) {
            return;
        }
        Bitmap banner = entry.banner;
        if (banner != null && !banner.isRecycled()) {
            BitmapShader shader = new BitmapShader(banner, Shader.TileMode.CLAMP,
                    Shader.TileMode.CLAMP);
            float scale = Math.max(w / (float) banner.getWidth(), h / (float) banner.getHeight());
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            matrix.postTranslate((w - banner.getWidth() * scale) / 2f,
                    (h - banner.getHeight() * scale) / 2f);
            shader.setLocalMatrix(matrix);
            artPaint.setShader(shader);
            return;
        }
        artPaint.setShader(null);
        if (entry.settings) {
            // tvOS's own card for Settings: one gear on brushed grey, no name in it.
            fillPaint.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{0xFFC9CCD2, 0xFF9EA2AA, 0xFF7E828A},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            float size = h * 0.68f;
            gear = new Glyph(Glyph.GEAR);
            gear.setInk(0xFF34363B);
            gear.setBounds(Math.round((w - size) / 2f), Math.round((h - size) / 2f),
                    Math.round((w + size) / 2f), Math.round((h + size) / 2f));
            return;
        }
        int base = Ui.tileColor(entry.accent);
        fillPaint.setShader(new LinearGradient(0, 0, w, h,
                new int[]{Ui.lerpColor(base, Color.WHITE, 0.16f), base,
                        Ui.lerpColor(base, Color.BLACK, 0.32f)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        // A quiet pool of the icon's own colour, so a generated card is not flat.
        glowPaint.setShader(new RadialGradient(w * 0.76f, h * 0.08f, h * 0.46f,
                new int[]{Ui.alpha(entry.accent, 0.26f), 0x00FFFFFF},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        float artSize = h * 0.46f;
        iconRect.set(w / 2f - artSize / 2f, h * 0.35f - artSize / 2f,
                w / 2f + artSize / 2f, h * 0.35f + artSize / 2f);
        labelPaint.setTextSize(h * 0.145f);
        cardLabel = TextUtils.ellipsize(entry.label, labelPaint, w * 0.84f,
                TextUtils.TruncateAt.END);
    }

    /** A five-pointed star, drawn from the outside in. */
    private void buildStar(float cx, float cy, float radius) {
        starPath.reset();
        for (int i = 0; i < 10; i++) {
            double angle = Math.PI / 5 * i - Math.PI / 2;
            float r = (i % 2 == 0) ? radius : radius * 0.44f;
            float x = cx + (float) (Math.cos(angle) * r);
            float y = cy + (float) (Math.sin(angle) * r);
            if (i == 0) {
                starPath.moveTo(x, y);
            } else {
                starPath.lineTo(x, y);
            }
        }
        starPath.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (entry == null || w == 0 || h == 0) {
            return;
        }
        float radius = h * RADIUS;
        if (artPaint.getShader() != null) {
            canvas.drawRoundRect(rect, radius, radius, artPaint);
        } else {
            drawGeneratedCard(canvas, w, h, radius);
        }
        float edgeRadius = Math.max(1f, radius - edgePaint.getStrokeWidth() / 2f);
        canvas.drawRoundRect(edgeRect, edgeRadius, edgeRadius, edgePaint);
        if (favourite) {
            // A soft shadow under the star, so it reads on light artwork too.
            canvas.save();
            canvas.translate(0, starDrop);
            canvas.drawPath(starPath, starShadowPaint);
            canvas.restore();
            canvas.drawPath(starPath, starPaint);
        }
    }

    private void drawGeneratedCard(Canvas canvas, int w, int h, float radius) {
        canvas.drawRoundRect(rect, radius, radius, fillPaint);
        if (entry.settings) {
            if (gear != null) {
                gear.draw(canvas);
            }
            return;
        }
        canvas.drawRoundRect(rect, radius, radius, glowPaint);
        Bitmap art = entry.icon;
        if (art != null && !art.isRecycled()) {
            canvas.drawBitmap(art, null, iconRect, iconPaint);
        }
        if (cardLabel != null) {
            canvas.drawText(cardLabel, 0, cardLabel.length(), w / 2f, h * 0.80f, labelPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        Motion.cancel(this);
        super.onDetachedFromWindow();
    }
}
