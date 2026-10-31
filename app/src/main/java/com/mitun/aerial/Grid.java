package com.mitun.aerial;

/**
 * Grid geometry for the home screen. Pure maths, no Android types, so it is
 * unit-testable and works for every screen size from a 720p stick to a 4K TV.
 *
 * tvOS keeps the app icons a constant physical size and fits as many as the
 * width allows, so we do the same: pick a column count whose tile width lands
 * in a comfortable range rather than hard-coding a count.
 */
public final class Grid {
    /** tvOS app icons are 5:3 landscape. */
    public static final float ASPECT = 0.6f;

    private Grid() {
    }

    public static final class Metrics {
        public final int columns;
        public final int tileWidthDp;
        public final int tileHeightDp;

        Metrics(int columns, int tileWidthDp, int tileHeightDp) {
            this.columns = columns;
            this.tileWidthDp = tileWidthDp;
            this.tileHeightDp = tileHeightDp;
        }
    }

    /**
     * @param widthDp      usable width of the screen in dp
     * @param paddingDp    screen edge inset
     * @param gapDp        space between icons
     * @param preferredDp  the icon width we would like to land on
     * @param minColumns   fewest icons we will show
     * @param maxColumns   most icons we will show
     */
    public static Metrics compute(int widthDp, int paddingDp, int gapDp, int preferredDp,
                                  int minColumns, int maxColumns) {
        int min = Math.max(1, minColumns);
        int max = Math.max(min, maxColumns);
        int content = Math.max(1, widthDp - paddingDp * 2);
        // width = (content - gap * (n - 1)) / n  ->  solve for n
        int columns = Math.round((content + gapDp) / (float) (preferredDp + gapDp));
        columns = Math.max(min, Math.min(max, columns));
        int tileWidth = (content - gapDp * (columns - 1)) / columns;
        int tileHeight = Math.round(tileWidth * ASPECT);
        return new Metrics(columns, Math.max(1, tileWidth), Math.max(1, tileHeight));
    }

    /**
     * Columns for a screen width, given the user's choice. A stored choice of
     * zero means "fit to screen".
     */
    public static Metrics resolve(int widthDp, int paddingDp, int gapDp, int preferredDp,
                                  int storedColumns, int minColumns, int maxColumns) {
        if (storedColumns >= minColumns && storedColumns <= maxColumns) {
            int content = Math.max(1, widthDp - paddingDp * 2);
            int tileWidth = (content - gapDp * (storedColumns - 1)) / storedColumns;
            int tileHeight = Math.round(tileWidth * ASPECT);
            return new Metrics(storedColumns, Math.max(1, tileWidth), Math.max(1, tileHeight));
        }
        return compute(widthDp, paddingDp, gapDp, preferredDp, minColumns, maxColumns);
    }
}
