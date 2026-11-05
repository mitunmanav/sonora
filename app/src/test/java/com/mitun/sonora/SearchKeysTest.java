package com.mitun.sonora;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SearchKeysTest {
    @Test
    public void theLettersPageHoldsTheWholeAlphabet() {
        String[] keys = SearchKeys.keys(false, true);
        assertEquals(30, keys.length);
        assertEquals(SearchKeys.PAGE, keys[0]);
        assertEquals("a", keys[1]);
        assertEquals("z", keys[26]);
        assertEquals(SearchKeys.VOICE, keys[29]);
    }

    @Test
    public void aTvWithNoAssistantHasNoVoiceKey() {
        for (String key : SearchKeys.keys(false, false)) {
            assertFalse(SearchKeys.VOICE.equals(key));
        }
    }

    @Test
    public void theDigitsPageHoldsTheDigits() {
        String[] keys = SearchKeys.keys(true, false);
        assertEquals(13, keys.length);
        assertEquals("0", keys[1]);
        assertEquals("9", keys[10]);
    }

    @Test
    public void typingAppends() {
        assertEquals("ne", SearchKeys.apply("n", "e"));
    }

    @Test
    public void deletingAnEmptyQueryIsSafe() {
        assertEquals("", SearchKeys.apply("", SearchKeys.DELETE));
        assertEquals("n", SearchKeys.apply("ne", SearchKeys.DELETE));
    }

    @Test
    public void aSpaceOnlyGoesBetweenWords() {
        assertEquals("", SearchKeys.apply("", SearchKeys.SPACE));
        assertEquals("prime ", SearchKeys.apply("prime", SearchKeys.SPACE));
        assertEquals("prime ", SearchKeys.apply("prime ", SearchKeys.SPACE));
    }

    @Test
    public void theQueryStopsAtItsLimit() {
        String full = "x".repeat(SearchKeys.MAX_QUERY);
        assertEquals(full, SearchKeys.apply(full, "y"));
        assertEquals(full, SearchKeys.apply(full, SearchKeys.SPACE));
    }

    @Test
    public void pageAndVoiceLeaveTheQueryAlone() {
        assertEquals("a", SearchKeys.apply("a", SearchKeys.PAGE));
        assertEquals("a", SearchKeys.apply("a", SearchKeys.VOICE));
    }

    @Test
    public void onlySingleCharactersType() {
        assertTrue(SearchKeys.types("a"));
        assertFalse(SearchKeys.types(SearchKeys.DELETE));
    }

    @Test
    public void movingStopsAtBothEnds() {
        assertEquals(0, SearchKeys.move(0, -1, 5));
        assertEquals(4, SearchKeys.move(4, 1, 5));
        assertEquals(3, SearchKeys.move(2, 1, 5));
    }
}
