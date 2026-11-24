package com.mitun.files;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The blue folder and the folded page, drawn from paths on a 100-unit grid. One
 * instance per kind serves every row, so a long folder allocates no icons.
 */
final class FileIcon extends Drawable {
    private static final FileIcon[] SHARED = new FileIcon[8];

    private final int kind;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint front = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fold = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path pagePath = new Path();
    private final Path foldPath = new Path();
    private final Path markPath = new Path();
    private final RectF box = new RectF();

    static FileIcon of(int kind) {
        int index = Math.max(0, Math.min(SHARED.length - 1, kind));
        if (SHARED[index] == null) {
            SHARED[index] = new FileIcon(index);
        }
        return SHARED[index];
    }

    private FileIcon(int kind) {
        this.kind = kind;
        if (kind == FileLogic.FOLDER) {
            fill.setShader(new LinearGradient(0, 14, 0, 40, 0xFF3F9BEA, 0xFF2F86D8,
                    Shader.TileMode.CLAMP));
            front.setShader(new LinearGradient(0, 28, 0, 86, 0xFF7CC7F9, 0xFF3F9FEC,
                    Shader.TileMode.CLAMP));
            return;
        }
        fill.setColor(0xFFF7F7F9);
        front.setColor(0x24000000);
        front.setStyle(Paint.Style.STROKE);
        front.setStrokeWidth(1.4f);
        fold.setColor(0xFFD9DAE0);
        pagePath.moveTo(22, 6);
        pagePath.lineTo(60, 6);
        pagePath.lineTo(80, 26);
        pagePath.lineTo(80, 88);
        pagePath.quadTo(80, 94, 74, 94);
        pagePath.lineTo(26, 94);
        pagePath.quadTo(20, 94, 20, 88);
        pagePath.lineTo(20, 12);
        pagePath.quadTo(20, 6, 26, 6);
        pagePath.close();
        foldPath.moveTo(60, 6);
        foldPath.lineTo(80, 26);
        foldPath.lineTo(64, 26);
        foldPath.quadTo(60, 26, 60, 22);
        foldPath.close();
        buildMark();
    }

    private void buildMark() {
        mark.setStrokeCap(Paint.Cap.ROUND);
        mark.setStrokeJoin(Paint.Join.ROUND);
        switch (kind) {
            case FileLogic.IMAGE:
                mark.setColor(0xFF34C759);
                markPath.moveTo(30, 76);
                markPath.lineTo(43, 56);
                markPath.lineTo(52, 67);
                markPath.lineTo(60, 58);
                markPath.lineTo(70, 76);
                markPath.close();
                markPath.addCircle(40, 44, 5.5f, Path.Direction.CW);
                break;
            case FileLogic.VIDEO:
                mark.setColor(0xFF5E5CE6);
                markPath.moveTo(40, 44);
                markPath.lineTo(66, 59);
                markPath.lineTo(40, 74);
                markPath.close();
                break;
            case FileLogic.AUDIO:
                mark.setColor(0xFFFF375F);
                markPath.addCircle(38, 72, 7, Path.Direction.CW);
                markPath.addCircle(62, 67, 7, Path.Direction.CW);
                markPath.addRect(42, 42, 45, 72, Path.Direction.CW);
                markPath.addRect(66, 37, 69, 67, Path.Direction.CW);
                markPath.moveTo(42, 42);
                markPath.lineTo(69, 37);
                markPath.lineTo(69, 44);
                markPath.lineTo(42, 49);
                markPath.close();
                break;
            case FileLogic.ARCHIVE:
                mark.setColor(0xFF8E8E93);
                for (int i = 0; i < 6; i++) {
                    markPath.addRect(47, 12 + i * 11, 53, 18 + i * 11, Path.Direction.CW);
                }
                markPath.addRoundRect(new RectF(43, 74, 57, 86), 3, 3, Path.Direction.CW);
                break;
            case FileLogic.APP:
                mark.setColor(0xFF30B94D);
                markPath.addRoundRect(new RectF(32, 44, 68, 78), 7, 7, Path.Direction.CW);
                break;
            case FileLogic.TEXT:
            default:
                mark.setColor(0xFFA2A2A9);
                for (int i = 0; i < 5; i++) {
                    markPath.addRoundRect(new RectF(31, 38 + i * 10, i == 4 ? 54 : 69, 41 + i * 10),
                            1.5f, 1.5f, Path.Direction.CW);
                }
                break;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        int saved = canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.scale(bounds.width() / 100f, bounds.height() / 100f);
        if (kind == FileLogic.FOLDER) {
            box.set(6, 14, 46, 40);
            canvas.drawRoundRect(box, 6, 6, fill);
            box.set(6, 24, 94, 86);
            canvas.drawRoundRect(box, 7, 7, fill);
            box.set(6, 28, 94, 86);
            canvas.drawRoundRect(box, 7, 7, front);
        } else {
            canvas.drawPath(pagePath, fill);
            canvas.drawPath(pagePath, front);
            canvas.drawPath(foldPath, fold);
            canvas.drawPath(markPath, mark);
        }
        canvas.restoreToCount(saved);
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
