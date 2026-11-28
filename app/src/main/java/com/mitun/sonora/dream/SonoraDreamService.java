package com.mitun.sonora.dream;

import android.service.dreams.DreamService;
import android.view.KeyEvent;
import android.view.View;

import com.mitun.sonora.Scenes;
import com.mitun.sonora.Prefs;
import com.mitun.sonora.SaverKeys;
import com.mitun.sonora.ui.ScreensaverView;
import com.mitun.sonora.ui.Ui;

/**
 * The system screen saver. When the TV goes idle the dream service takes over
 * and Aerial's aerial stills play, so the screen is never a dead black panel -
 * which is exactly what removing MiTVDream left behind.
 */
public class SonoraDreamService extends DreamService {
    private ScreensaverView view;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(true);
        setFullscreen(true);
        setScreenBright(false);
        Ui.immersive(getWindow());
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        Prefs prefs = new Prefs(this);
        if (view != null) {
            // The dream was started again without being stopped, so the old
            // view would keep its bitmaps alive.
            view.stop();
        }
        view = new ScreensaverView(this);
        view.setSceneDurationSec(prefs.screensaverSceneSec());
        // "Always" shows the place from the first frame. "On input" waits for
        // the user to press OK, and "never" never shows it.
        view.setShowOverlay(prefs.screensaverClock() == 1);
        setContentView(view);
        view.setFocusable(true);
        view.requestFocus();
        view.start();
    }

    @Override
    public void onDreamingStopped() {
        if (view != null) {
            view.stop();
            view = null;
        }
        super.onDreamingStopped();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (view != null && event.getAction() == KeyEvent.ACTION_DOWN) {
            // The same table as the saver on the home screen: the arrows
            // change the scene, OK shows the clock once in "On tap" mode,
            // any other key wakes the TV.
            switch (SaverKeys.action(event.getKeyCode(),
                    new Prefs(this).screensaverClock(), view.infoOverlayShown())) {
                case SaverKeys.PREVIOUS:
                    view.previousScene();
                    return true;
                case SaverKeys.NEXT:
                    view.nextScene();
                    return true;
                case SaverKeys.SHOW_CLOCK:
                    view.showInfoOverlay();
                    return true;
                case SaverKeys.PASS:
                    break;
                case SaverKeys.WAKE:
                default:
                    finish();
                    return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
