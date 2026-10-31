package com.mitun.sonora.tools;

import android.app.Activity;
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

/**
 * The share screen.
 *
 * The LAN server runs on a thread inside this activity and dies with it, so
 * there is no listening socket when the app is closed. It shows the address
 * big enough to read off the screen and type into a phone or a laptop.
 */
public final class ShareActivity extends Activity {
    private static final String EXTRA_PATH = "path";
    private static final String EXTRA_NAME = "name";

    private FileServer server;
    private File shared;

    public static void start(Context context, File file) {
        Intent intent = new Intent(context, ShareActivity.class);
        intent.putExtra(EXTRA_PATH, file.getAbsolutePath());
        intent.putExtra(EXTRA_NAME, file.getName());
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.immersive(this);
        String path = getIntent().getStringExtra(EXTRA_PATH);
        shared = path == null ? FileServer.appFolder(this) : new File(path);

        File root = shared.isDirectory() ? shared : shared.getParentFile();
        if (root == null) {
            root = FileServer.appFolder(this);
        }
        server = new FileServer();
        int port = -1;
        try {
            port = server.start(root);
        } catch (Exception e) {
            Ui.toast(this, "This TV could not open a port for sharing");
        }
        setContentView(build(port));
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
        subtitle.setText(shared.getName());
        subtitle.setTextColor(0xB3FFFFFF);
        subtitle.setTextSize(15);
        subtitle.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 28));
        column.addView(subtitle);

        TextView address = new TextView(this);
        address.setText(port > 0 ? server.address() : "No address available");
        address.setTextColor(Color.WHITE);
        address.setTextSize(38);
        address.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        address.setLetterSpacing(-0.02f);
        address.setGravity(Gravity.CENTER);
        address.setPadding(Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 20));
        address.setBackground(Ui.rounded(0x1FFFFFFF, Ui.dpf(this, 18)));
        column.addView(address);

        TextView hint = new TextView(this);
        hint.setText(port > 0
                ? "Open this address in a browser on the same network."
                : "Connect this TV to a network and try again.");
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

    @Override
    protected void onDestroy() {
        if (server != null) {
            server.stop();
        }
        super.onDestroy();
    }
}
