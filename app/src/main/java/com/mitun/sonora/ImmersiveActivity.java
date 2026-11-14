package com.mitun.sonora;

import android.app.Activity;

/**
 * Every screen Sonora owns. Full-screen, free of the system bars: the flags
 * go back on whenever the window or the task could have dropped them, so the
 * screens themselves never think about it.
 */
public class ImmersiveActivity extends Activity {

    @Override
    protected void onResume() {
        super.onResume();
        Platform.immersive(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            Platform.immersive(this);
        }
    }
}
