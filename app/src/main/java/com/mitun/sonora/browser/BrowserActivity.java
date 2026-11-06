package com.mitun.sonora.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
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
import java.util.Locale;

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

    private static final String BLOCKED_PAGE =
            "<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" "
            + "content=\"width=device-width,initial-scale=1\">"
            + "<style>body{font-family:sans-serif;background:#0d0f14;color:#f5f6f8;"
            + "margin:0;padding:8vh 8vw}h1{font-weight:300;font-size:42px;margin:0 0 8px}"
            + "p{color:#9aa0aa;font-size:20px}</style></head><body>"
            + "<h1>Blocked</h1><p>Sonora's ad blocker stopped this page. "
            + "If the page was not an ad, choose Menu and turn the ad blocker "
            + "off to load it.</p></body></html>";

    /** Typed schemes that need another app, which a TV does not have. */
    private static final String[] APP_SCHEMES = {
            "tel:", "mailto:", "sms:", "intent:", "geo:", "market:", "ftp:",
            "file:", "data:", "javascript:"};

    private WebView web;
    private EditText address;
    private TextView status;
    private AdBlock adBlock;
    private Prefs prefs;
    private boolean pageFailed;

    public static void start(Context context) {
        context.startActivity(new Intent(context, BrowserActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.immersive(this);
        prefs = new Prefs(this);
        if (!prefs.browserEnabled()) {
            Ui.toast(this, "The browser is off");
            finish();
            return;
        }
        adBlock = new AdBlock(this);
        adBlock.setEnabled(prefs.browserAdBlock());
        try {
            setContentView(build());
            boolean restored = savedInstanceState != null
                    && web.restoreState(savedInstanceState) != null;
            if (!restored) {
                web.loadDataWithBaseURL(null, START_PAGE, "text/html", "utf-8", null);
            }
        } catch (RuntimeException | Error e) {
            // A TV with no web engine has no WebView to make. The screen says
            // so rather than taking the whole app down with it.
            releaseWeb();
            setContentView(noWebView());
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
        try {
            web.destroy();
        } catch (RuntimeException | Error ignored) {
        }
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
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Schemes match case-insensitively: "HTTPS://" is still web.
                if (url != null && (url.regionMatches(true, 0, "http://", 0, 7)
                        || url.regionMatches(true, 0, "https://", 0, 8)
                        || url.regionMatches(true, 0, "about:", 0, 6))) {
                    String upgraded = localOrHttps(url);
                    if (!upgraded.equals(url)) {
                        view.loadUrl(upgraded);
                        return true;
                    }
                    return false;
                }
                Ui.toast(BrowserActivity.this,
                        "That link needs an app this TV does not have");
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view,
                                                             WebResourceRequest request) {
                if (adBlock.isBlocked(request.getUrl().toString())) {
                    if (request.isForMainFrame()) {
                        view.post(() -> view.loadDataWithBaseURL(
                                null, BLOCKED_PAGE, "text/html", "utf-8", null));
                    }
                    return blocked();
                }
                return null;
            }

            @Override
            public void onPageStarted(WebView view, String url,
                                       android.graphics.Bitmap favicon) {
                pageFailed = false;
                status.setText("Loading…");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!pageFailed) {
                    status.setText("");
                }
                if (!address.hasFocus() && url != null
                        && !url.startsWith("about:") && !url.startsWith("data:")) {
                    address.setText(url);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                         android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) {
                    pageFailed = true;
                    status.setText(loadError(error));
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (!pageFailed && progress < 100) {
                    status.setText("Loading… " + progress + "%");
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean dialog,
                                           boolean userGesture,
                                           android.os.Message resultMsg) {
                Ui.toast(BrowserActivity.this,
                        "This browser can only show one page at a time");
                return false;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message,
                                      JsResult result) {
                Ui.toast(BrowserActivity.this,
                        "This TV cannot show that page's dialog");
                result.confirm();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message,
                                        JsResult result) {
                Ui.toast(BrowserActivity.this,
                        "This TV cannot show that page's dialog");
                result.cancel();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message,
                                       String defaultValue, JsPromptResult result) {
                Ui.toast(BrowserActivity.this,
                        "This TV cannot show that page's dialog");
                result.cancel();
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView view,
                    android.webkit.ValueCallback<android.net.Uri[]> callback,
                    FileChooserParams params) {
                Ui.toast(BrowserActivity.this, "This browser cannot pick files");
                callback.onReceiveValue(null);
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                Ui.toast(BrowserActivity.this,
                        "This browser cannot play fullscreen video");
                callback.onCustomViewHidden();
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                    android.webkit.GeolocationPermissions.Callback callback) {
                // Left pending, the page would wait on a location that never
                // comes: a TV with no GPS service says no out loud instead.
                callback.invoke(origin, false, false);
                Ui.toast(BrowserActivity.this,
                        "This browser cannot share your location");
            }

            @Override
            public void onPermissionRequest(
                    android.webkit.PermissionRequest request) {
                // Camera, microphone, midi: a page asking for hardware this
                // TV cannot offer gets a denial, not a silent wait.
                request.deny();
                Ui.toast(BrowserActivity.this,
                        "This browser cannot use that hardware");
            }
        });
        // There is nowhere on a TV a download could go, and a click that
        // silently does nothing is worse than saying so.
        web.setDownloadListener((url, userAgent, disposition, mime, length) ->
                Ui.toast(this, "This browser cannot download files"));
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

    /** Names why a page failed the way the system would to a person. */
    private static String loadError(android.webkit.WebResourceError error) {
        switch (error.getErrorCode()) {
            case WebViewClient.ERROR_HOST_LOOKUP:
                return "There is no site at that address";
            case WebViewClient.ERROR_CONNECT:
            case WebViewClient.ERROR_IO:
                return "The site could not be reached";
            case WebViewClient.ERROR_TIMEOUT:
                return "The site took too long to answer";
            case WebViewClient.ERROR_FAILED_SSL_HANDSHAKE:
                return "The secure connection failed";
            default:
                CharSequence description = error.getDescription();
                return description != null && description.length() > 0
                        ? description.toString()
                        : "That page could not be loaded";
        }
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
        if (needsAnApp(text)) {
            Ui.toast(this, "That address needs an app this TV does not have");
            return;
        }
        web.loadUrl(localOrHttps(normalise(text)));
    }

    /**
     * http is only trusted on the local network — the router's page, a
     * computer's share, Sonora's own share server. Anywhere else it is
     * quietly upgraded to https.
     */
    public static String localOrHttps(String url) {
        if (url == null || !url.startsWith("http://")) {
            return url;
        }
        return isLocalHost(url.substring("http://".length()))
                ? url
                : "https://" + url.substring("http://".length());
    }

    private static boolean isLocalHost(String rest) {
        String host = rest;
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        int at = host.lastIndexOf('@');
        if (at >= 0) {
            host = host.substring(at + 1);
        }
        if (host.startsWith("[")) {
            int close = host.indexOf(']');
            host = close >= 0 ? host.substring(1, close) : host.substring(1);
        } else {
            int colon = host.indexOf(':');
            if (colon >= 0) {
                host = host.substring(0, colon);
            }
        }
        host = host.toLowerCase(Locale.US);
        if (host.contains(":")) {
            return host.equals("::1") || host.startsWith("fe80:")
                    || host.startsWith("fc") || host.startsWith("fd");
        }
        if (host.equals("localhost") || host.endsWith(".local")
                || host.indexOf('.') < 0) {
            return true;
        }
        // Only a real IPv4 literal is a private address; a name like
        // "10.evil.com" merely starts the way one does.
        if (!host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return false;
        }
        if (host.startsWith("10.") || host.startsWith("127.")
                || host.startsWith("169.254.") || host.startsWith("192.168.")) {
            return true;
        }
        if (host.startsWith("172.")) {
            String[] parts = host.split("\\.");
            if (parts.length == 4) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return false;
    }

    private static boolean needsAnApp(String text) {
        String lower = text.toLowerCase(Locale.US);
        for (String scheme : APP_SCHEMES) {
            if (lower.startsWith(scheme)) {
                return true;
            }
        }
        return false;
    }

    /** Turns what the user typed into something the WebView can load. */
    public static String normalise(String text) {
        if (text == null) {
            return "https://duckduckgo.com/";
        }
        text = text.trim();
        if (text.isEmpty()) {
            return "https://duckduckgo.com/";
        }
        if (text.startsWith("http://") || text.startsWith("https://")) {
            return text;
        }
        if (text.contains("://")) {
            return text;
        }
        // about:blank and its kin are pages the WebView itself serves.
        if (text.startsWith("about:")) {
            return text;
        }
        if (text.contains(" ")) {
            return "https://duckduckgo.com/?q=" + encodeQuery(text);
        }
        // host:port is a local address; a bare ::1 or a scheme-looking word
        // like "foo:" is nobody's address and falls through to the search.
        if (text.equalsIgnoreCase("localhost") || text.toLowerCase(Locale.US).startsWith("localhost:")
                || text.startsWith("[") || text.matches("[^:\\s]+:\\d+\\S*")) {
            return "http://" + text;
        }
        if (text.contains(".")) {
            if (text.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d+)?(/.*)?$")) {
                return "http://" + text;
            }
            return "https://" + text;
        }
        return "https://duckduckgo.com/?q=" + encodeQuery(text);
    }

    private static String encodeQuery(String text) {
        try {
            return java.net.URLEncoder.encode(text, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return text;
        }
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
                            CookieManager.getInstance().removeAllCookies(null);
                            WebStorage.getInstance().deleteAllData();
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
                .setItems(names, (dialog, which) ->
                        web.loadUrl(localOrHttps(bookmarks.get(which))))
                .setNegativeButton("Close", null)
                .show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (typing()) {
                    hideKeyboard();
                } else if (web != null && web.canGoBack()) {
                    web.goBack();
                } else {
                    finish();
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /** The address bar is the only place the on-screen keyboard ever opens. */
    private boolean typing() {
        try {
            InputMethodManager keyboard = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            return keyboard != null && keyboard.isActive(address);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) {
            try {
                web.saveState(outState);
            } catch (RuntimeException | Error ignored) {
            }
        }
    }

    /** singleTask: an intent for the browser comes back to this screen. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!prefs.browserEnabled()) {
            finish();
            return;
        }
        if (address != null) {
            android.net.Uri data = intent.getData();
            if (data != null) {
                address.setText(data.toString());
            }
            address.requestFocus();
        }
    }

    @Override
    protected void onPause() {
        if (web != null) {
            web.onPause();
            web.pauseTimers();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.resumeTimers();
            web.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.pauseTimers();
        }
        // A destroyed WebView that is still in the tree keeps the activity
        // alive with it.
        releaseWeb();
        super.onDestroy();
    }
}
