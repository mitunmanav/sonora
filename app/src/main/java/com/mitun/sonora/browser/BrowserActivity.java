package com.mitun.sonora.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mitun.sonora.Prefs;
import com.mitun.sonora.ui.Ui;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The optional browser.
 *
 * It is off until the user turns it on, and when it is on it blocks ads from
 * a list that is bundled in the app. Nothing else about it reaches out to
 * anyone: no analytics, no update checks, no search suggestions.
 */
public final class BrowserActivity extends Activity {
    private static final String START_PAGE =
            "<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" "
            + "content=\"width=device-width,initial-scale=1\">"
            + "<style>body{font-family:sans-serif;background:#0d0f14;color:#f5f6f8;"
            + "margin:0;padding:8vh 8vw}h1{font-weight:300;font-size:42px;margin:0 0 8px}"
            + "p{color:#9aa0aa;margin:0 0 40px}a{display:block;color:#8ab4f8;"
            + "text-decoration:none;padding:14px 0;border-bottom:1px solid #1c2029;"
            + "font-size:20px}</style></head><body>"
            + "<h1>Sonora</h1><p>Type an address above, or start from here.</p>"
            + "<a href=\"https://duckduckgo.com\">DuckDuckGo</a>"
            + "<a href=\"https://en.wikipedia.org\">Wikipedia</a>"
            + "<a href=\"https://www.youtube.com\">YouTube</a>"
            + "<a href=\"https://news.ycombinator.com\">Hacker News</a>"
            + "</body></html>";

    private WebView web;
    private EditText address;
    private TextView status;
    private AdBlock adBlock;
    private Prefs prefs;

