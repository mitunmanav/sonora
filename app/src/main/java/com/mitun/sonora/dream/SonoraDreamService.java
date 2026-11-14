package com.mitun.sonora.dream;

import android.service.dreams.DreamService;
import android.view.KeyEvent;
import android.view.View;

import com.mitun.sonora.Scenes;
import com.mitun.sonora.ui.ScreensaverView;
import com.mitun.sonora.Platform;

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
        Platform.immersive(getWindow());
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (view != null) {
            // The dream was started again without being stopped, so the old
            // view would keep its bitmaps alive.
            view.stop();
        }
        view = new ScreensaverView(this);
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
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    view.previousScene();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    view.nextScene();
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    view.showInfoOverlay();
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    finish();
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
