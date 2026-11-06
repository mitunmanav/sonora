package com.mitun.sonora.tools;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.mitun.sonora.AppManager;
import com.mitun.sonora.AppRepository;
import com.mitun.sonora.PackageActions;
import com.mitun.sonora.ui.Motion;
import com.mitun.sonora.ui.RowDrawable;
import com.mitun.sonora.ui.ScreenStack;
import com.mitun.sonora.ui.SettingRow;
import com.mitun.sonora.ui.Theme;
import com.mitun.sonora.ui.Type;
import com.mitun.sonora.ui.Ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * The app manager: every package on the device, not just the launchable ones.
 *
 * The list is a real list view so it recycles its rows, and the icons are
 * loaded one at a time in the background. Loading every icon up front is what
 * makes an app manager slow.
 */
public final class AppPanel {
    private static final int FILTER_ALL = 0;
    private static final int FILTER_USER = 1;
    private static final int FILTER_SYSTEM = 2;
    private static final int FILTER_DISABLED = 3;

    private static final String[] FILTER_NAMES = {"All", "User", "System", "Disabled"};

    private AppPanel() {
    }

    public static void show(ToolActivity host) {
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);

        ListView list = new ListView(host);
        AppAdapter adapter = new AppAdapter(host);
        // The filters sit above the list instead of inside it: a header is
        // part of the list, and the D-pad cannot leave the list for a header
        // the way it can for a row above it.
        content.addView(adapter.filterBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        list.setSelector(Ui.listSelector(host));
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            AppManager.Item item = (AppManager.Item) list.getItemAtPosition(position);
            if (item != null) {
                showDetail(host, adapter, item);
            }
        });
        // The adapter is how onResume finds the list to reload it.
        content.setTag(adapter);
        content.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                adapter.shutdown();
            }
        });
        host.stack().pushContent("Apps", "Everything installed on this TV", content);
        adapter.load();
    }

    /**
     * Reloads the app list when the screen on top is one, or a detail pushed
     * over one. Coming back from an uninstall or a settings screen can leave
     * an app that is gone still listed - or a detail screen for it open.
     */
    static void resumed(ToolActivity host) {
        ScreenStack stack = host.stack();
        View topView = ToolActivity.stackTopView(stack);
        Object tag = topView == null ? null : topView.getTag();
        if (tag instanceof AppManager.Item) {
            AppManager.Item item = (AppManager.Item) tag;
            if (!isInstalled(host, item.packageName)) {
                // A detail screen for a package that left is a dead end.
                stack.pop();
            } else {
                View below = stack.topContentBelow();
                if (below != null && below.getTag() instanceof AppAdapter) {
                    stack.refreshSettings(item.label,
                            detailRows(host, (AppAdapter) below.getTag(), item));
                    tagDetail(host, item);
                }
            }
        }
        reloadIfAppList(stack.topContent());
        reloadIfAppList(stack.topContentBelow());
    }

    private static boolean isInstalled(ToolActivity host, String packageName) {
        try {
            host.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (RuntimeException e) {
            // The system cannot say: keep the screen rather than drop it on
            // a maybe.
            return true;
        }
    }

    private static void reloadIfAppList(View content) {
        if (content != null && content.getTag() instanceof AppAdapter) {
            ((AppAdapter) content.getTag()).load();
        }
    }

    private static void showDetail(ToolActivity host, AppAdapter adapter, AppManager.Item item) {
        host.stack().pushSettings(item.label, detailRows(host, adapter, item));
        tagDetail(host, item);
    }

    /** Marks the detail screen with its app, so a resume can rebuild or drop it. */
    private static void tagDetail(ToolActivity host, AppManager.Item item) {
        int count = host.stack().getChildCount();
        if (count > 0) {
            host.stack().getChildAt(count - 1).setTag(item);
        }
    }

    private static List<SettingRow> detailRows(ToolActivity host, AppAdapter adapter,
                                               AppManager.Item item) {
        List<SettingRow> rows = new ArrayList<>();
        rows.add(SettingRow.header(item.label));
        rows.add(SettingRow.value("Package", item.packageName));
        rows.add(SettingRow.value("Version", (item.versionName == null
                ? "Unknown" : item.versionName) + " (" + item.versionCode + ")"));
        rows.add(SettingRow.value("Type", item.systemApp ? "System app" : "Installed app"));
        rows.add(SettingRow.value("State", item.enabled ? "Enabled" : "Disabled"));
        rows.add(SettingRow.value("Home screen", item.hasLauncherEntry ? "Yes" : "No"));
        rows.add(SettingRow.header("Actions"));
        if (item.hasLauncherEntry) {
            rows.add(SettingRow.action("Open", () -> {
                PackageActions.Result result = PackageActions.launch(host, item.packageName);
                if (!result.ok) {
                    Ui.toast(host, result.message);
                }
            }));
        }
        boolean androidControlsStop = android.os.Build.VERSION.SDK_INT
                >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
        rows.add(SettingRow.action(
                androidControlsStop ? "Stop in Android app settings" : "App info", () -> {
                    PackageActions.Result result =
                            PackageActions.openAppInfo(host, item.packageName);
                    if (!result.ok) {
                        Ui.toast(host, result.message);
                    }
                }));
        // Sonora itself gets no switch that could strand the remote: hiding
        // it or removing it while it answers HOME leaves the button with
        // nowhere to go.
        boolean self = item.packageName.equals(host.getPackageName());
        if (item.hasLauncherEntry && !self) {
            // Two stores say "hidden": the system's disabled component and the
            // tile menu's pref. The row is honest only if it reads and writes
            // both, so a hide from either side shows and undoes the same way.
            boolean hidden = item.hidden || host.prefs().hidden().contains(item.packageName);
            rows.add(SettingRow.toggle("Hidden from home screen", hidden, () -> {
                boolean target = !hidden;
                PackageActions.Result result =
                        PackageActions.setHidden(host, item.packageName, target);
                Ui.toast(host, result.message);
                if (result.ok) {
                    Set<String> homeHidden = host.prefs().hidden();
                    if (target) {
                        homeHidden.add(item.packageName);
                    } else {
                        homeHidden.remove(item.packageName);
                    }
                    host.prefs().setHidden(homeHidden);
                    item.hidden = target;
                    adapter.reload();
                    // The home screen has to forget the app list, or the app
                    // stays on it until the app is restarted.
                    appsChanged(host);
                }
                // The screen is rebuilt, or the row would keep showing the
                // value it had before the tap.
                host.stack().refreshSettings(item.label,
                        detailRows(host, adapter, item));
                tagDetail(host, item);
            }));
        } else if (!item.hasLauncherEntry) {
            rows.add(SettingRow.value("Home screen", "No launcher entry, so nothing to hide"));
        }
        if (androidControlsStop) {
            rows.add(SettingRow.note("Android does not let Sonora stop other apps directly on "
                    + "this version. Use Force stop on the app settings screen."));
        } else {
            rows.add(SettingRow.action("Force stop", () -> {
                PackageActions.Result result = PackageActions.forceStop(host, item.packageName);
                Ui.toast(host, result.message);
            }));
        }
        rows.add(SettingRow.action("Clear storage", () -> {
            PackageActions.Result result = PackageActions.clearData(host, item.packageName);
            Ui.toast(host, result.message);
        }));
        if (item.systemApp) {
            rows.add(SettingRow.value("Note",
                    "Preinstalled: uninstalling removes it for this user only."));
        }
        rows.add(SettingRow.value("Note",
                "Disabling an app hides it from every launcher, including this one."));
        if (!self) {
            rows.add(SettingRow.danger("Uninstall", () -> {
                host.uninstallApp(item.packageName, item.label);
                // The package may be gone by the time the home screen looks at
                // its list again.
                appsChanged(host);
            }));
        }
        return rows;
    }

    /**
     * Tells the home screen that the list of apps has changed.
     *
     * The cache is dropped and the epoch is moved, so the launcher rebuilds
     * its grid the next time it comes back.
     */
    private static void appsChanged(ToolActivity host) {
        AppRepository.invalidate();
        host.prefs().bumpAppsEpoch();
    }

    /** The rows of the app list: an icon, the name and the package. */
    private static View buildRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(context, 14), Ui.dp(context, 8), Ui.dp(context, 22), Ui.dp(context, 8));
        // The row is not focusable: the list holds the focus and reports the
        // click itself. A focusable row stops the list from ever doing that,
        // and OK would do nothing at all.
        row.setFocusable(false);

        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44));
        ip.setMarginEnd(Ui.dp(context, 14));
        row.addView(icon, ip);

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView label = new TextView(context);
        label.setTextColor(Theme.ink());
        Type.apply(label, Type.HEADING);
        label.setMaxLines(1);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(label);
        TextView pkg = new TextView(context);
        pkg.setTextColor(Theme.inkTertiary());
        Type.apply(pkg, Type.CAPTION);
        pkg.setMaxLines(1);
        pkg.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(pkg);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.setTag(new Object[]{icon, label, pkg});
        return row;
    }

    /** The filter tabs above the list. */
    private static final class AppAdapter extends BaseAdapter {
        private final ToolActivity host;
        private final List<AppManager.Item> all = new ArrayList<>();
        private final List<AppManager.Item> shown = new ArrayList<>();
        private int filter = FILTER_ALL;
        private final List<TextView> tabs = new ArrayList<>();
        /** One decoder for every icon, like the thumbnails in the file manager. */
        private final ExecutorService icons = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "sonora-icons");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        /** Icons already being decoded, so a rebind does not queue them again. */
        private final Set<AppManager.Item> decoding = new HashSet<>();
        /** A reload that is still running; a second ask is already covered. */
        private boolean loading;

        AppAdapter(ToolActivity host) {
            this.host = host;
        }

        View filterBar() {
            LinearLayout bar = new LinearLayout(host);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setPadding(Ui.dp(host, 14), Ui.dp(host, 8), Ui.dp(host, 14), Ui.dp(host, 4));
            for (int i = 0; i < FILTER_NAMES.length; i++) {
                final int index = i;
                TextView tab = new TextView(host);
                tab.setText(FILTER_NAMES[i]);
                tab.setTextColor(Theme.inkSoft());
                Type.apply(tab, Type.BODY);
                tab.setPadding(Ui.dp(host, 16), Ui.dp(host, 8), Ui.dp(host, 16), Ui.dp(host, 8));
                RowDrawable tabBg = new RowDrawable(host);
                tab.setBackground(tabBg);
                tab.setFocusable(true);
                final float[] tabFocus = {0f};
                tab.setOnFocusChangeListener((v, focused) -> {
                    Motion.springCustom(v, 0, focused ? 1f : 0f, Motion.DAMPING_CRITICAL,
                            Motion.RESPONSE_FOCUS, new Motion.FloatProperty() {
                                @Override
                                public float get() {
                                    return tabFocus[0];
                                }

                                @Override
                                public void set(float value) {
                                    tabFocus[0] = value;
                                    tabBg.setFocus(value);
                                    tab.setTextColor(Ui.lerpColor(Theme.inkSoft(),
                                            Theme.ink(), value));
                                }
                            });
                    v.setElevation(focused ? Ui.dpf(host, 8f) : 0f);
                });
                tab.setOnClickListener(v -> {
                    filter = index;
                    applyFilter();
                    for (int t = 0; t < tabs.size(); t++) {
                        tabs.get(t).setTextColor(t == index ? Theme.ink() : Theme.inkSoft());
                    }
                    notifyDataSetChanged();
                });
                tabs.add(tab);
                bar.addView(tab, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            return bar;
        }

        void load() {
            if (loading) {
                return;
            }
            loading = true;
            new Thread(() -> {
                List<AppManager.Item> loaded = AppManager.load(host);
                host.runOnUiThread(() -> {
                    loading = false;
                    if (host.isFinishing() || host.isDestroyed()) {
                        return;
                    }
                    all.clear();
                    all.addAll(loaded);
                    applyFilter();
                    notifyDataSetChanged();
                });
            }, "sonora-apps").start();
        }

        void reload() {
            load();
        }

        void shutdown() {
            icons.shutdownNow();
        }

        private void applyFilter() {
            shown.clear();
            for (AppManager.Item item : all) {
                boolean keep;
                switch (filter) {
                    case FILTER_USER:
                        keep = !item.systemApp;
                        break;
                    case FILTER_SYSTEM:
                        keep = item.systemApp;
                        break;
                    case FILTER_DISABLED:
                        keep = !item.enabled;
                        break;
                    default:
                        keep = true;
                        break;
                }
                if (keep) {
                    shown.add(item);
                }
            }
        }

        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public AppManager.Item getItem(int position) {
            return position >= 0 && position < shown.size() ? shown.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = buildRow(host);
            }
            Object[] parts = (Object[]) view.getTag();
            ImageView icon = (ImageView) parts[0];
            TextView label = (TextView) parts[1];
            TextView pkg = (TextView) parts[2];

            AppManager.Item item = getItem(position);
            if (item == null) {
                // A recycled row still wears the app it last showed; without
                // a clear it would present that app as this one.
                label.setText("");
                pkg.setText("");
                icon.setTag(null);
                icon.setImageDrawable(null);
                return view;
            }
            label.setText(item.label);
            pkg.setText(item.packageName);
            icon.setTag(item.packageName);
            if (item.icon != null) {
                icon.setImageBitmap(item.icon);
            } else {
                icon.setImageDrawable(null);
                loadIcon(item, icon);
            }
            return view;
        }

        /** Decodes one icon on the shared worker and drops it in when it is ready. */
        private void loadIcon(AppManager.Item item, ImageView target) {
            if (!decoding.add(item)) {
                return;
            }
            try {
                icons.execute(() -> {
                    Bitmap bitmap = null;
                    try {
                        PackageManager pm = host.getPackageManager();
                        bitmap = Ui.drawableToBitmap(pm.getApplicationIcon(item.packageName),
                                96, 96, true);
                    } catch (Exception | OutOfMemoryError ignored) {
                    }
                    if (bitmap != null) {
                        item.icon = bitmap;
                    }
                    final Bitmap ready = bitmap;
                    host.runOnUiThread(() -> {
                        decoding.remove(item);
                        if (ready != null && item.packageName.equals(target.getTag())) {
                            target.setImageBitmap(ready);
                        }
                    });
                });
            } catch (RejectedExecutionException e) {
                decoding.remove(item);
            }
        }
    }
}