    public static void start(Context context) {
        context.startActivity(new Intent(context, BrowserActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.immersive(this);
        prefs = new Prefs(this);
        adBlock = new AdBlock(this);
        adBlock.setEnabled(prefs.browserAdBlock());
        try {
            setContentView(build());
        } catch (RuntimeException | Error e) {
            // A TV with no web engine has no WebView to make. The screen says
            // so rather than taking the whole app down with it.
            releaseWeb();
            setContentView(noWebView());
            return;
        }
        if (savedInstanceState == null) {
            web.loadUrl("about:blank");
            web.loadDataWithBaseURL(null, START_PAGE, "text/html", "utf-8", null);
        } else {
            web.restoreState(savedInstanceState);
        }
    }

    /** What the screen says when the TV has no web engine at all. */
    private View noWebView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.NIGHT);
        TextView text = new TextView(this);
        text.setText("This TV has no web browser.\n\n"
                + "Sonora's browser needs the system's web engine, and this "
                + "device does not have one. Nothing else in Sonora is affected.");
        text.setTextColor(Color.WHITE);
        text.setTextSize(18);
        text.setGravity(Gravity.CENTER);
        text.setPadding(Ui.dp(this, 40), Ui.dp(this, 40), Ui.dp(this, 40), Ui.dp(this, 40));
        root.addView(text, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        return root;
    }

    /** Frees the WebView and takes it out of the tree. */
    private void releaseWeb() {
        if (web == null) {
            return;
        }
        if (web.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) web.getParent()).removeView(web);
        }
        web.destroy();
        web = null;
    }

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.NIGHT);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 6));

        address = new EditText(this);
        address.setSingleLine(true);
        address.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        address.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                go();
                return true;
            }
            return false;
        });
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(0x80FFFFFF);
        address.setHint("Type an address");
        address.setTextSize(16);
        address.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        address.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 10));
        address.setBackground(Ui.rounded(0x1FFFFFFF, Ui.dpf(this, 12)));
        top.addView(address, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(button("Go", this::go), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(top);

        status = new TextView(this);
        status.setTextColor(0x8CFFFFFF);
        status.setTextSize(12);
        status.setPadding(Ui.dp(this, 22), 0, Ui.dp(this, 22), Ui.dp(this, 6));
        status.setMaxLines(1);
        status.setEllipsize(TextUtils.TruncateAt.END);
        column.addView(status);

        web = new WebView(this);
        web.setBackgroundColor(Ui.NIGHT);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportZoom(false);
        // A page must not be able to read the TV's files.
        settings.setAllowFileAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view,
                                                             WebResourceRequest request) {
                if (adBlock.isBlocked(request.getUrl().toString())) {
                    return blocked();
                }
                return null;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                address.setText(url);
                status.setText("");
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                         android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) {
                    status.setText("That page could not be loaded");
                }
            }
        });
        column.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 18));
        bottom.addView(button("Back", web::goBack));
        bottom.addView(button("Forward", web::goForward));
        bottom.addView(button("Reload", web::reload));
        bottom.addView(button("Home", () -> web.loadDataWithBaseURL(
                null, START_PAGE, "text/html", "utf-8", null)));
        bottom.addView(button("Menu", this::showMenu));
        column.addView(bottom);

        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private static WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "utf-8",
                new ByteArrayInputStream(new byte[0]));
    }

    private View button(String text, Runnable action) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(15);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setGravity(Gravity.CENTER);
        view.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 10));
        view.setFocusable(true);
        view.setBackground(Ui.rounded(0x14FFFFFF, Ui.dpf(this, 12)));
        view.setOnFocusChangeListener((v, focused) -> v.setBackground(
                Ui.rounded(focused ? 0x33FFFFFF : 0x14FFFFFF, Ui.dpf(this, 12))));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void go() {
        String text = address.getText().toString().trim();
        if (text.length() == 0) {
            return;
        }
        hideKeyboard();
        web.loadUrl(normalise(text));
    }

    /** Turns what the user typed into something the WebView can load. */
    static String normalise(String text) {
        if (text.contains(" ") || !text.contains(".")) {
            return "https://duckduckgo.com/?q=" + Uri.encode(text);
        }
        if (text.startsWith("http://") || text.startsWith("https://")) {
            return text;
        }
        return "https://" + text;
    }

    private void hideKeyboard() {
        try {
            InputMethodManager keyboard = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.hideSoftInputFromWindow(address.getWindowToken(), 0);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void showMenu() {
        List<String> items = new ArrayList<>();
        items.add("Bookmark this page");
        items.add("Bookmarks");
        items.add(adBlock.isEnabled() ? "Ad blocker: on" : "Ad blocker: off");
        items.add("Clear history");
        items.add("About this browser");
        new AlertDialog.Builder(this)
                .setItems(items.toArray(new String[0]), (dialog, which) -> {
                    switch (which) {
                        case 0:
                            String url = web.getUrl();
                            if (url != null && url.startsWith("http")) {
                                prefs.addBrowserBookmark(url);
                                Ui.toast(this, "Bookmarked");
                            } else {
                                Ui.toast(this, "There is no page to bookmark yet");
                            }
                            break;
                        case 1:
                            showBookmarks();
                            break;
                        case 2:
                            adBlock.setEnabled(!adBlock.isEnabled());
                            prefs.setBrowserAdBlock(adBlock.isEnabled());
                            Ui.toast(this, adBlock.isEnabled()
                                    ? "Ad blocker on" : "Ad blocker off");
                            break;
                        case 3:
                            web.clearHistory();
                            Ui.toast(this, "History cleared");
                            break;
                        default:
                            Ui.toast(this, "Sonora's browser blocks " + adBlock.size()
                                    + " ad hosts from a list bundled in the app. "
                                    + "No analytics, no suggestions, no phone home.");
                            break;
                    }
                })
                .show();
    }

    private void showBookmarks() {
        List<String> bookmarks = prefs.browserBookmarks();
        String[] names = bookmarks.toArray(new String[0]);
        if (names.length == 0) {
            new AlertDialog.Builder(this)
                    .setTitle("Bookmarks")
                    .setMessage("None yet. Open a page and choose Bookmark in the menu.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Bookmarks")
                .setItems(names, (dialog, which) -> web.loadUrl(bookmarks.get(which)))
                .setNegativeButton("Close", null)
                .show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (web != null && web.canGoBack()) {
                    web.goBack();
                } else {
                    finish();
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) {
            web.saveState(outState);
        }
    }

    @Override
    protected void onPause() {
        if (web != null) {
            web.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        // A destroyed WebView that is still in the tree keeps the activity
        // alive with it.
        releaseWeb();
        super.onDestroy();
    }
}
