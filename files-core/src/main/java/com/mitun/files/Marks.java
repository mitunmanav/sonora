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

    private void build(int kind) {
        switch (kind) {
            case DOWNLOADS:
                path.addCircle(12, 12, 9, Path.Direction.CW);
                line(12, 7, 12, 16);
                line(8.5f, 12.5f, 12, 16);
                line(15.5f, 12.5f, 12, 16);
                break;
            case MOVIES:
                path.addRoundRect(new RectF(3.5f, 5, 20.5f, 19), 2, 2, Path.Direction.CW);
                line(8, 5, 8, 19);
                line(16, 5, 16, 19);
                line(3.5f, 12, 8, 12);
                line(16, 12, 20.5f, 12);
                break;
            case MUSIC:
                path.addCircle(7.5f, 18, 2.5f, Path.Direction.CW);
                path.addCircle(17.5f, 16, 2.5f, Path.Direction.CW);
                line(10, 18, 10, 6);
                line(20, 16, 20, 4);
                line(10, 6, 20, 4);
                break;
            case PICTURES:
                path.addRoundRect(new RectF(3.5f, 5, 20.5f, 19), 2, 2, Path.Direction.CW);
                path.addCircle(8.5f, 9.5f, 1.5f, Path.Direction.CW);
                path.moveTo(4, 17);
                path.lineTo(10, 12);
                path.lineTo(14, 15.5f);
                path.lineTo(16.5f, 13.5f);
                path.lineTo(20, 16.5f);
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
                path.addCircle(17, 12, 0.5f, Path.Direction.CW);
                break;
            case BACK:
                line(14.5f, 5.5f, 8, 12);
                line(8, 12, 14.5f, 18.5f);
                break;
            case GRID:
                for (int y = 0; y < 2; y++) {
                    for (int x = 0; x < 2; x++) {
                        path.addRoundRect(new RectF(4.5f + x * 8.5f, 4.5f + y * 8.5f,
                                10.5f + x * 8.5f, 10.5f + y * 8.5f), 1.2f, 1.2f, Path.Direction.CW);
                    }
                }
                break;
            case LIST:
                for (int y = 0; y < 3; y++) {
                    line(4.5f, 7 + y * 5, 5, 7 + y * 5);
                    line(9, 7 + y * 5, 20, 7 + y * 5);
                }
                break;
            case SORT:
                line(4.5f, 7, 19.5f, 7);
                line(7, 12, 17, 12);
                line(9.5f, 17, 14.5f, 17);
                break;
            case NEW_FOLDER:
                folder();
                line(12, 10.5f, 12, 16.5f);
                line(9, 13.5f, 15, 13.5f);
                break;
            case MORE:
                path.addCircle(12, 12, 9, Path.Direction.CW);
                for (int x = 0; x < 3; x++) {
                    line(8 + x * 4, 12, 8.05f + x * 4, 12);
                }
                break;
            case APPEARANCE:
                path.addCircle(12, 12, 8.5f, Path.Direction.CW);
                path.addArc(new RectF(7, 3.5f, 17, 20.5f), -90, 180);
                line(12, 3.5f, 12, 20.5f);
                break;
            case FOLDER:
            default:
                folder();
                break;
        }
    }

    private void folder() {
        path.moveTo(3.5f, 18);
        path.lineTo(3.5f, 6);
        path.lineTo(9.5f, 6);
        path.lineTo(11.5f, 8.5f);
        path.lineTo(20.5f, 8.5f);
        path.lineTo(20.5f, 18);
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
