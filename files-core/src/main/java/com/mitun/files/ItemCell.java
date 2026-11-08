package com.mitun.files;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import java.text.DateFormat;
import java.util.Date;

/**
 * One item of a folder, as one view that draws everything, in either of Finder's
 * two looks: an icon over a name, or a striped row with columns. A view per label
 * would multiply every visible item by four.
 */
final class ItemCell extends View {
    private final FilesTheme theme;
    private final boolean grid;
    private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint smallPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF box = new RectF();
    private final int iconSize;
    private final float pad;
    private final DateFormat dates;

    /** Bound on the screen's thread, read on the thumbnailer's. */
    private volatile FileLogic.Entry entry;
    private boolean striped;
    private Bitmap thumb;
    private CharSequence shownName = "";
    private String shownDate = "";
    private String shownSize = "";
    private String shownKind = "";

    ItemCell(Context context, FilesTheme theme, boolean grid, DateFormat dates) {
        super(context);
        this.theme = theme;
        this.grid = grid;
        this.dates = dates;
        iconSize = FilesTheme.dp(context, grid ? 62 : 20);
        pad = FilesTheme.dp(context, 8);
        namePaint.setTextSize(FilesTheme.sp(context, grid ? 13 : 13.5f));
        namePaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        smallPaint.setTextSize(FilesTheme.sp(context, grid ? 11 : 12.5f));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec),
                FilesTheme.dp(getContext(), grid ? 116 : 34));
    }

    void bind(FileLogic.Entry entry, boolean striped) {
        this.entry = entry;
        this.striped = striped;
        thumb = null;
        thumbPaint.setShader(null);
        if (entry == null) {
            // A recycled cell for a row that is gone draws nothing, rather than
            // the file it showed last.
            shownName = "";
            setContentDescription(null);
            invalidate();
            return;
        }
        // A file that will not say when it changed gets a dash, not the epoch.
        shownDate = entry.modified <= 0 ? "--" : dates.format(new Date(entry.modified));
        shownSize = entry.folder ? "--" : FileLogic.formatSize(entry.size);
        shownKind = FileLogic.kindName(entry.kind);
        updateName();
        setContentDescription(entry.name + ", " + shownKind
                + (entry.folder ? "" : ", " + shownSize));
        invalidate();
    }

    FileLogic.Entry entry() {
        return entry;
    }

    /** Shows the picture itself in place of the icon. */
    void setThumb(Bitmap bitmap) {
        thumb = bitmap;
        BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float scale = Math.max(iconSize / (float) bitmap.getWidth(),
                iconSize / (float) bitmap.getHeight());
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        // The picture overflows the square on one side: show its middle, not its corner.
        matrix.postTranslate((iconSize - bitmap.getWidth() * scale) / 2f,
                (iconSize - bitmap.getHeight() * scale) / 2f);
        shader.setLocalMatrix(matrix);
        thumbPaint.setShader(shader);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        updateName();
    }

    private void updateName() {
        if (entry == null || getWidth() == 0) {
            return;
        }
        // Finder keeps the end of a long name, where the extension and the episode are.
        float room = grid ? getWidth() - pad : nameColumnEnd() - nameStart() - pad;
        shownName = TextUtils.ellipsize(entry.name, namePaint, room, TextUtils.TruncateAt.MIDDLE);
    }

    private float nameStart() {
        return pad * 2 + iconSize;
    }

    private float nameColumnEnd() {
        return getWidth() * 0.52f;
    }

    private boolean lit() {
        return isSelected() && getParent() instanceof View && ((View) getParent()).hasFocus();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (entry == null) {
            return;
        }
        if (grid) {
            drawGrid(canvas);
        } else {
            drawRow(canvas);
        }
    }

    private void drawGrid(Canvas canvas) {
        int w = getWidth();
        float iconLeft = (w - iconSize) / 2f;
        float iconTop = FilesTheme.dp(getContext(), 8);
        float nameTop = iconTop + iconSize + FilesTheme.dp(getContext(), 7);
        boolean lit = lit();
        if (lit) {
            // Finder's selection: a soft square behind the icon, a blue capsule on the name.
            float around = FilesTheme.dp(getContext(), 5);
            box.set(iconLeft - around, iconTop - around, iconLeft + iconSize + around,
                    iconTop + iconSize + around);
            fillPaint.setColor(theme.hover);
            canvas.drawRoundRect(box, around * 1.4f, around * 1.4f, fillPaint);
        }
        drawIcon(canvas, iconLeft, iconTop);
        float textWidth = namePaint.measureText(shownName, 0, shownName.length());
        float baseline = nameTop - namePaint.ascent();
        if (lit) {
            float inset = FilesTheme.dp(getContext(), 6);
            box.set((w - textWidth) / 2f - inset, nameTop - 1, (w + textWidth) / 2f + inset,
                    nameTop + namePaint.descent() - namePaint.ascent() + 2);
            fillPaint.setColor(theme.accent);
            canvas.drawRoundRect(box, box.height() / 3f, box.height() / 3f, fillPaint);
        }
        namePaint.setColor(lit ? 0xFFFFFFFF : theme.ink);
        canvas.drawText(shownName, 0, shownName.length(), (w - textWidth) / 2f, baseline, namePaint);
        if (!entry.folder) {
            smallPaint.setColor(theme.inkSecondary);
            float small = smallPaint.measureText(shownSize);
            canvas.drawText(shownSize, (w - small) / 2f,
                    baseline + namePaint.descent() - smallPaint.ascent() + 2, smallPaint);
        }
    }

    private void drawRow(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        boolean lit = lit();
        if (lit) {
            box.set(0, 0, w, h);
            fillPaint.setColor(theme.accent);
            float radius = FilesTheme.dp(getContext(), 7);
            canvas.drawRoundRect(box, radius, radius, fillPaint);
        } else if (striped) {
            box.set(0, 0, w, h);
            fillPaint.setColor(theme.stripe);
            float radius = FilesTheme.dp(getContext(), 7);
            canvas.drawRoundRect(box, radius, radius, fillPaint);
        }
        drawIcon(canvas, pad, (h - iconSize) / 2f);
        float baseline = h / 2f - (namePaint.descent() + namePaint.ascent()) / 2f;
        namePaint.setColor(lit ? 0xFFFFFFFF : theme.ink);
        canvas.drawText(shownName, 0, shownName.length(), nameStart(), baseline, namePaint);
        smallPaint.setColor(lit ? 0xE6FFFFFF : theme.inkSecondary);
        drawColumn(canvas, shownDate, w * 0.52f, w * 0.20f, baseline);
        drawColumn(canvas, shownSize, w * 0.72f, w * 0.12f, baseline);
        drawColumn(canvas, shownKind, w * 0.84f, w * 0.16f, baseline);
    }

    private void drawColumn(Canvas canvas, String text, float left, float width, float baseline) {
        CharSequence clipped = TextUtils.ellipsize(text, smallPaint, width - pad,
                TextUtils.TruncateAt.END);
        canvas.drawText(clipped, 0, clipped.length(), left, baseline, smallPaint);
    }

    private void drawIcon(Canvas canvas, float left, float top) {
        if (thumb != null && !thumb.isRecycled()) {
            int saved = canvas.save();
            canvas.translate(left, top);
            box.set(0, 0, iconSize, iconSize);
            float radius = FilesTheme.dp(getContext(), 6);
            canvas.drawRoundRect(box, radius, radius, thumbPaint);
            canvas.restoreToCount(saved);
            return;
        }
        FileIcon icon = FileIcon.of(entry.kind);
        icon.setBounds(Math.round(left), Math.round(top), Math.round(left) + iconSize,
                Math.round(top) + iconSize);
        icon.draw(canvas);
    }
}
