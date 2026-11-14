package com.mitun.sonora;

/**
 * Grid geometry for the home screen. Pure maths, no Android types, so it is
 * unit-testable and correct on every screen from a 720p stick to a 4K panel.
 *
 * tvOS keeps the app icons a fixed fraction of the screen width and lets the
 * column count follow, so we do the same. The proportions below were measured
 * off Apple's own tvOS 26 press render (1960x1102): a 5:3 icon is 13.0% of the
 * screen width, the screen margin is 4.7% each side and the gutter 2.55%.
 *
 * Nothing here is hard-coded to a resolution. A 720p@240dpi box reports
 * 1280x720dp while a 1080p@320dpi box reports 960x540dp, and both must land on
 * the same layout.
 */
public final class Grid {
    /** tvOS app icons are 5:3 landscape. */
    public static final float ASPECT = 0.6f;

    /** Icon width as a fraction of the screen width, measured from tvOS 26. */
    public static final float ICON_WIDTH_RATIO = 0.130f;
    /** Screen margin on each side, as a fraction of the screen width. */
    public static final float MARGIN_RATIO = 0.047f;
    /** The inset of the glass dock from the screen edge. */
    public static final float DOCK_MARGIN_RATIO = 0.0204f;
    /** Gutter between icons, as a fraction of the screen width. */
    public static final float GAP_RATIO = 0.0255f;

    public static final int MIN_COLUMNS = 4;
    public static final int MAX_COLUMNS = 8;
    /** Below this icon width the labels stop being readable, so drop a column. */
    public static final int MIN_TILE_WIDTH_DP = 80;

    private Grid() {
    }

    public static final class Metrics {
        public final int columns;
        public final int tileWidthDp;
        public final int tileHeightDp;
        public final int marginDp;
        public final int gapDp;

        Metrics(int columns, int tileWidthDp, int tileHeightDp, int marginDp, int gapDp) {
            this.columns = columns;
            this.tileWidthDp = tileWidthDp;
            this.tileHeightDp = tileHeightDp;
            this.marginDp = marginDp;
            this.gapDp = gapDp;
        }
    }

    /**
     * Picks the column count that keeps icons at their tvOS size: the fewest
     * icons that fill the width without shrinking them, so a wide screen shows
     * more rather than bigger icons.
     */
    public static Metrics compute(int widthDp) {
        return compute(widthDp, MIN_COLUMNS, MAX_COLUMNS);
    }

    public static Metrics compute(int widthDp, int minColumns, int maxColumns) {
        int margin = Math.round(widthDp * MARGIN_RATIO);
        int gap = Math.max(1, Math.round(widthDp * GAP_RATIO));
        int content = Math.max(1, widthDp - margin * 2);
        int preferred = Math.max(1, Math.round(widthDp * ICON_WIDTH_RATIO));

        int columns = Math.round((content + gap) / (float) (preferred + gap));
        columns = Math.max(minColumns, Math.min(maxColumns, columns));

        // tvOS proportions are scale-invariant, so a 16:9 screen always lands on
        // the same column count. On a screen too narrow for that, drop columns
        // until the icons are big enough to read rather than shrink them.
        int tileWidth = (content - gap * (columns - 1)) / columns;
        while (columns > minColumns && tileWidth < MIN_TILE_WIDTH_DP) {
            columns--;
            tileWidth = (content - gap * (columns - 1)) / columns;
        }

        int tileHeight = Math.round(tileWidth * ASPECT);
        return new Metrics(columns, Math.max(1, tileWidth), Math.max(1, tileHeight), margin, gap);
    }

    /**
     * Pure: the tile a D-pad key moves to from {@code index}, or -1 when the
     * edge is reached. Moving off the last column wraps to the next row, so
     * the middle columns are never a dead end.
     */
    public static int moveTarget(int index, int columns, int count, int keyCode) {
        if (index < 0 || columns < 1 || index >= count) {
            return -1;
        }
        // KeyEvent key codes, kept as numbers so this class stays Android-free.
        // 21 LEFT, 22 RIGHT, 19 UP, 20 DOWN.
        int row = index / columns;
        int column = index % columns;
        switch (keyCode) {
            case 21:
                if (column > 0) {
                    return index - 1;
                }
                return row > 0 ? Math.min(row * columns - 1, count - 1) : -1;
            case 22:
                if (column + 1 < columns && index + 1 < count) {
                    return index + 1;
                }
                int nextRow = (row + 1) * columns;
                return nextRow < count ? nextRow : -1;
            case 19:
                return row > 0 ? index - columns : -1;
            case 20:
                return index + columns < count ? index + columns : -1;
            default:
                return -1;
        }
    }

    /** How many rows fit under the dock once the grid is open, before it scrolls. */
    private static final int OPEN_ROWS = 3;

    /**
     * Pure: how far the dock and the rows slide for the focus to be on a row.
     *
     * The first row sits in the dock, over the wallpaper. Any other row opens
     * the grid: the dock rides up to the top of the screen, and the list only
     * scrolls further once the focus has passed what fits under it.
     *
     * @param dockTravel how far the dock moves up to reach the top of the screen
     * @param openPitch  the distance between two rows in the open grid
     */
    public static float scrollFor(int row, float dockTravel, float openPitch) {
        if (row <= 0) {
            return 0f;
        }
        return -(dockTravel + Math.max(0, row - OPEN_ROWS) * openPitch);
    }

    /** Pure: how open the grid is at a scroll position, from 0 to 1. */
    public static float openProgress(float scroll, float dockTravel) {
        if (dockTravel <= 0f) {
            return scroll < 0f ? 1f : 0f;
        }
        return Math.max(0f, Math.min(1f, -scroll / dockTravel));
    }

    /**
     * Honours a stored column count. A stored value of zero means "fit to the
     * screen", which is the default.
     */
    public static Metrics resolve(int widthDp, int storedColumns) {
        if (storedColumns >= MIN_COLUMNS && storedColumns <= MAX_COLUMNS) {
            Metrics auto = compute(widthDp);
            int margin = auto.marginDp;
            int gap = auto.gapDp;
            int content = Math.max(1, widthDp - margin * 2);
            int tileWidth = (content - gap * (storedColumns - 1)) / storedColumns;
            int tileHeight = Math.round(tileWidth * ASPECT);
            return new Metrics(storedColumns, Math.max(1, tileWidth), Math.max(1, tileHeight),
                    margin, gap);
        }
        return compute(widthDp);
    }
}
