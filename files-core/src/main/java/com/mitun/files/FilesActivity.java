package com.mitun.files;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Files, in the manner of Finder: a sidebar of places, a window of icons or a
 * striped list with columns, a path bar, and a menu on a long press of OK.
 *
 * It is the whole file manager, used by the standalone app and bundled into the
 * launcher. Listing, sorting, copying and thumbnails all run off the main thread,
 * and a folder of ten thousand files only ever has one screenful of views.
 */
public class FilesActivity extends Activity {
    /** Bundled use: a folder of the host's own, shown first, needing no permission. */
    public static final String EXTRA_OWN_FOLDER = "own_folder";
    public static final String EXTRA_OWN_LABEL = "own_label";
    /** Bundled use: follow the host's appearance rather than offer a choice. */
    public static final String EXTRA_LIGHT = "light";
    /** Bundled use: an activity of the host that shares a file, given by class name. */
    public static final String EXTRA_SHARE_CLASS = "share_class";
    public static final String EXTRA_SHARE_LABEL = "share_label";
    public static final String EXTRA_SHARE_PATH = "share_path";
    public static final String EXTRA_SHARE_NAME = "share_name";

    private static final int REQUEST_STORAGE = 11;
    private static final int THUMB_CACHE_BYTES = 6 * 1024 * 1024;

