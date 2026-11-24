package com.mitun.files;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** The line marks of the sidebar and the toolbar, on a 24-unit grid. */
final class Marks extends Drawable {
    static final int DOWNLOADS = 0;
    static final int MOVIES = 1;
    static final int MUSIC = 2;
    static final int PICTURES = 3;
    static final int DOCUMENTS = 4;
    static final int DRIVE = 5;
    static final int FOLDER = 6;
    static final int BACK = 7;
    static final int GRID = 8;
    static final int LIST = 9;
    static final int SORT = 10;
    static final int NEW_FOLDER = 11;
    static final int MORE = 12;
    static final int APPEARANCE = 13;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    Marks(int kind) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(1.8f);
        build(kind);
    }

    void setInk(int colour) {
        paint.setColor(colour);
        invalidateSelf();
    }

    /** Draws another mark with the same pen, for a button whose meaning changed. */
    void setKind(int kind) {
        path.rewind();
        build(kind);
        invalidateSelf();
    }

    private void build(int kind) {
        switch (kind) {
            case DOWNLOADS:
                path.addCircle(12, 12, 8.5f, Path.Direction.CW);
                line(12, 7.2f, 12, 13.2f);
                line(8.4f, 10.6f, 12, 14.4f);
                line(15.6f, 10.6f, 12, 14.4f);
                break;
            case MOVIES:
                // Clapperboard: a striped top slab hinged above the body.
                path.moveTo(3.5f, 10f);
                path.lineTo(5f, 4.5f);
                path.lineTo(21.5f, 4.5f);
                path.lineTo(20f, 10f);
                path.close();
                for (float f = 0.22f; f < 0.8f; f += 0.28f) {
                    line(3.5f + 16.5f * f, 10f, 5f + 16.5f * (f + 0.10f), 4.5f);
                }
                path.addRoundRect(new RectF(3.5f, 10f, 20.5f, 19f), 1.6f, 1.6f,
                        Path.Direction.CW);
                break;
            case MUSIC:
                path.addOval(new RectF(5.4f, 15.9f, 8.6f, 18.5f), Path.Direction.CW);
                line(8.4f, 16.4f, 8.4f, 6f);
                path.addOval(new RectF(15f, 13.9f, 18.2f, 16.5f), Path.Direction.CW);
                line(18f, 14.4f, 18f, 4.2f);
                break;
            case PICTURES:
                path.addRoundRect(new RectF(3.5f, 5f, 20.5f, 19f), 2f, 2f, Path.Direction.CW);
                path.addCircle(8.5f, 9.3f, 1.5f, Path.Direction.CW);
                path.moveTo(4.3f, 16.8f);
                path.lineTo(9.5f, 11.8f);
                path.lineTo(12.8f, 14.6f);
                path.lineTo(15.8f, 12.2f);
                path.lineTo(19.7f, 16.8f);
                break;
            case DOCUMENTS:
                path.moveTo(6, 3.5f);
                path.lineTo(14, 3.5f);
                path.lineTo(18.5f, 8);
                path.lineTo(18.5f, 20.5f);
                path.lineTo(6, 20.5f);
                path.close();
                line(14, 3.5f, 14, 8);
                line(14, 8, 18.5f, 8);
                line(9, 12.5f, 15.5f, 12.5f);
                line(9, 16, 15.5f, 16);
                break;
            case DRIVE:
                path.addRoundRect(new RectF(3, 7, 21, 17), 2.5f, 2.5f, Path.Direction.CW);
                line(6.5f, 12, 11, 12);
                path.addCircle(17, 12, 0.9f, Path.Direction.CW);
                break;
            case BACK:
                line(15f, 5.5f, 8.5f, 12);
                line(8.5f, 12, 15f, 18.5f);
                break;
            case GRID:
                for (int y = 0; y < 2; y++) {
                    for (int x = 0; x < 2; x++) {
                        float left = 3.5f + x * 9.5f;
                        float top = 3.5f + y * 9.5f;
                        path.addRoundRect(new RectF(left, top, left + 8f, top + 8f), 2f, 2f,
                                Path.Direction.CW);
                    }
                }
                break;
            case LIST:
                for (int y = 0; y < 3; y++) {
                    float row = 7f + y * 5f;
                    path.addCircle(5.6f, row, 0.9f, Path.Direction.CW);
                    line(9.5f, row, 20, row);
                }
                break;
            case SORT:
                line(4.5f, 7, 19.5f, 7);
                line(7, 12, 17, 12);
                line(9.5f, 17, 14.5f, 17);
                break;
            case NEW_FOLDER:
                folder();
                line(12, 11, 12, 16);
                line(9.5f, 13.5f, 14.5f, 13.5f);
                break;
            case MORE:
                path.addCircle(12, 12, 9, Path.Direction.CW);
                for (int x = 0; x < 3; x++) {
                    path.addCircle(8 + x * 4, 12, 0.9f, Path.Direction.CW);
                }
                break;
            case APPEARANCE:
                path.addCircle(12, 12, 8.5f, Path.Direction.CW);
                line(12, 3.5f, 12, 20.5f);
                break;
            case FOLDER:
            default:
                folder();
                break;
        }
    }

    private void folder() {
        path.moveTo(4, 18);
        path.lineTo(4, 6.5f);
        path.lineTo(9, 6.5f);
        path.lineTo(11, 9);
        path.lineTo(20, 9);
        path.lineTo(20, 18);
        path.close();
    }

    private void line(float x1, float y1, float x2, float y2) {
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        int saved = canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f);
        canvas.drawPath(path, paint);
        canvas.restoreToCount(saved);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
