package com.mitun.sonora.tools;

import com.mitun.sonora.ImmersiveActivity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mitun.sonora.ui.Ui;

import java.io.File;
import com.mitun.sonora.Platform;

/**
 * The share screen.
 *
 * The LAN server runs on a thread inside this activity and dies with it, so
 * there is no listening socket when the app is closed. It shows the address
 * big enough to read off the screen and type into a phone or a laptop.
 */
public final class ShareActivity extends ImmersiveActivity {
    static final String EXTRA_PATH = "path";
    static final String EXTRA_NAME = "name";

    private FileServer server;
    private File shared;
    private File root;
    private TextView address;
    private TextView hint;
    private boolean stopStarted;

    public static void start(Context context, File file) {
        Intent intent = new Intent(context, ShareActivity.class);
        intent.putExtra(EXTRA_PATH, file.getAbsolutePath());
        intent.putExtra(EXTRA_NAME, file.getName());
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String path = getIntent().getStringExtra(EXTRA_PATH);
        shared = path == null ? FileServer.appFolder(this) : new File(path);
        if (path != null && !shared.exists()) {
            // What it pointed at is gone; the app's own folder is the fallback.
            Ui.toast(this, "That file is gone, so Sonora's own folder is shared");
            shared = FileServer.appFolder(this);
        }

        root = shared.isDirectory() ? shared : shared.getParentFile();
        if (root == null) {
            root = FileServer.appFolder(this);
        }
        server = new FileServer();
        setContentView(build(startServer()));
    }

    /** Starts the server and returns the port it listens on. */
    private int startServer() {
        try {
            return server.start(root);
        } catch (Exception e) {
            Ui.toast(this, "This TV could not open a port for sharing");
            return -1;
        }
    }

    private View build(int port) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(Ui.dp(this, 60), Ui.dp(this, 50), Ui.dp(this, 60), Ui.dp(this, 40));

        TextView title = new TextView(this);
        title.setText("Share over the network");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        title.setLetterSpacing(-0.015f);
        title.setGravity(Gravity.CENTER);
        column.addView(title);

        TextView subtitle = new TextView(this);
        // A file is served as its whole folder, so the subtitle says what is
        // really shared, not just the name the share was opened with.
        subtitle.setText(shared.isDirectory() ? shared.getName()
                : shared.getName() + " · the whole folder is shared");
        subtitle.setTextColor(0xB3FFFFFF);
        subtitle.setTextSize(15);
        subtitle.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 28));
        column.addView(subtitle);

        String url = port > 0 ? server.address() : "";
        address = new TextView(this);
        address.setText(url.isEmpty() ? "No network address" : url);
        address.setTextColor(Color.WHITE);
        address.setTextSize(38);
        address.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        address.setLetterSpacing(-0.02f);
        address.setGravity(Gravity.CENTER);
        address.setPadding(Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 20));
        address.setBackground(Ui.rounded(0x1FFFFFFF, Ui.dpf(this, 18)));
        column.addView(address);

        hint = new TextView(this);
        // A port that would not open is not the network's fault.
        hint.setText(hintText(port));
        hint.setTextColor(0xB3FFFFFF);
        hint.setTextSize(15);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, Ui.dp(this, 20), 0, 0);
        column.addView(hint);

        TextView note = new TextView(this);
        note.setText("The server stops as soon as you leave this screen.");
        note.setTextColor(0x80FFFFFF);
        note.setTextSize(13);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, Ui.dp(this, 10), 0, 0);
        column.addView(note);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xF0000000);
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        return root;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                finish();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /** The line under the address, which tracks the same restart it does. */
    private String hintText(int port) {
        if (port <= 0) {
            return "The share could not start on this TV.";
        }
        return server.address().isEmpty()
                ? "Connect this TV to a network and try again."
                : "Open this address in a browser on the same network.";
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // The relaunch is singleTask: what is shared comes from the new
        // intent, so the screen starts over the way a fresh open would.
        setIntent(intent);
        recreate();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // The screen baked its metrics when it was built; a resize or a
        // density change starts it over, which restarts the server on a new
        // port and shows the new address.
        recreate();
    }

    @Override
    protected void onStart() {
        super.onStart();
        stopStarted = false;
        // The screen came back: the server was stopped when it went away.
        if (server != null && !server.isRunning()) {
            int port = startServer();
            String url = port > 0 ? server.address() : "";
            if (address != null) {
                address.setText(url.isEmpty() ? "No network address" : url);
            }
            // The hint says what the address does, or it lies about a restart.
            if (hint != null) {
                hint.setText(hintText(port));
            }
        }
    }

    /**
     * Stops the server off the main thread: stop() joins the accept thread,
     * which is up to half a second the UI is not allowed to spend.
     */
    private void stopServer() {
        final FileServer stopping = server;
        if (stopping == null || stopStarted || !stopping.isRunning()) {
            return;
        }
        stopStarted = true;
        Thread stopper = new Thread(stopping::stop, "sonora-share-stop");
        stopper.setDaemon(true);
        try {
            stopper.start();
        } catch (RuntimeException | OutOfMemoryError e) {
            // A thread that cannot start must not leave the port listening.
            stopping.stop();
        }
    }

    @Override
    protected void onStop() {
        // Nothing keeps listening once the user has left this screen.
        stopServer();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopServer();
        super.onDestroy();
    }
}
