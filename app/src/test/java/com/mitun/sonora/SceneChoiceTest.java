package com.mitun.sonora;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class SceneChoiceTest {
    private static final boolean[] NONE = {false, false, false};

    @Test
    public void startsAtTheFirstSceneWithNothingHidden() {
        assertEquals(0, SceneChoice.next(-1, 1, NONE));
    }

    @Test
    public void wrapsRoundBothEnds() {
        assertEquals(0, SceneChoice.next(2, 1, NONE));
        assertEquals(2, SceneChoice.next(0, -1, NONE));
    }

    @Test
    public void skipsHiddenScenesInBothDirections() {
        boolean[] hidden = {false, true, false};
        assertEquals(2, SceneChoice.next(0, 1, hidden));
        assertEquals(0, SceneChoice.next(2, 1, hidden));
        assertEquals(0, SceneChoice.next(2, -1, hidden));
    }

    @Test
    public void aHiddenFirstSceneIsNotTheFirstShown() {
        assertEquals(1, SceneChoice.next(-1, 1, new boolean[]{true, false, false}));
    }

    @Test
    public void staysOnTheOnlySceneLeft() {
        assertEquals(1, SceneChoice.next(1, 1, new boolean[]{true, false, true}));
    }

    @Test
    public void hidingEverySceneStillShowsThemAll() {
        boolean[] hidden = {true, true, true};
        assertTrue(SceneChoice.allHidden(hidden));
        assertEquals(1, SceneChoice.next(0, 1, hidden));
    }

    @Test
    public void noScenesMeansNothingToShow() {
        assertEquals(-1, SceneChoice.next(-1, 1, new boolean[0]));
        assertEquals(-1, SceneChoice.next(0, 1, null));
        assertFalse(SceneChoice.allHidden(new boolean[0]));
    }

    @Test
    public void anOutOfRangeCurrentStillFindsAScene() {
        // A stale index deeper than -1 must not turn into a negative slot.
        assertEquals(0, SceneChoice.next(-10, 1, NONE));
        assertEquals(0, SceneChoice.next(10, 1, NONE));
        assertEquals(2, SceneChoice.next(-10, -1, NONE));
    }

    @Test
    public void listsTheScenesOfARegion() {
        String[] regions = {"Africa", "Europe", "Asia", "Europe"};
        assertEquals(Arrays.asList(1, 3), SceneChoice.inRegion(regions, "Europe"));
        assertEquals(Arrays.asList(0, 1, 2, 3), SceneChoice.inRegion(regions, null));
        assertEquals(Collections.emptyList(), SceneChoice.inRegion(regions, "Oceania"));
    }
}
