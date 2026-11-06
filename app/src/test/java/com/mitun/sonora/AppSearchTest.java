package com.mitun.sonora;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AppSearchTest {
    private static AppEntry app(String pkg, String label) {
        return new AppEntry(label, pkg, null, null, null, 0, false);
    }

    private static final List<AppEntry> APPS = Arrays.asList(
            app("com.netflix", "Netflix"),
            app("com.yt", "YouTube"),
            app("com.gp", "Google Play"),
            app("com.settings", "Settings"),
            app("com.spotify", "Spotify")
    );

    @Test
    public void findsAnAppByTheStartOfItsName() {
        List<AppEntry> found = AppSearch.filter(APPS, "net");
        assertEquals(1, found.size());
        assertEquals("com.netflix", found.get(0).packageName);
    }

    @Test
    public void ignoresTheCase() {
        List<AppEntry> found = AppSearch.filter(APPS, "SPOTIFY");
        assertEquals(1, found.size());
        assertEquals("com.spotify", found.get(0).packageName);
    }

    @Test
    public void findsAnAppByItsInitials() {
        List<AppEntry> found = AppSearch.filter(APPS, "yt");
        assertEquals(1, found.size());
        assertEquals("com.yt", found.get(0).packageName);
    }

    @Test
    public void findsAnAppByTheInitialsOfTwoWords() {
        List<AppEntry> found = AppSearch.filter(APPS, "gp");
        assertEquals(1, found.size());
        assertEquals("com.gp", found.get(0).packageName);
    }

    @Test
    public void putsTheBestMatchFirst() {
        List<AppEntry> found = AppSearch.filter(APPS, "s");
        assertTrue(found.size() > 1);
        assertEquals("com.settings", found.get(0).packageName);
    }

    @Test
    public void anEmptyQueryReturnsEverything() {
        assertEquals(APPS.size(), AppSearch.filter(APPS, "").size());
        assertEquals(APPS.size(), AppSearch.filter(APPS, "   ").size());
        assertEquals(APPS.size(), AppSearch.filter(APPS, null).size());
    }

    @Test
    public void aQueryThatMatchesNothingFindsNothing() {
        assertTrue(AppSearch.filter(APPS, "zzzz").isEmpty());
    }

    @Test
    public void aNullListIsNotAFailure() {
        assertTrue(AppSearch.filter(null, "net").isEmpty());
        assertTrue(AppSearch.filter(Collections.emptyList(), "net").isEmpty());
    }

    @Test
    public void findsWhereTheFocusedAppSitsInTheResults() {
        List<AppEntry> found = AppSearch.filter(APPS, "s");
        int index = AppSearch.focusedIndex("com.spotify", found);
        assertTrue(index >= 0);
        assertEquals("com.spotify", found.get(index).packageName);
    }

    @Test
    public void anAppThatIsNotInTheResultsHasNoIndex() {
        assertEquals(-1, AppSearch.focusedIndex("com.gone", APPS));
        assertEquals(-1, AppSearch.focusedIndex(null, APPS));
        assertEquals(-1, AppSearch.focusedIndex("com.netflix", null));
    }

    @Test
    public void matchesUnderTheTurkishLocale() {
        // Turkish folds "I" to dotless "ı", which must not hide "IMDb" from "i".
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            List<AppEntry> apps = Collections.singletonList(app("com.imdb", "IMDb"));
            List<AppEntry> found = AppSearch.filter(apps, "i");
            assertEquals(1, found.size());
            assertEquals("com.imdb", found.get(0).packageName);
        } finally {
            Locale.setDefault(original);
        }
    }
}
