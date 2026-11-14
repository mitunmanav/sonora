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
            // The measured tvOS proportion is 14.7%; rounding may nudge it a little.
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
    public void survivesAbsurdScreenSizes() {
        assertTrue(Grid.compute(1).tileWidthDp >= 1);
        assertTrue(Grid.compute(100000).columns <= Grid.MAX_COLUMNS);
    }
}
