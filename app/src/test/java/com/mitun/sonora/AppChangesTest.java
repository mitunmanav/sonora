package com.mitun.sonora;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppChangesTest {
    @Test
    public void aMovedSequenceMeansTheListIsStale() {
        assertTrue(AppChanges.moved(4, 5));
        assertFalse(AppChanges.moved(4, 4));
    }

    @Test
    public void beforeTheFirstReadNothingCounts() {
        assertFalse(AppChanges.moved(-1, 7));
    }
}
