package com.mitun.aerial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GridTest {

    @Test
    public void fitsMoreColumnsOnWiderScreens() {
        Grid.Metrics hd = Grid.compute(960, 44, 14, 140, 4, 8);
        Grid.Metrics wide = Grid.compute(1280, 44, 14, 140, 4, 8);
        assertTrue("wider screen should hold more icons", wide.columns > hd.columns);
    }

    @Test
    public void clampsToTheAllowedRange() {
        assertEquals(4, Grid.compute(320, 44, 14, 140, 4, 8).columns);
        assertEquals(8, Grid.compute(4000, 44, 14, 140, 4, 8).columns);
    }

    @Test
    public void tileWidthFillsTheContentWidth() {
        int widthDp = 960, paddingDp = 44, gapDp = 14;
        Grid.Metrics m = Grid.compute(widthDp, paddingDp, gapDp, 140, 4, 8);
        int content = widthDp - paddingDp * 2;
        int used = m.tileWidthDp * m.columns + gapDp * (m.columns - 1);
        // Never overflow the content width, and never leave more than one gap unused.
        assertTrue("tiles overflow: " + used + " > " + content, used <= content);
        assertTrue("tiles do not fill: " + used + " vs " + content, used > content - gapDp);
    }

    @Test
    public void tileHeightIsFiveByThree() {
        Grid.Metrics m = Grid.compute(960, 44, 14, 140, 4, 8);
        assertEquals(Math.round(m.tileWidthDp * 0.6f), m.tileHeightDp);
    }

    @Test
    public void aStoredColumnCountOverridesTheAutomaticOne() {
        Grid.Metrics auto = Grid.compute(960, 44, 14, 140, 4, 8);
        Grid.Metrics fixed = Grid.resolve(960, 44, 14, 140, auto.columns, 4, 8);
        assertEquals(auto.columns, fixed.columns);
        assertEquals(auto.tileWidthDp, fixed.tileWidthDp);
    }

    @Test
    public void anOutOfRangeStoredCountFallsBackToAutomatic() {
        Grid.Metrics auto = Grid.compute(960, 44, 14, 140, 4, 8);
        Grid.Metrics fixed = Grid.resolve(960, 44, 14, 140, 99, 4, 8);
        assertEquals(auto.columns, fixed.columns);
    }

    @Test
    public void survivesAbsurdScreenSizes() {
        assertTrue(Grid.compute(1, 44, 14, 140, 4, 8).tileWidthDp >= 1);
        assertTrue(Grid.compute(100000, 44, 14, 140, 4, 8).columns <= 8);
    }
}
