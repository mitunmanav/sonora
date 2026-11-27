package com.mitun.sonora;

/**
 * What a key does while the screen saver is up, for the in-app saver and the
 * system dream alike.
 *
 * Pure, so both answer the same way and it can be tested without a TV. tvOS
 * and Google TV wake on any button; the arrows here change the scene first,
 * and OK shows the clock once in the "On tap" mode before it wakes.
 */
public final class SaverKeys {
    public static final int WAKE = 0;
    public static final int NEXT = 1;
    public static final int PREVIOUS = 2;
    public static final int SHOW_CLOCK = 3;
    /** Left to the system: the volume belongs to the TV, not to the saver. */
    public static final int PASS = 4;

    private SaverKeys() {
    }

    /**
     * @param keyCode     a KeyEvent key code, kept as a number so this class
     *                    stays Android-free
     * @param clockMode   {@link Prefs#screensaverClock()}: 0 on tap, 1 always, 2 never
     * @param clockShown  whether the clock is on screen now
     */
    public static int action(int keyCode, int clockMode, boolean clockShown) {
        switch (keyCode) {
            case 21: // DPAD_LEFT
                return PREVIOUS;
            case 22: // DPAD_RIGHT
                return NEXT;
            case 23: // DPAD_CENTER
            case 66: // ENTER
            case 160: // NUMPAD_ENTER
                return clockMode == 0 && !clockShown ? SHOW_CLOCK : WAKE;
            case 24: // VOLUME_UP
            case 25: // VOLUME_DOWN
            case 164: // VOLUME_MUTE
                return PASS;
            default:
                return WAKE;
        }
    }
}