    private final ExecutorService lister = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "files-list");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private final ExecutorService thumbnailer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "files-thumbs");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private final LruCache<String, Bitmap> thumbs = new LruCache<String, Bitmap>(THUMB_CACHE_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    /** The item that was selected in each folder, so going back lands where you left. */
    private final Map<String, String> lastSelected = new HashMap<>();

    private FilesTheme theme;
    private SharedPreferences prefs;
    private boolean low;
    private boolean bundled;
    private FrameLayout root;
    private LinearLayout sidebarList;
    private final List<SideRow> sideRows = new ArrayList<>();
    private final List<Places.Place> places = new ArrayList<>();
    private LinearLayout toolbar;
    private View backButton;
    private TextView titleView;
    private TextView countView;
    private TextView pasteButton;
    private FrameLayout content;
    private LinearLayout columnHeader;
    private TextView message;
    private TextView actionButton;
    private TextView pathBar;
    private AbsListView itemsView;
    private final ItemAdapter adapter = new ItemAdapter();
    private DateFormat dateFormat;

    private Places.Place place;
    private File folder;
    private List<FileLogic.Entry> entries = new ArrayList<>();
    private int generation;
    private boolean gridMode;
    private int sortMode;
    private boolean showHidden;
    private Sheets.Sheet sheet;
    private QuickLook quickLook;
    private File clipboard;
    private boolean clipboardMove;
    private AtomicBoolean running;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("files", MODE_PRIVATE);
        bundled = getIntent().hasExtra(EXTRA_LIGHT);
        boolean light = bundled ? getIntent().getBooleanExtra(EXTRA_LIGHT, false)
                : prefs.getBoolean("light", false);
        theme = new FilesTheme(light);
        low = FilesTheme.lowMemory(this);
        gridMode = prefs.getBoolean("grid", true);
        sortMode = prefs.getInt("sort", FileLogic.SORT_NAME);
        showHidden = prefs.getBoolean("hidden", false);
        dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
        immersive();
        buildWindow();
        setContentView(root);
        readPlaces();
        if (!places.isEmpty()) {
            open(places.get(0), places.get(0).folder, null);
        }
        sideRows.get(0).post(() -> focusSidebar());
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private int dp(float value) {
        return FilesTheme.dp(this, value);
    }

    // --- The window ---------------------------------------------------------

    private void buildWindow() {
        root = new FrameLayout(this);
        root.setBackgroundColor(theme.window);
        getWindow().setBackgroundDrawable(null);

        LinearLayout window = new LinearLayout(this);
        window.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(window, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setBackground(Sheets.rounded(theme.sidebar, dp(16)));
        side.setPadding(dp(10), dp(16), dp(10), dp(10));
        LinearLayout.LayoutParams sideParams = new LinearLayout.LayoutParams(dp(196),
                ViewGroup.LayoutParams.MATCH_PARENT);
        sideParams.setMargins(dp(12), dp(12), 0, dp(12));
        window.addView(side, sideParams);

        TextView appName = new TextView(this);
        appName.setText("Files");
        appName.setTextColor(theme.ink);
        appName.setTextSize(22f);
        appName.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        appName.setPadding(dp(8), 0, 0, dp(8));
        side.addView(appName);

        ScrollView sideScroll = new ScrollView(this);
        sideScroll.setVerticalScrollBarEnabled(false);
        sideScroll.setFocusable(false);
        sidebarList = new LinearLayout(this);
        sidebarList.setOrientation(LinearLayout.VERTICAL);
        sideScroll.addView(sidebarList);
        side.addView(sideScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(16), dp(12), dp(16), dp(8));
        window.addView(main, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        main.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        backButton = toolButton(Marks.BACK, "Back", this::goUp);
        toolbar.addView(backButton);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(10), 0, dp(8), 0);
        titleView = new TextView(this);
        titleView.setTextColor(theme.ink);
        titleView.setTextSize(18f);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        titles.addView(titleView);
        countView = new TextView(this);
        countView.setTextColor(theme.inkSecondary);
        countView.setTextSize(11.5f);
        titles.addView(countView);
        toolbar.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        pasteButton = new TextView(this);
        pasteButton.setTextSize(13f);
        pasteButton.setGravity(Gravity.CENTER);
        pasteButton.setTextColor(0xFFFFFFFF);
        pasteButton.setFocusable(true);
        pasteButton.setClickable(true);
        pasteButton.setSingleLine(true);
        pasteButton.setPadding(dp(14), 0, dp(14), 0);
        pasteButton.setVisibility(View.GONE);
        final GradientDrawable pasteRest = Sheets.rounded(theme.accent, dp(15));
        final GradientDrawable pasteLit = Sheets.rounded(0xFFFFFFFF, dp(15));
        pasteButton.setBackground(pasteRest);
        pasteButton.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(focused ? pasteLit : pasteRest);
            pasteButton.setTextColor(focused ? 0xFF101C26 : 0xFFFFFFFF);
        });
        pasteButton.setOnClickListener(v -> askPaste());
        pasteButton.setOnKeyListener(this::onToolbarKey);
        LinearLayout.LayoutParams pasteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30));
        pasteParams.setMarginEnd(dp(8));
        toolbar.addView(pasteButton, pasteParams);

        toolbar.addView(toolButton(gridMode ? Marks.LIST : Marks.GRID,
                gridMode ? "View as list" : "View as icons", this::toggleView));
        toolbar.addView(toolButton(Marks.SORT, "Sort", this::showSortMenu));
        toolbar.addView(toolButton(Marks.NEW_FOLDER, "New folder", this::askNewFolder));
        toolbar.addView(toolButton(Marks.MORE, "Actions", this::showSelectedMenu));
        if (!bundled) {
            toolbar.addView(toolButton(Marks.APPEARANCE, "Appearance", this::toggleAppearance));
        }

        columnHeader = new LinearLayout(this);
        columnHeader.setOrientation(LinearLayout.HORIZONTAL);
        columnHeader.setPadding(dp(8), dp(6), dp(8), dp(4));
        columnHeader.addView(columnLabel("Name"), weighted(0.52f));
        columnHeader.addView(columnLabel("Date Modified"), weighted(0.20f));
        columnHeader.addView(columnLabel("Size"), weighted(0.12f));
        columnHeader.addView(columnLabel("Kind"), weighted(0.16f));
        main.addView(columnHeader);

        content = new FrameLayout(this);
        main.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout messageBox = new LinearLayout(this);
        messageBox.setOrientation(LinearLayout.VERTICAL);
        messageBox.setGravity(Gravity.CENTER);
        message = new TextView(this);
        message.setTextColor(theme.inkSecondary);
        message.setTextSize(15f);
        message.setGravity(Gravity.CENTER);
        message.setPadding(dp(40), 0, dp(40), dp(12));
        messageBox.addView(message);
        actionButton = new TextView(this);
        actionButton.setTextSize(14f);
        actionButton.setTextColor(0xFFFFFFFF);
        actionButton.setGravity(Gravity.CENTER);
        actionButton.setFocusable(true);
        actionButton.setClickable(true);
        actionButton.setPadding(dp(18), 0, dp(18), 0);
        final GradientDrawable actionRest = Sheets.rounded(theme.accent, dp(8));
        final GradientDrawable actionLit = Sheets.rounded(0xFFFFFFFF, dp(8));
        actionButton.setBackground(actionRest);
        actionButton.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(focused ? actionLit : actionRest);
            actionButton.setTextColor(focused ? 0xFF101C26 : 0xFFFFFFFF);
        });
        actionButton.setOnKeyListener((v, code, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_LEFT) {
                focusSidebar();
                return true;
            }
            return false;
        });
        messageBox.addView(actionButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        content.addView(messageBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        message.setVisibility(View.GONE);
        actionButton.setVisibility(View.GONE);

        pathBar = new TextView(this);
        pathBar.setTextColor(theme.inkSecondary);
        pathBar.setTextSize(12f);
        pathBar.setSingleLine(true);
        pathBar.setEllipsize(TextUtils.TruncateAt.START);
        pathBar.setPadding(dp(8), dp(6), dp(8), 0);
        main.addView(pathBar);

        buildItemsView();
    }

    private TextView columnLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(theme.inkSecondary);
        label.setTextSize(11.5f);
        return label;
    }

    private static LinearLayout.LayoutParams weighted(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    private View toolButton(int mark, String description, Runnable action) {
        FrameLayout button = new FrameLayout(this);
        button.setFocusable(true);
        button.setClickable(true);
        button.setContentDescription(description);
        Marks glyph = new Marks(mark);
        glyph.setInk(theme.ink);
        ImageView image = new ImageView(this);
        image.setImageDrawable(glyph);
        button.addView(image, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        final GradientDrawable rest = Sheets.rounded(theme.hover, dp(17));
        final GradientDrawable lit = Sheets.rounded(theme.accent, dp(17));
        button.setBackground(rest);
        button.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(focused ? lit : rest);
            glyph.setInk(focused ? 0xFFFFFFFF : theme.ink);
        });
        button.setOnClickListener(v -> action.run());
        button.setOnKeyListener(this::onToolbarKey);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(34), dp(34));
        params.setMarginStart(dp(6));
        button.setLayoutParams(params);
        return button;
    }

    private boolean onToolbarKey(View view, int code, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
            focusContent();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP) {
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && view == backButton) {
            focusSidebar();
            return true;
        }
        return false;
    }

    private void buildItemsView() {
        if (itemsView != null) {
            content.removeView(itemsView);
        }
        AbsListView view;
        if (gridMode) {
            GridView grid = new GridView(this);
            grid.setNumColumns(columnCount());
            grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
            grid.setVerticalSpacing(dp(6));
            view = grid;
        } else {
            ListView list = new ListView(this);
            list.setDivider(null);
            view = list;
        }
        view.setSelector(new ColorDrawable(0));
        view.setVerticalScrollBarEnabled(false);
        view.setClipToPadding(false);
        view.setOnItemClickListener((parent, v, position, id) -> openEntry(position));
        view.setOnItemLongClickListener((parent, v, position, id) -> {
            showItemMenu(entries.get(position));
            return true;
        });
        view.setOnKeyListener(this::onItemsKey);
        // The selection is only drawn while this list holds the focus.
        view.setOnFocusChangeListener((v, focused) -> ((AbsListView) v).invalidateViews());
        content.addView(view, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        view.setAdapter(adapter);
        itemsView = view;
        columnHeader.setVisibility(gridMode ? View.GONE : View.VISIBLE);
    }

    private int columnCount() {
        float density = getResources().getDisplayMetrics().density;
        int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels / density);
        return Math.max(3, (widthDp - 256) / 112);
    }

    private boolean onItemsKey(View view, int code, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || entries.isEmpty()) {
            return false;
        }
        int position = itemsView.getSelectedItemPosition();
        int columns = gridMode ? ((GridView) itemsView).getNumColumns() : 1;
        columns = Math.max(1, columns);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (position % columns == 0) {
                    focusSidebar();
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return (position + 1) % columns == 0 || position == entries.size() - 1;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (position < columns) {
                    focusToolbar();
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return position + columns >= entries.size()
                        && (position / columns) == ((entries.size() - 1) / columns);
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_INFO:
                showSelectedMenu();
                return true;
            default:
                return false;
        }
    }

    private void focusToolbar() {
        (backButton.getVisibility() == View.VISIBLE && backButton.isEnabled()
                ? backButton : toolbar.getChildAt(toolbar.getChildCount() - 1)).requestFocus();
    }

    private void focusContent() {
        if (itemsView.getVisibility() == View.VISIBLE && !entries.isEmpty()) {
            itemsView.requestFocus();
        } else if (actionButton.getVisibility() == View.VISIBLE) {
            actionButton.requestFocus();
        }
    }

    private void focusSidebar() {
        for (SideRow row : sideRows) {
            if (row.place == place) {
                row.requestFocus();
                return;
            }
        }
        if (!sideRows.isEmpty()) {
            sideRows.get(0).requestFocus();
        }
    }

    // --- The sidebar --------------------------------------------------------

    private void readPlaces() {
        Places.Place current = place;
        places.clear();
        sideRows.clear();
        sidebarList.removeAllViews();
        addSection("Favorites", Places.favourites(this,
                getIntent().getStringExtra(EXTRA_OWN_LABEL),
                getIntent().getStringExtra(EXTRA_OWN_FOLDER)));
        addSection("Locations", Places.locations(this));
        // The rebuilt row of the place that is open stays the open one.
        if (current != null) {
            for (Places.Place candidate : places) {
                if (candidate.folder.equals(current.folder)) {
                    place = candidate;
                }
            }
        }
        markOpenPlace();
    }

    private void addSection(String title, List<Places.Place> section) {
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(theme.inkSecondary);
        heading.setTextSize(11.5f);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setPadding(dp(8), dp(12), 0, dp(4));
        sidebarList.addView(heading);
        for (Places.Place item : section) {
            places.add(item);
            SideRow row = new SideRow(item);
            sideRows.add(row);
            sidebarList.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
        }
    }

    private void markOpenPlace() {
        for (SideRow row : sideRows) {
            row.showState();
        }
    }

    /** One place in the sidebar. */
    private final class SideRow extends LinearLayout {
        final Places.Place place;
        private final Marks glyph;
        private final TextView label;
        private final GradientDrawable lit = Sheets.rounded(theme.accent, dp(7));
        private final GradientDrawable open = Sheets.rounded(theme.hover, dp(7));

        SideRow(Places.Place place) {
            super(FilesActivity.this);
            this.place = place;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setFocusable(true);
            setClickable(true);
            setPadding(dp(8), 0, dp(8), 0);
            setContentDescription(place.label);
            glyph = new Marks(place.mark);
            ImageView image = new ImageView(getContext());
            image.setImageDrawable(glyph);
            addView(image, new LayoutParams(dp(17), dp(17)));
            label = new TextView(getContext());
            label.setText(place.label);
            label.setTextSize(13.5f);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LayoutParams labelParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            labelParams.setMarginStart(dp(9));
            addView(label, labelParams);
            setOnFocusChangeListener((v, focused) -> showState());
            setOnClickListener(v -> {
                FilesActivity.this.open(place, place.folder, null);
                focusContent();
            });
            setOnKeyListener((v, code, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (!entries.isEmpty() || actionButton.getVisibility() == VISIBLE) {
                        focusContent();
                    } else {
                        focusToolbar();
                    }
                    return true;
                }
                return event.getAction() == KeyEvent.ACTION_DOWN
                        && code == KeyEvent.KEYCODE_DPAD_LEFT;
            });
            showState();
        }

        void showState() {
            boolean focused = isFocused();
            setBackground(focused ? lit : place == FilesActivity.this.place ? open : null);
            glyph.setInk(focused ? 0xFFFFFFFF : theme.accent);
            label.setTextColor(focused ? 0xFFFFFFFF : theme.ink);
        }
    }

    // --- Opening a folder ---------------------------------------------------

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void open(Places.Place target, File dir, String select) {
        if (folder != null && itemsView != null) {
            rememberSelection();
        }
        place = target;
        folder = dir;
        markOpenPlace();
        titleView.setText(dir.equals(target.folder) ? target.label : dir.getName());
        backButton.setAlpha(dir.equals(target.folder) ? 0.4f : 1f);
        updatePathBar();
        if (target.needsAccess && !hasStorageAccess()) {
            showAccessNeeded();
            return;
        }
        load(select);
    }

    private void rememberSelection() {
        Object selected = itemsView.getSelectedItem();
        if (selected instanceof FileLogic.Entry) {
            lastSelected.put(folder.getPath(), ((FileLogic.Entry) selected).name);
        }
    }

    private void updatePathBar() {
        StringBuilder path = new StringBuilder();
        for (File crumb : FileLogic.crumbs(place.folder, folder)) {
            if (path.length() > 0) {
                path.append("  ›  ");
            }
            path.append(crumb.equals(place.folder) ? place.label : crumb.getName());
        }
        pathBar.setText(path);
    }

    private void load(final String select) {
        final int ticket = ++generation;
        final File dir = folder;
        final int mode = sortMode;
        final boolean hidden = showHidden;
        lister.execute(() -> {
            File[] children = null;
            try {
                children = dir.listFiles();
            } catch (SecurityException ignored) {
                // Reported below, the same as any folder that cannot be read.
            }
            List<FileLogic.Entry> read = new ArrayList<>(children == null ? 0 : children.length);
            if (children != null) {
                for (File child : children) {
                    read.add(FileLogic.Entry.of(child));
                }
            }
            final boolean failed = children == null;
            final List<FileLogic.Entry> sorted = FileLogic.sorted(read, mode, hidden);
            runOnUiThread(() -> {
                if (ticket == generation && !isDestroyed()) {
                    show(sorted, failed, select);
                }
            });
        });
    }

    private void show(List<FileLogic.Entry> list, boolean failed, String select) {
        boolean hadFocus = itemsView.hasFocus();
        entries = list;
        adapter.notifyDataSetChanged();
        countView.setText(FileLogic.itemCount(list.size()));
        actionButton.setVisibility(View.GONE);
        if (failed) {
            showMessage(dir404() ? "This location is no longer available."
                    : "This folder cannot be opened.");
        } else if (list.isEmpty()) {
            showMessage("This folder is empty.");
        } else {
            message.setVisibility(View.GONE);
            itemsView.setVisibility(View.VISIBLE);
            String wanted = select != null ? select : lastSelected.get(folder.getPath());
            int position = 0;
            for (int i = 0; wanted != null && i < list.size(); i++) {
                if (wanted.equals(list.get(i).name)) {
                    position = i;
                    break;
                }
            }
            itemsView.setSelection(position);
        }
        if (hadFocus && list.isEmpty()) {
            focusToolbar();
        }
    }

    private boolean dir404() {
        return !folder.exists();
    }

    private void showMessage(String text) {
        itemsView.setVisibility(View.GONE);
        message.setText(text);
        message.setVisibility(View.VISIBLE);
    }

    /** Says what is missing and offers the one way there is to give it. */
    private void showAccessNeeded() {
        generation++;
        entries = new ArrayList<>();
        adapter.notifyDataSetChanged();
        countView.setText("");
        itemsView.setVisibility(View.GONE);
        message.setVisibility(View.VISIBLE);
        boolean settingsExist = Build.VERSION.SDK_INT < 30 || accessScreen() != null;
        if (settingsExist) {
            message.setText("Files needs permission to show what is on this TV. "
                    + "It reads nothing until you open a folder, and sends nothing anywhere.");
            actionButton.setText("Allow Access");
            actionButton.setVisibility(View.VISIBLE);
            actionButton.setOnClickListener(v -> requestAccess());
        } else {
            actionButton.setVisibility(View.GONE);
            message.setText("This TV has no screen for allowing access to all files. "
                    + "From a computer, run:\n\nadb shell appops set " + getPackageName()
                    + " MANAGE_EXTERNAL_STORAGE allow");
        }
    }

    private Intent accessScreen() {
        if (Build.VERSION.SDK_INT < 30) {
            return null;
        }
        Intent own = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        if (own.resolveActivity(getPackageManager()) != null) {
            return own;
        }
        Intent all = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
        return all.resolveActivity(getPackageManager()) != null ? all : null;
    }

    private void requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            Intent screen = accessScreen();
            if (screen != null) {
                try {
                    startActivity(screen);
                } catch (RuntimeException e) {
                    toast("The TV would not open that screen.");
                }
            }
            return;
        }
        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == REQUEST_STORAGE && place != null) {
            open(place, folder, null);
            focusContent();
        }
    }

    private void goUp() {
        if (folder == null || place == null || folder.equals(place.folder)) {
            return;
        }
        File parent = folder.getParentFile();
        String came = folder.getName();
        if (parent != null) {
            open(place, parent, came);
        }
    }

    private void openEntry(int position) {
        if (position < 0 || position >= entries.size()) {
            return;
        }
        FileLogic.Entry entry = entries.get(position);
        if (entry.folder) {
            open(place, entry.file, null);
            itemsView.requestFocus();
        } else if (entry.kind == FileLogic.IMAGE || entry.kind == FileLogic.TEXT) {
            showQuickLook(position);
        } else {
            openElsewhere(entry);
        }
    }

    private void showQuickLook(int position) {
        rememberSelection();
        quickLook = new QuickLook(this, theme, entries, position, low, () -> {
            root.removeView(quickLook);
            quickLook = null;
            itemsView.requestFocus();
        });
        root.addView(quickLook, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        quickLook.requestFocus();
    }

    private void openElsewhere(FileLogic.Entry entry) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(FilesProvider.uriFor(this, entry.file), FileLogic.mimeOf(entry.name));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            sheet = Sheets.alert(root, theme, "No App Can Open This",
                    "Nothing on this TV opens " + FileLogic.kindName(entry.kind).toLowerCase()
                            + " files like “" + entry.name + "”.",
                    new String[]{"OK"}, 0, -1, this::backToItems, index -> backToItems());
        } catch (RuntimeException e) {
            toast("Could not open " + entry.name);
        }
    }

    private void backToItems() {
        if (itemsView.getVisibility() == View.VISIBLE && !entries.isEmpty()) {
            itemsView.requestFocus();
        } else {
            focusToolbar();
        }
    }

    // --- Menus --------------------------------------------------------------

    private void showSelectedMenu() {
        Object selected = itemsView.getSelectedItem();
        if (selected instanceof FileLogic.Entry) {
            showItemMenu((FileLogic.Entry) selected);
        } else {
            toast("Nothing is selected");
        }
    }

    private void showItemMenu(final FileLogic.Entry entry) {
        final String shareClass = getIntent().getStringExtra(EXTRA_SHARE_CLASS);
        List<String> rows = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        rows.add("Open");
        actions.add(() -> openEntry(entries.indexOf(entry)));
        rows.add("Copy");
        actions.add(() -> setClipboard(entry.file, false));
        rows.add("Move");
        actions.add(() -> setClipboard(entry.file, true));
        rows.add("Rename");
        actions.add(() -> askRename(entry));
        rows.add("Get Info");
        actions.add(() -> showInfo(entry));
        if (shareClass != null) {
            rows.add(getIntent().getStringExtra(EXTRA_SHARE_LABEL));
            actions.add(() -> share(shareClass, entry.file));
        }
        rows.add("Delete");
        actions.add(() -> askDelete(entry));
        sheet = Sheets.menu(root, theme, entry.name, rows.toArray(new String[0]), -1,
                rows.size() - 1, this::backToItems, index -> {
                    backToItems();
                    actions.get(index).run();
                });
    }

    private void share(String shareClass, File file) {
        try {
            startActivity(new Intent().setClassName(this, shareClass)
                    .putExtra(getIntent().getStringExtra(EXTRA_SHARE_PATH), file.getAbsolutePath())
                    .putExtra(getIntent().getStringExtra(EXTRA_SHARE_NAME), file.getName()));
        } catch (RuntimeException e) {
            toast("Could not share " + file.getName());
        }
    }

    private void showSortMenu() {
        String[] rows = {"Name", "Date Modified", "Size", "Kind",
                showHidden ? "Hide Hidden Files" : "Show Hidden Files"};
        final View back = getCurrentFocus();
        sheet = Sheets.menu(root, theme, "Sort By", rows, sortMode, -1, () -> refocus(back), index -> {
            if (index < 4) {
                sortMode = index;
                prefs.edit().putInt("sort", index).apply();
            } else {
                showHidden = !showHidden;
                prefs.edit().putBoolean("hidden", showHidden).apply();
            }
            load(null);
            refocus(back);
        });
    }

    private void refocus(View view) {
        if (view != null) {
            view.requestFocus();
        }
    }

    private void toggleView() {
        rememberSelection();
        gridMode = !gridMode;
        prefs.edit().putBoolean("grid", gridMode).apply();
        // The toolbar button shows the other view, so the window is rebuilt with it.
        recreate();
    }

    private void toggleAppearance() {
        prefs.edit().putBoolean("light", !theme.light).apply();
        recreate();
    }

    private void showInfo(final FileLogic.Entry entry) {
        final String kind = FileLogic.kindName(entry.kind);
        final String when = dateFormat.format(new Date(entry.modified));
        final View back = getCurrentFocus();
        final AtomicBoolean never = new AtomicBoolean(false);
        lister.execute(() -> {
            final long size = FileOps.sizeOf(Collections.singletonList(entry.file), never);
            runOnUiThread(() -> {
                if (!isDestroyed()) {
                    sheet = Sheets.alert(root, theme, entry.name,
                            kind + "\n" + FileLogic.formatSize(size) + "\nModified " + when
                                    + "\n" + entry.file.getParent(),
                            new String[]{"OK"}, 0, -1, () -> refocus(back), i -> refocus(back));
                }
            });
        });
    }

    private void askNewFolder() {
        final View back = getCurrentFocus();
        if (folder == null) {
            return;
        }
        sheet = Sheets.input(root, theme, "New Folder", FileLogic.freeName("untitled folder", folder, false),
                "Create", name -> {
                    refocus(back);
                    String problem = FileLogic.nameProblem(name);
                    if (problem != null) {
                        complain("That Name Cannot Be Used", problem);
                    } else if (new File(folder, name).exists()) {
                        complain("That Name Is Taken", "Something in this folder is already called “"
                                + name + "”.");
                    } else if (new File(folder, name).mkdir()) {
                        load(name);
                    } else {
                        complain("The Folder Was Not Created",
                                "This location does not allow new folders.");
                    }
                });
    }

    private void askRename(final FileLogic.Entry entry) {
        sheet = Sheets.input(root, theme, "Rename", entry.name, "Rename", name -> {
            backToItems();
            if (name.equals(entry.name)) {
                return;
            }
            String problem = FileLogic.nameProblem(name);
            File target = new File(entry.file.getParentFile(), name);
            if (problem != null) {
                complain("That Name Cannot Be Used", problem);
            } else if (target.exists()) {
                complain("That Name Is Taken",
                        "Something in this folder is already called “" + name + "”.");
            } else if (entry.file.renameTo(target)) {
                load(name);
            } else {
                complain("It Was Not Renamed", "This location does not allow changes.");
            }
        });
    }

    private void complain(String title, String why) {
        final View back = getCurrentFocus();
        sheet = Sheets.alert(root, theme, title, why, new String[]{"OK"}, 0, -1,
                () -> refocus(back), i -> refocus(back));
    }

    private void askDelete(final FileLogic.Entry entry) {
        sheet = Sheets.alert(root, theme, "Delete “" + entry.name + "”?",
                "This cannot be undone.", new String[]{"Cancel", "Delete"}, 0, 1,
                this::backToItems, index -> {
                    backToItems();
                    if (index != 1) {
                        return;
                    }
                    runJob("Deleting", cancel -> {
                        return FileOps.delete(Collections.singletonList(entry.file), cancel);
                    });
                });
    }

    private void setClipboard(File file, boolean move) {
        clipboard = file;
        clipboardMove = move;
        pasteButton.setText((move ? "Move Here: " : "Paste: ") + ellipsized(file.getName()));
        pasteButton.setVisibility(View.VISIBLE);
    }

    private static String ellipsized(String name) {
        return name.length() <= 18 ? name : name.substring(0, 8) + "…"
                + name.substring(name.length() - 8);
    }

    private void askPaste() {
        if (clipboard == null || folder == null) {
            return;
        }
        final View back = getCurrentFocus();
        final String action = clipboardMove ? "Move Here" : "Paste Here";
        sheet = Sheets.menu(root, theme, clipboard.getName(), new String[]{action, "Forget It"}, -1,
                -1, () -> refocus(back), index -> {
                    final File source = clipboard;
                    final boolean move = clipboardMove;
                    clipboard = null;
                    pasteButton.setVisibility(View.GONE);
                    backToItems();
                    if (index != 0) {
                        return;
                    }
                    final File into = folder;
                    runJob(move ? "Moving" : "Copying", cancel -> FileOps.transfer(
                            Collections.singletonList(source), into, move, cancel,
                            (done, total, name) -> reportProgress(done, total, name)));
                });
    }

    // --- Long jobs ----------------------------------------------------------

    private interface Job {
        FileOps.Result run(AtomicBoolean cancel);
    }

    private Sheets.Progress progress;
    private long lastProgressAt;

    private void reportProgress(final long done, final long total, final String name) {
        long now = System.currentTimeMillis();
        // A busy copy reports thousands of times a second; the screen needs a few.
        if (now - lastProgressAt < 100L) {
            return;
        }
        lastProgressAt = now;
        runOnUiThread(() -> {
            if (progress != null) {
                progress.update(done, total, name);
            }
        });
    }

    private void runJob(String title, final Job job) {
        final AtomicBoolean cancel = new AtomicBoolean(false);
        running = cancel;
        progress = new Sheets.Progress(root, theme, title, () -> cancel.set(true));
        sheet = progress.sheet;
        new Thread(() -> {
            FileOps.Result computed;
            try {
                computed = job.run(cancel);
            } catch (RuntimeException e) {
                computed = new FileOps.Result();
                computed.failed = 1;
                computed.problem = "Something went wrong and the job stopped.";
            }
            final FileOps.Result result = computed;
            runOnUiThread(() -> {
                if (isDestroyed()) {
                    return;
                }
                running = null;
                if (progress != null) {
                    progress.sheet.close();
                    progress = null;
                }
                backToItems();
                load(null);
                if (result.failed > 0) {
                    complain(result.done > 0 ? "Some Items Were Not Finished" : "That Did Not Work",
                            result.problem == null ? "This location does not allow it."
                                    : result.problem);
                } else if (result.cancelled) {
                    toast("Stopped. Anything not finished was left out.");
                }
            });
        }, "files-job").start();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    // --- The window's life --------------------------------------------------

    @Override
    public void onBackPressed() {
        if (quickLook != null) {
            quickLook.close();
        } else if (sheet != null && !sheet.closed()) {
            sheet.cancel();
        } else if (folder != null && place != null && !folder.equals(place.folder)) {
            goUp();
            itemsView.requestFocus();
        } else {
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        immersive();
        // Permission may have been given, and a drive plugged in or pulled out.
        View focused = getCurrentFocus();
        if (sheet == null || sheet.closed()) {
            readPlaces();
            if (quickLook == null && place != null && folder != null) {
                open(place, folder, null);
            }
            if (focused == null || !focused.isAttachedToWindow()) {
                focusSidebar();
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            immersive();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        thumbs.evictAll();
    }

    @Override
    protected void onDestroy() {
        generation++;
        if (running != null) {
            running.set(true);
        }
        lister.shutdownNow();
        thumbnailer.shutdownNow();
        thumbs.evictAll();
        super.onDestroy();
    }

    // --- The list -----------------------------------------------------------

    private final class ItemAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public FileLogic.Entry getItem(int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            ItemCell cell = convert instanceof ItemCell ? (ItemCell) convert
                    : new ItemCell(FilesActivity.this, theme, gridMode, dateFormat);
            FileLogic.Entry entry = entries.get(position);
            cell.bind(entry, position % 2 == 0);
            if (gridMode && !low && entry.kind == FileLogic.IMAGE) {
                loadThumb(cell, entry);
            }
            return cell;
        }
    }

    private void loadThumb(final ItemCell cell, final FileLogic.Entry entry) {
        final String key = entry.file.getPath() + "@" + entry.modified;
        Bitmap cached = thumbs.get(key);
        if (cached != null) {
            cell.setThumb(cached);
            return;
        }
        final int side = dp(62);
        try {
            thumbnailer.execute(() -> {
                // Scrolled away already, so this picture is not wanted any more.
                if (cell.entry() != entry) {
                    return;
                }
                final Bitmap bitmap = decodeThumb(entry.file, side);
                if (bitmap == null) {
                    return;
                }
                runOnUiThread(() -> {
                    thumbs.put(key, bitmap);
                    if (cell.entry() == entry) {
                        cell.setThumb(bitmap);
                    }
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // The window is closing.
        }
    }

    private static Bitmap decodeThumb(File file, int side) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            int sample = 1;
            while (Math.min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }
}
