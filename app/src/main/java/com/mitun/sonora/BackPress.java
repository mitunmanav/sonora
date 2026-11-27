package com.mitun.sonora;

/**
 * Tells a real BACK press from a stray key-up.
 *
 * BACK acts when it is let go. An up whose down this window never saw, or one
 * the system cancelled because the focus moved while it was held, is not a
 * press, and acting on it would pop a screen the user never asked to leave.
 */
public final class BackPress {
    private boolean down;

    /**
     * Feeds one BACK event in.
     *
     * @param up        true for ACTION_UP, false for ACTION_DOWN
     * @param repeat    the down's repeat count; a held key repeats
     * @param cancelled whether the up carries FLAG_CANCELED
     * @return true when this event is the release of a press to act on
     */
    public boolean released(boolean up, int repeat, boolean cancelled) {
        if (!up) {
            if (repeat == 0) {
                down = true;
            }
            return false;
        }
        boolean pressed = down;
        down = false;
        return pressed && !cancelled;
    }
}
