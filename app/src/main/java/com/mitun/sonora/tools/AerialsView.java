package com.mitun.sonora.tools;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.util.TypedValue;
import android.view.FocusFinder;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mitun.sonora.Prefs;
import com.mitun.sonora.SceneChoice;
import com.mitun.sonora.Scenes;
import com.mitun.sonora.ui.BackdropView;
import com.mitun.sonora.ui.Glyph;
import com.mitun.sonora.ui.Motion;
import com.mitun.sonora.ui.RowDrawable;
import com.mitun.sonora.ui.ScreenStack;
import com.mitun.sonora.ui.Theme;
import com.mitun.sonora.ui.Type;
import com.mitun.sonora.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Choose Aerials, measured from tvOS 26: categories down the left, three columns
 * of pictures on the right, and one button that hides or shows them all. OK on a
 * picture leaves it out of the screen saver, or brings it back.
 *
 * It keeps a small copy of each picture and lets them go when it closes.
 */
final class AerialsView extends FrameLayout {
    /** A row of tvOS's picture grid, at 1920x1080: 409x231 on a 364 pitch. */
    private static final int THUMB_WIDTH_DP = 204;
    private static final int THUMB_HEIGHT_DP = 115;
    private static final int THUMB_GAP_DP = 33;
    private static final int THUMB_RADIUS_DP = 9;
    private static final int ROW_PITCH_DP = 182;
    private static final int GRID_TOP_DP = 142;
    private static final int GRID_END_DP = 40;
    private static final int COLUMNS = 3;
    private static final float THUMB_FOCUS_SCALE = 1.1f;
    private static final float HIDDEN_ALPHA = 0.4f;

    private static final int SIDE_START_DP = 40;
    private static final int SIDE_WIDTH_DP = 146;
    private static final int SIDE_ROW_DP = 33;
    private static final float SIDE_PITCH_DP = 35.5f;
    private static final int SIDE_CENTER_FIRST_DP = 159;
    private static final float SIDE_FOCUS_SCALE = 1.05f;

