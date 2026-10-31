package com.mitun.sonora;

/**
 * What BACK means on the top-level home screen.
 *
 * Pure and separate from the activity because the rule is a hard platform
 * constraint, not a detail of the UI: BACK is a breadcrumb, never a toggle
 * and never a dialog, and it must always end up at the TV home screen.
 */
public final class BackPolicy {
    /** Scroll the grid back to the first row and focus the first tile. */
    public static final int SCROLL_TO_TOP = 0;
    /** Leave Sonora for the home screen the TV really uses. */
    public static final int LEAVE = 1;

    private BackPolicy() {
    }

    /**
     * @param scrollY         how far the app grid is scrolled down
     * @param focusedIndex    index of the focused tile, or -1 when nothing is focused
     * @param isDefaultLauncher whether Sonora is the TV's home screen
     */
    public static int atTopLevel(int scrollY, int focusedIndex, boolean isDefaultLauncher) {
        if (scrollY > 0 || focusedIndex > 0) {
            return SCROLL_TO_TOP;
        }
        // Sonora is the TV home screen when it is the default launcher, so
        // there is nowhere to leave to. Otherwise the user has to be able to
        // reach the home screen the TV really uses.
        return isDefaultLauncher ? SCROLL_TO_TOP : LEAVE;
    }
}
