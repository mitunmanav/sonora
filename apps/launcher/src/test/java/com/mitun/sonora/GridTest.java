package com.mitun.sonora;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GridTest {

    @Test
    public void iconsKeepTheirTvOSSizeOnEveryScreen() {
        // A 720p@240dpi box reports 1280dp wide, a 1080p@320dpi box 960dp.
        for (int width : new int[]{640, 853, 960, 1280, 1920}) {
            Grid.Metrics m = Grid.compute(width);
            float ratio = (float) m.tileWidthDp / width;
            // The measured tvOS proportion is 13.0%; rounding may nudge it a little.
            assertTrue("width " + width + " gave ratio " + ratio,
                    ratio > Grid.ICON_WIDTH_RATIO - 0.02f && ratio < Grid.ICON_WIDTH_RATIO + 0.02f);
        }
    }

    @Test
    public void every16by9ScreenGetsTheSameTvOSColumnCount() {
        // tvOS proportions are scale-invariant: a 16:9 screen always shows six
        // icons whatever its resolution or density.
        int expected = Grid.compute(960).columns;
        for (int width : new int[]{853, 960, 1280, 1920}) {
            assertEquals("width " + width, expected, Grid.compute(width).columns);
        }
    }

    @Test
    public void aVeryNarrowScreenDropsColumnsRatherThanShrinkingIcons() {
        Grid.Metrics narrow = Grid.compute(320);
        assertTrue("columns " + narrow.columns, narrow.columns < Grid.compute(960).columns);
        // Four columns is the floor, so at 320dp the icons are as large as the
        // screen allows - no further column can be given up.
        assertEquals(Grid.MIN_COLUMNS, narrow.columns);
    }

    @Test
    public void iconsStayReadableOnTheSmallestRealisticTV() {
        // 640dp is the narrowest a TV ever reports (720p at 240dpi is 853dp).
        Grid.Metrics m = Grid.compute(640);
        assertTrue("icons too small: " + m.tileWidthDp, m.tileWidthDp >= Grid.MIN_TILE_WIDTH_DP);
    }

    @Test
    public void columnsStayWithinTheAllowedRange() {
        assertTrue(Grid.compute(320).columns >= Grid.MIN_COLUMNS);
        assertTrue(Grid.compute(4000).columns <= Grid.MAX_COLUMNS);
    }

    @Test
    public void tilesNeverOverflowTheContentWidth() {
        for (int width : new int[]{640, 960, 1280, 1920}) {
            Grid.Metrics m = Grid.compute(width);
            int content = width - m.marginDp * 2;
            int used = m.tileWidthDp * m.columns + m.gapDp * (m.columns - 1);
            assertTrue("overflow at " + width + ": " + used + " > " + content, used <= content);
        }
    }

    @Test
    public void theFirstRowStaysInTheDock() {
        assertEquals(0f, Grid.scrollFor(0, 700f, 260f), 0.001f);
    }

    @Test
    public void anyOtherRowOpensTheGridAndOnlyScrollsPastWhatFits() {
        assertEquals(-700f, Grid.scrollFor(1, 700f, 260f), 0.001f);
        assertEquals(-700f, Grid.scrollFor(3, 700f, 260f), 0.001f);
        assertEquals(-960f, Grid.scrollFor(4, 700f, 260f), 0.001f);
        assertEquals(-1220f, Grid.scrollFor(5, 700f, 260f), 0.001f);
    }

    @Test
    public void openProgressFollowsTheDockAndStopsAtBothEnds() {
        assertEquals(0f, Grid.openProgress(0f, 700f), 0.001f);
        assertEquals(0.5f, Grid.openProgress(-350f, 700f), 0.001f);
        assertEquals(1f, Grid.openProgress(-700f, 700f), 0.001f);
        assertEquals(1f, Grid.openProgress(-1500f, 700f), 0.001f);
    }

    @Test
    public void tileHeightIsFiveByThree() {
        Grid.Metrics m = Grid.compute(960);
        assertEquals(Math.round(m.tileWidthDp * 0.6f), m.tileHeightDp);
    }

    @Test
    public void aStoredColumnCountOverridesTheAutomaticOne() {
        Grid.Metrics auto = Grid.compute(960);
        Grid.Metrics fixed = Grid.resolve(960, auto.columns);
        assertEquals(auto.columns, fixed.columns);
        assertEquals(auto.tileWidthDp, fixed.tileWidthDp);
    }

    @Test
    public void anOutOfRangeStoredCountFallsBackToAutomatic() {
        Grid.Metrics auto = Grid.compute(960);
        assertEquals(auto.columns, Grid.resolve(960, 99).columns);
        assertEquals(auto.columns, Grid.resolve(960, 0).columns);
    }

    @Test
    public void focusWrapsBetweenRowsInsteadOfStoppingAtTheColumnEdge() {
        // Six columns, twelve tiles. Leaving the last column enters the next
        // row; leaving the first column steps back to the previous row.
        assertEquals(6, Grid.moveTarget(5, 6, 12, 22));
        assertEquals(5, Grid.moveTarget(6, 6, 12, 21));
        assertEquals(12, Grid.moveTarget(11, 6, 18, 22));
        assertEquals(11, Grid.moveTarget(12, 6, 18, 21));
        // The first and last tile are the only real ends of the grid.
        assertEquals(-1, Grid.moveTarget(0, 6, 12, 21));
        assertEquals(-1, Grid.moveTarget(11, 6, 12, 22));
    }

    @Test
    public void focusHandlesAPartialLastRow() {
        assertEquals(6, Grid.moveTarget(5, 6, 8, 22));
        assertEquals(5, Grid.moveTarget(6, 6, 8, 21));
        assertEquals(7, Grid.moveTarget(1, 6, 8, 20));
        assertEquals(-1, Grid.moveTarget(7, 6, 8, 22));
        assertEquals(-1, Grid.moveTarget(2, 6, 8, 20));
    }

    @Test
    public void focusStaysInTheColumnWhenMovingUpAndDown() {
        assertEquals(0, Grid.moveTarget(6, 6, 13, 19));
        assertEquals(6, Grid.moveTarget(0, 6, 13, 20));
        assertEquals(-1, Grid.moveTarget(0, 6, 13, 19));
        assertEquals(-1, Grid.moveTarget(6, 6, 12, 20));
    }

    @Test
    public void focusIgnoresAbsurdInput() {
        assertEquals(-1, Grid.moveTarget(-1, 6, 12, 22));
        assertEquals(-1, Grid.moveTarget(12, 6, 12, 22));
        assertEquals(-1, Grid.moveTarget(2, 0, 12, 22));
    }

    @Test
    public void survivesAbsurdScreenSizes() {
        assertTrue(Grid.compute(1).tileWidthDp >= 1);
        assertTrue(Grid.compute(100000).columns <= Grid.MAX_COLUMNS);
    }
}