    private final Prefs prefs;
    private final boolean[] hidden;
    private final Bitmap[] thumbs = new Bitmap[Scenes.ALL.size()];
    private final List<SideRow> sides = new ArrayList<>();
    private final List<Tile> tiles = new ArrayList<>();
    private final LinearLayout grid;
    private final TextView hideAll;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sonora-aerial-thumbs");
        thread.setDaemon(true);
        return thread;
    });
    private SideRow selected;

    AerialsView(Context context, Prefs prefs) {
        super(context);
        this.prefs = prefs;
        hidden = Scenes.hiddenFlags(prefs);
        setClipChildren(false);
        // Unlike a settings page this has no mark on its left, so it brings a
        // wallpaper of its own to cover the one the activity draws.
        BackdropView backdrop = new BackdropView(context);
        addView(backdrop, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        loadBackdrop(backdrop);

        TextView title = new TextView(context);
        title.setText("Choose Aerials");
        title.setTextColor(Theme.ink());
        Type.apply(title, Type.DISPLAY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, ScreenStack.PAGE_TITLE_SP);
        title.setGravity(Gravity.CENTER);
        LayoutParams titleParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT, Gravity.TOP);
        titleParams.topMargin = dp(ScreenStack.PAGE_TITLE_TOP_DP);
        addView(title, titleParams);

        TextView subtitle = new TextView(context);
        // The toggles only reach the bundled pictures. When the screen saver
        // plays the TV's own photos the screen says so, and that they cannot
        // be hidden here, instead of offering switches that do nothing.
        int photoSource = prefs.screensaverPhotoSource();
        subtitle.setText(photoSource == 1
                ? "The screen saver is playing the photos on this TV, not these "
                    + "pictures. Photos always rotate and cannot be hidden here; "
                    + "Photo source in the screen saver settings chooses what plays."
                : photoSource == 2
                    ? "The screen saver rotates through these pictures and the photos "
                        + "on this TV. Hide any pictures you don't want; the photos "
                        + "always play and cannot be hidden here."
                    : "The screen saver rotates through these pictures. Hide any you "
                        + "don't want. If you hide them all, it shows them all.");
        subtitle.setTextColor(Theme.inkSecondary());
        Type.apply(subtitle, Type.CAPTION);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        subtitle.setLineSpacing(0f, 1.1f);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        LayoutParams subtitleParams = new LayoutParams(dp(456), LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        subtitleParams.topMargin = dp(70);
        addView(subtitle, subtitleParams);

        hideAll = new HideAllButton(context);
        LayoutParams hideParams = new LayoutParams(dp(120), dp(29), Gravity.TOP | Gravity.END);
        hideParams.topMargin = dp(63);
        hideParams.setMarginEnd(dp(GRID_END_DP));
        addView(hideAll, hideParams);

        addSide(context, "All", null, 0);
        addSide(context, Scenes.AFRICA, Scenes.AFRICA, 1);
        addSide(context, Scenes.ASIA, Scenes.ASIA, 2);
        addSide(context, Scenes.EUROPE, Scenes.EUROPE, 3);

        ScrollView scroller = new ScrollView(context);
        scroller.setFocusable(false);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        // Room for a focused picture to grow past the edge of the scroller.
        scroller.setPadding(dp(14), dp(14), dp(14), dp(14));
        grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        scroller.addView(grid, new ScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        LayoutParams scrollerParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT, Gravity.TOP | Gravity.END);
        scrollerParams.topMargin = dp(GRID_TOP_DP - 14);
        scrollerParams.setMarginEnd(dp(GRID_END_DP - 14));
        addView(scroller, scrollerParams);

        selected = sides.get(0);
        selected.setSelectedCategory(true);
        showCategory(null);
        refreshHideAll();
        loadThumbs();
    }

    private void addSide(Context context, String label, String region, int index) {
        SideRow row = new SideRow(context, label, region);
        LayoutParams params = new LayoutParams(dp(SIDE_WIDTH_DP), dp(SIDE_ROW_DP),
                Gravity.TOP | Gravity.START);
        params.setMarginStart(dp(SIDE_START_DP));
        params.topMargin = Math.round(Ui.dpf(context, SIDE_CENTER_FIRST_DP
                + index * SIDE_PITCH_DP - SIDE_ROW_DP / 2f));
        addView(row, params);
        sides.add(row);
    }

    private void showCategory(String region) {
        grid.removeAllViews();
        tiles.clear();
        List<Integer> scenes = SceneChoice.inRegion(Scenes.regions(), region);
        if (scenes.isEmpty()) {
            // An empty grid would be a silent dead end for RIGHT.
            TextView empty = new TextView(getContext());
            empty.setText("No pictures in this category");
            empty.setTextColor(Theme.inkSecondary());
            Type.apply(empty, Type.CAPTION);
            grid.addView(empty, new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            return;
        }
        LinearLayout row = null;
        for (int i = 0; i < scenes.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                grid.addView(row, new LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, dp(ROW_PITCH_DP)));
            }
            Tile tile = new Tile(getContext(), scenes.get(i));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(THUMB_WIDTH_DP), LayoutParams.MATCH_PARENT);
            if (i % COLUMNS > 0) {
                params.setMarginStart(dp(THUMB_GAP_DP));
            }
            row.addView(tile, params);
            tiles.add(tile);
        }
        // A short row still has to fill its width, or the columns would drift.
        if (row != null) {
            int left = COLUMNS - (scenes.size() % COLUMNS == 0 ? COLUMNS : scenes.size() % COLUMNS);
            for (int i = 0; i < left; i++) {
                LinearLayout.LayoutParams spacer = new LinearLayout.LayoutParams(
                        dp(THUMB_WIDTH_DP), LayoutParams.MATCH_PARENT);
                spacer.setMarginStart(dp(THUMB_GAP_DP));
                row.addView(new View(getContext()), spacer);
            }
        }
    }

    /**
     * Hands the backdrop its blur. The view only takes the shared one the
     * launcher already made; a miss is decoded on the thumbnail worker.
     */
    private void loadBackdrop(final BackdropView backdrop) {
        final int index = prefs.wallpaper();
        final String uri = prefs.wallpaperUri();
        final String key = BackdropView.key(index, uri);
        Bitmap ready = BackdropView.remembered(key);
        if (ready != null) {
            backdrop.setSoftened(ready);
            return;
        }
        decoder.execute(() -> {
            Bitmap soft = null;
            try {
                Bitmap decoded = null;
                if (index < 0 && uri != null) {
                    decoded = Ui.decodeUri(getResources(),
                            getContext().getContentResolver(), Uri.parse(uri), 96);
                }
                if (decoded == null) {
                    decoded = Ui.decodeScene(getResources(),
                            Scenes.ALL.get(Scenes.safeIndex(index)).res, 96, 54);
                }
                soft = BackdropView.soften(decoded);
                if (decoded != null) {
                    decoded.recycle();
                }
            } catch (RuntimeException | OutOfMemoryError ignored) {
            }
            if (soft == null) {
                return;
            }
            BackdropView.remember(key, soft);
            final Bitmap blur = soft;
            post(() -> {
                if (!decoder.isShutdown()) {
                    backdrop.setSoftened(blur);
                }
            });
        });
    }

    private void loadThumbs() {
        final int width = Math.round(dp(THUMB_WIDTH_DP) * THUMB_FOCUS_SCALE);
        final int height = Math.round(dp(THUMB_HEIGHT_DP) * THUMB_FOCUS_SCALE);
        for (int i = 0; i < thumbs.length; i++) {
            final int index = i;
            decoder.execute(() -> {
                final Bitmap thumb = decodeThumb(index, width, height);
                post(() -> {
                    if (decoder.isShutdown()) {
                        // The screen closed while this one was decoding.
                        return;
                    }
                    thumbs[index] = thumb;
                    for (Tile tile : tiles) {
                        if (tile.scene == index) {
                            tile.setThumb(thumb);
                        }
                    }
                });
            });
        }
    }

    private Bitmap decodeThumb(int index, int width, int height) {
        try {
            Bitmap full = Ui.decodeScene(getResources(), Scenes.ALL.get(index).res, width, height);
            if (full == null) {
                return null;
            }
            float cover = Math.max(width / (float) full.getWidth(),
                    height / (float) full.getHeight());
            int cropWidth = Math.round(width / cover);
            int cropHeight = Math.round(height / cover);
            Bitmap thumb = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Rect source = new Rect((full.getWidth() - cropWidth) / 2,
                    (full.getHeight() - cropHeight) / 2,
                    (full.getWidth() + cropWidth) / 2,
                    (full.getHeight() + cropHeight) / 2);
            new Canvas(thumb).drawBitmap(full, source, new Rect(0, 0, width, height),
                    new Paint(Paint.FILTER_BITMAP_FLAG));
            full.recycle();
            return thumb;
        } catch (RuntimeException | OutOfMemoryError e) {
            // The name still shows; a TV short of memory only loses the picture.
            return null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        decoder.shutdownNow();
        super.onDetachedFromWindow();
    }

    /** The screen opens on the first category, whatever is nearest the top. */
    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocused) {
        return selected.requestFocus();
    }

    /** Keeps the focus here, and sends LEFT from the pictures to the open category. */
    @Override
    public View focusSearch(View focused, int direction) {
        View next = FocusFinder.getInstance().findNextFocus(this, focused, direction);
        if (direction == View.FOCUS_LEFT && focused instanceof Tile
                && (next == null || next instanceof SideRow)) {
            return selected;
        }
        return next != null ? next : focused;
    }

    private void toggle(int scene) {
        hidden[scene] = !hidden[scene];
        save();
    }

    private void setAll(boolean hide) {
        for (int i = 0; i < hidden.length; i++) {
            hidden[i] = hide;
        }
        save();
    }

    private void save() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < hidden.length; i++) {
            if (hidden[i]) {
                names.add(Scenes.ALL.get(i).name);
            }
        }
        prefs.setHiddenScenes(names);
        for (Tile tile : tiles) {
            tile.refreshHidden();
        }
        refreshHideAll();
    }

    private void refreshHideAll() {
        hideAll.setText(SceneChoice.allHidden(hidden) ? "Show All" : "Hide All");
    }

    private int dp(float value) {
        return Ui.dp(getContext(), value);
    }

    /** A pill that animates from its rest shade to white under the focus. */
    private final class HideAllButton extends TextView {
        private final RowDrawable material;
        private float focus;

        HideAllButton(Context context) {
            super(context);
            material = new RowDrawable(context);
            setBackground(material);
            setGravity(Gravity.CENTER);
            Type.apply(this, Type.CAPTION);
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            setIncludeFontPadding(false);
            setTextColor(Theme.ink());
            setFocusable(true);
            setClickable(true);
            setOnClickListener(v -> setAll(!SceneChoice.allHidden(hidden)));
            setOnFocusChangeListener((v, hasFocus) -> {
                Motion.scale(this, hasFocus ? SIDE_FOCUS_SCALE : 1f, Motion.DAMPING_CRITICAL,
                        Motion.RESPONSE_FOCUS);
                Motion.springCustom(this, 0, hasFocus ? 1f : 0f, Motion.DAMPING_CRITICAL,
                        Motion.RESPONSE_FOCUS, new Motion.FloatProperty() {
                            @Override
                            public float get() {
                                return focus;
                            }

                            @Override
                            public void set(float value) {
                                focus = value;
                                material.setFocus(value);
                                setTextColor(Ui.mix(Theme.ink(), Ui.INK_ON_LIT, value));
                            }
                        });
            });
        }
    }

    /**
     * One category. Bare text at rest, a white pill under the focus, and a grey
     * pill while the focus is out in the pictures of the category it opened.
     */
    private final class SideRow extends TextView {
        private static final int OPEN_FILL = 0xA6FFFFFF;

        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF pill = new RectF();
        private float focus;
        private boolean open;

        SideRow(Context context, String label, String region) {
            super(context);
            setText(label);
            Type.apply(this, Type.CAPTION);
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(12), 0, dp(12), 0);
            setIncludeFontPadding(false);
            setTextColor(Theme.ink());
            setFocusable(true);
            setClickable(true);
            setOnClickListener(v -> {
                if (selected != this) {
                    selected.setSelectedCategory(false);
                    selected = this;
                    setSelectedCategory(true);
                    showCategory(region);
                }
            });
            setOnFocusChangeListener((v, hasFocus) -> {
                Motion.scale(this, hasFocus ? SIDE_FOCUS_SCALE : 1f, Motion.DAMPING_CRITICAL,
                        Motion.RESPONSE_FOCUS);
                setElevation(Ui.dpf(context, hasFocus ? 8f : 0f));
                Motion.springCustom(this, 0, hasFocus ? 1f : 0f, Motion.DAMPING_CRITICAL,
                        Motion.RESPONSE_FOCUS, new Motion.FloatProperty() {
                            @Override
                            public float get() {
                                return focus;
                            }

                            @Override
                            public void set(float value) {
                                focus = value;
                                applyInk();
                                invalidate();
                            }
                        });
            });
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                            view.getHeight() / 2f);
                }
            });
        }

        void setSelectedCategory(boolean value) {
            open = value;
            applyInk();
            invalidate();
        }

        private void applyInk() {
            float lit = open ? 1f : focus;
            setTextColor(Ui.mix(Theme.ink(), Ui.INK_ON_LIT, lit));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            pill.set(0, 0, getWidth(), getHeight());
            float radius = getHeight() / 2f;
            if (open) {
                fill.setColor(OPEN_FILL);
                canvas.drawRoundRect(pill, radius, radius, fill);
            }
            if (focus > 0f) {
                fill.setColor(Color.WHITE);
                fill.setAlpha(Math.round(255 * focus));
                canvas.drawRoundRect(pill, radius, radius, fill);
            }
            super.onDraw(canvas);
        }
    }

    /** One picture with its name under it. OK leaves it out or brings it back. */
    private final class Tile extends FrameLayout {
        final int scene;
        private final ImageView picture;
        private final ImageView badge;
        private final TextView name;
        private final FrameLayout frame;

        Tile(Context context, int scene) {
            super(context);
            this.scene = scene;
            setClipChildren(false);
            setClipToPadding(false);
            setFocusable(true);
            setClickable(true);

            frame = new FrameLayout(context);
            frame.setBackgroundColor(0x26FFFFFF);
            frame.setClipToOutline(true);
            frame.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                            Ui.dpf(view.getContext(), THUMB_RADIUS_DP));
                }
            });
            addView(frame, new LayoutParams(LayoutParams.MATCH_PARENT, dp(THUMB_HEIGHT_DP),
                    Gravity.TOP));

            picture = new ImageView(context);
            picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
            frame.addView(picture, new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            badge = new ImageView(context);
            Glyph eye = new Glyph(Glyph.HIDDEN);
            eye.setInk(Ui.INK);
            badge.setImageDrawable(eye);
            badge.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LayoutParams badgeParams = new LayoutParams(dp(18), dp(18),
                    Gravity.BOTTOM | Gravity.END);
            badgeParams.setMargins(0, 0, dp(8), dp(8));
            frame.addView(badge, badgeParams);

            name = new TextView(context);
            name.setText(Scenes.ALL.get(scene).name);
            name.setTextColor(Theme.inkSecondary());
            Type.apply(name, Type.CAPTION);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            name.setGravity(Gravity.CENTER_HORIZONTAL);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LayoutParams nameParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT, Gravity.TOP);
            nameParams.topMargin = dp(THUMB_HEIGHT_DP + 5);
            addView(name, nameParams);

            setThumb(thumbs[scene]);
            refreshHidden();
            setOnClickListener(v -> toggle(scene));
            setOnFocusChangeListener((v, hasFocus) -> {
                // The picture grows from its centre, and its name moves down
                // clear of it and brightens.
                Motion.scale(frame, hasFocus ? THUMB_FOCUS_SCALE : 1f, Motion.DAMPING_CRITICAL,
                        Motion.RESPONSE_FOCUS);
                frame.setElevation(Ui.dpf(context, hasFocus ? 14f : 0f));
                Motion.spring(name, Motion.TRANSLATION_Y,
                        hasFocus ? Ui.dpf(context, 7f) : 0f, Motion.DAMPING_CRITICAL,
                        Motion.RESPONSE_FOCUS);
                name.setTextColor(hasFocus ? Theme.ink() : Theme.inkSecondary());
            });
        }

        void setThumb(Bitmap bitmap) {
            picture.setImageBitmap(bitmap);
        }

        void refreshHidden() {
            boolean off = hidden[scene];
            picture.setAlpha(off ? HIDDEN_ALPHA : 1f);
            badge.setVisibility(off ? View.VISIBLE : View.GONE);
            setContentDescription(Scenes.ALL.get(scene).name + (off ? ", hidden" : ", shown"));
        }
    }
}
