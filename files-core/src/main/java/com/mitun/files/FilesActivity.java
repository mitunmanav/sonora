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
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
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

    private final ExecutorService lister = Workers.background("files-list");
    private final ExecutorService thumbnailer = Workers.background("files-thumbs");
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
    private View viewButton;
    private Marks viewGlyph;
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
    /** The folder a listing was last asked for, so a resume does not list it twice. */
    private File listed;
    /** The folder a lister pass is still reading; a resume must not queue it again. */
    private File requested;
    private int generation;
    private boolean gridMode;
    private int sortMode;
    private boolean showHidden;
    private Sheets.Sheet sheet;
    private QuickLook quickLook;
    private File clipboard;
    private boolean clipboardMove;
    private AtomicBoolean running;
    private AtomicBoolean sizing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("files", MODE_PRIVATE);
        bundled = getIntent().hasExtra(EXTRA_LIGHT);
        boolean light = bundled ? getIntent().getBooleanExtra(EXTRA_LIGHT, false)
                : getBoolean("light", false);
        theme = new FilesTheme(light);
        low = FilesTheme.lowMemory(this);
        gridMode = getBoolean("grid", true);
        sortMode = getInt("sort", FileLogic.SORT_NAME);
        showHidden = getBoolean("hidden", false);
        dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
        immersive();
        buildWindow();
        setContentView(root);
        readPlaces();
        if (savedInstanceState != null) {
            restore(savedInstanceState);
        }
        if (place == null && !places.isEmpty()) {
            open(places.get(0), places.get(0).folder, null);
        }
        if (!sideRows.isEmpty()) {
            sideRows.get(0).post(() -> focusSidebar());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // singleTask: a second launch reaches the one live screen. The share
        // and own-folder extras are read lazily from the intent, so keeping
        // the newest one is all that is needed.
        setIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        // A rebuild for a new look must not lose where the user was.
        if (place != null) {
            state.putString("place", place.folder.getAbsolutePath());
        }
        if (folder != null) {
            state.putString("folder", folder.getAbsolutePath());
        }
        if (clipboard != null) {
            state.putString("clipboard", clipboard.getAbsolutePath());
            state.putBoolean("move", clipboardMove);
        }
        state.putSerializable("last", new HashMap<>(lastSelected));
    }

    @SuppressWarnings("unchecked")
    private void restore(Bundle state) {
        String at = state.getString("place");
        if (at != null) {
            for (Places.Place candidate : places) {
                if (candidate.folder.getAbsolutePath().equals(at)) {
                    place = candidate;
                }
            }
        }
        String dir = state.getString("folder");
        if (place != null && dir != null) {
            open(place, new File(dir), null);
        }
        String held = state.getString("clipboard");
        if (held != null) {
            setClipboard(new File(held), state.getBoolean("move"));
        }
        Object last = state.getSerializable("last");
        if (last instanceof Map) {
            lastSelected.putAll((Map<String, String>) last);
        }
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

    // A value stored under the wrong type reads as the default, not a crash.
    private boolean getBoolean(String key, boolean fallback) {
        try {
            return prefs.getBoolean(key, fallback);
        } catch (ClassCastException e) {
            return fallback;
        }
    }

    private int getInt(String key, int fallback) {
        try {
            return prefs.getInt(key, fallback);
        } catch (ClassCastException e) {
            return fallback;
        }
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

        viewGlyph = new Marks(gridMode ? Marks.LIST : Marks.GRID);
        viewButton = toolButton(viewGlyph, gridMode ? "View as list" : "View as icons",
                this::toggleView);
        toolbar.addView(viewButton);
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
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && code == (rtl() ? KeyEvent.KEYCODE_DPAD_RIGHT
                            : KeyEvent.KEYCODE_DPAD_LEFT)) {
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
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        return label;
    }

    private static LinearLayout.LayoutParams weighted(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    private View toolButton(int mark, String description, Runnable action) {
        return toolButton(new Marks(mark), description, action);
    }

    private View toolButton(Marks glyph, String description, Runnable action) {
        FrameLayout button = new FrameLayout(this);
        button.setFocusable(true);
        button.setClickable(true);
        button.setContentDescription(description);
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
        if (code == (rtl() ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT)
                && view == backButton) {
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
            // Between a list shrinking and the layout that knows it, the press
            // can land on a row that no longer exists.
            FileLogic.Entry entry = adapter.getItem(position);
            if (entry != null) {
                showItemMenu(entry);
            }
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
        // Between a list shrinking and the layout that clamps it, the selected
        // position can point past the end.
        if (position < 0 || position >= entries.size()) {
            return false;
        }
        int columns = gridMode ? ((GridView) itemsView).getNumColumns() : 1;
        columns = Math.max(1, columns);
        // Under RTL the sidebar sits on the right: the edges the two keys guard swap.
        boolean rtl = rtl();
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (!rtl && position % columns == 0) {
                    focusSidebar();
                    return true;
                }
                return rtl && ((position + 1) % columns == 0 || position == entries.size() - 1);
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (rtl && position % columns == 0) {
                    focusSidebar();
                    return true;
                }
                return !rtl && ((position + 1) % columns == 0 || position == entries.size() - 1);
            case KeyEvent.KEYCODE_DPAD_UP:
                if (position < columns) {
                    focusToolbar();
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                // No cell below this one: stay, rather than fall out of the grid.
                return position + columns >= entries.size();
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

    /** Mirrored layouts put the sidebar on the right; key directions follow the pixels. */
    private boolean rtl() {
        return getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL;
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
        if (section.isEmpty()) {
            return;
        }
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
                int toward = rtl() ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
                int away = rtl() ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
                if (event.getAction() == KeyEvent.ACTION_DOWN && code == toward) {
                    if (!entries.isEmpty() || actionButton.getVisibility() == VISIBLE) {
                        focusContent();
                    } else {
                        focusToolbar();
                    }
                    return true;
                }
                return event.getAction() == KeyEvent.ACTION_DOWN && code == away;
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
        // The old folder's entries leave now: while the new listing is read the
        // grid must not keep acting on them under the new path.
        entries = new ArrayList<>();
        adapter.notifyDataSetChanged();
        markOpenPlace();
        boolean atTop = dir.equals(target.folder);
        titleView.setText(atTop ? target.label : dir.getName());
        backButton.setAlpha(atTop ? 0.4f : 1f);
        backButton.setEnabled(!atTop);
        backButton.setFocusable(!atTop);
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
        final File dir = folder;
        if (dir == null) {
            return;
        }
        final int ticket = ++generation;
        requested = dir;
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
            // This too waits on the drive, so it is answered here, off the screen.
            final boolean gone = failed && !dir.exists();
            final List<FileLogic.Entry> sorted = FileLogic.sorted(read, mode, hidden);
            runOnUiThread(() -> {
                if (ticket == generation && !isDestroyed()) {
                    show(sorted, failed, gone, select);
                }
            });
        });
    }

    private void show(List<FileLogic.Entry> list, boolean failed, boolean gone, String select) {
        boolean hadFocus = itemsView.hasFocus();
        requested = null;
        entries = list;
        adapter.notifyDataSetChanged();
        // A listing that failed is asked again on the next resume.
        if (!failed) {
            listed = folder;
        }
        countView.setText(FileLogic.itemCount(list.size()));
        actionButton.setVisibility(View.GONE);
        if (failed) {
            showMessage(gone ? "This location is no longer available."
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

    private void showMessage(String text) {
        itemsView.setVisibility(View.GONE);
        message.setText(text);
        message.setVisibility(View.VISIBLE);
    }

    /** Says what is missing and offers the one way there is to give it. */
    private void showAccessNeeded() {
        generation++;
        // The generation bump drops any list still in flight; its marker goes too.
        requested = null;
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
        // The list is gone, so the one thing left to press gets the focus —
        // unless a sheet is up: a resume can reach here underneath one, and
        // keys must stay with what the user can see.
        if (sheet == null || sheet.closed()) {
            focusContent();
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
            // The row this was asked on has been listed away since.
            toast("That item is no longer there.");
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
        // Only reachable through a race, but only one may live: a second would
        // bury the first still attached, with its decoder thread.
        if (quickLook != null) {
            quickLook.close();
        }
        rememberSelection();
        quickLook = new QuickLook(this, theme, entries, position, low, () -> {
            root.removeView(quickLook);
            quickLook = null;
            backToItems();
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
                    "Nothing on this TV opens " + FileLogic.kindName(entry.kind)
                            .toLowerCase(Locale.ROOT) + " files like “" + entry.name + "”.",
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

    /** Where this entry sits now; a copy kept since a reload is found by its path. */
    private int positionOf(FileLogic.Entry entry) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).file.getPath().equals(entry.file.getPath())) {
                return i;
            }
        }
        return -1;
    }

    private void showItemMenu(final FileLogic.Entry entry) {
        final String shareClass = getIntent().getStringExtra(EXTRA_SHARE_CLASS);
        List<String> rows = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        rows.add("Open");
        actions.add(() -> openEntry(positionOf(entry)));
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
        // The button offers the other view, so it gets the other mark: the list is
        // the only thing that actually needs rebuilding, and the window stays.
        viewGlyph.setKind(gridMode ? Marks.LIST : Marks.GRID);
        viewButton.setContentDescription(gridMode ? "View as list" : "View as icons");
        boolean hadFocus = itemsView.hasFocus();
        int position = itemsView.getSelectedItemPosition();
        buildItemsView();
        if (position > 0) {
            itemsView.setSelection(position);
        }
        if (hadFocus) {
            itemsView.requestFocus();
        }
    }

    private void toggleAppearance() {
        prefs.edit().putBoolean("light", !theme.light).apply();
        recreate();
    }

    private void showInfo(final FileLogic.Entry entry) {
        final String kind = FileLogic.kindName(entry.kind);
        // A file that will not say when it changed gets a dash, not the epoch.
        final String when = entry.modified <= 0 ? "--"
                : dateFormat.format(new Date(entry.modified));
        final String where = entry.file.getParent() == null
                ? entry.file.getPath() : entry.file.getParent();
        final View back = getCurrentFocus();
        // Counting a whole tree runs on its own thread so the listings behind it
        // are never held up; answering the sheet stops the count.
        final AtomicBoolean stop = new AtomicBoolean(false);
        sizing = stop;
        final Sheets.Sheet info = sheet = Sheets.alert(root, theme, entry.name,
                kind + "\n…\nModified " + when + "\n" + where,
                new String[]{"OK"}, 0, -1,
                () -> { stop.set(true); refocus(back); },
                i -> { stop.set(true); refocus(back); });
        Thread counting = new Thread(() -> {
            long size;
            try {
                size = FileOps.sizeOf(Collections.singletonList(entry.file), stop);
            } catch (Throwable failure) {
                // A tree too deep for the stack still answers rather than hangs.
                size = -1L;
            }
            final long counted = size;
            runOnUiThread(() -> {
                if (!isDestroyed() && !info.closed()) {
                    info.message(kind + "\n" + (counted < 0L ? "Cannot be counted"
                            : FileLogic.formatSize(counted))
                            + "\nModified " + when + "\n" + where);
                }
            });
        }, "files-info");
        counting.setDaemon(true);
        counting.start();
    }

    private void askNewFolder() {
        final View back = getCurrentFocus();
        if (folder == null) {
            return;
        }
        final File dir = folder;
        // Even the first free name is found off the screen: a dead drive can hold
        // these calls for seconds.
        lister.execute(() -> {
            final String free = FileLogic.freeName("untitled folder", dir, false);
            runOnUiThread(() -> {
                // A job that started during the wait owns the sheet space now;
                // cancelling it would stop work the user asked for.
                if (isDestroyed() || !dir.equals(folder) || quickLook != null
                        || running != null) {
                    return;
                }
                // A sheet that appeared during the wait answers first rather
                // than stacking under a second one.
                if (sheet != null && !sheet.closed()) {
                    sheet.cancel();
                }
                sheet = Sheets.input(root, theme, "New Folder", free, "Create",
                        () -> refocus(back), name -> {
                    refocus(back);
                    String problem = FileLogic.nameProblem(name);
                    if (problem != null) {
                        complain("That Name Cannot Be Used", problem);
                        return;
                    }
                    final File made = new File(dir, name);
                    lister.execute(() -> {
                        final boolean taken = FileLogic.taken(made);
                        final boolean created = !taken && made.mkdir();
                        runOnUiThread(() -> {
                            if (isDestroyed()) {
                                return;
                            }
                            if (taken) {
                                complain("That Name Is Taken",
                                        "Something in this folder is already called “"
                                                + name + "”.");
                            } else if (created) {
                                load(name);
                            } else {
                                complain("The Folder Was Not Created",
                                        "This location does not allow new folders.");
                            }
                        });
                    });
                });
            });
        });
    }

    private void askRename(final FileLogic.Entry entry) {
        sheet = Sheets.input(root, theme, "Rename", entry.name, "Rename",
                this::backToItems, name -> {
            backToItems();
            if (name.equals(entry.name)) {
                return;
            }
            String problem = FileLogic.nameProblem(name);
            if (problem != null) {
                complain("That Name Cannot Be Used", problem);
                return;
            }
            final File target = new File(entry.file.getParentFile(), name);
            lister.execute(() -> {
                // A filesystem that cannot tell letter cases apart reports the very
                // file being renamed as taken; that rename is allowed.
                final boolean taken = FileLogic.taken(target) && !sameFile(entry.file, target);
                final boolean renamed = !taken && entry.file.renameTo(target);
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    if (taken) {
                        complain("That Name Is Taken",
                                "Something in this folder is already called “" + name + "”.");
                    } else if (renamed) {
                        load(name);
                    } else {
                        complain("It Was Not Renamed", "This location does not allow changes.");
                    }
                });
            });
        });
    }

    /** Whether two paths arrive at the same file, however they were written. */
    private static boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalPath().equals(b.getCanonicalPath());
        } catch (IOException e) {
            return false;
        }
    }

    /** The name a paste lands under, picked the way FileOps.transfer picks it. */
    private static String pastedName(File source, File into, boolean move) {
        File parent = source.getParentFile();
        boolean same = parent != null && sameFile(parent, into);
        return move && same ? source.getName() : FileLogic.freeName(source.getName(), into, same);
    }

    private void complain(String title, String why) {
        // A late answer can land over a sheet opened since the question was
        // asked; one modal at a time. A running job keeps its sheet: stopping
        // it to show a message would cost the user real work.
        if (running == null && sheet != null && !sheet.closed()) {
            sheet.cancel();
        }
        // Read after the cancel: the view holding focus may have died with it.
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
                        return FileOps.delete(Collections.singletonList(entry.file), cancel,
                                (done, total, name) -> reportProgress(done, total, name));
                    }, after(entry));
                });
    }

    /** The row to land on once this one is gone: the next, or the one above it. */
    private String after(FileLogic.Entry entry) {
        int at = positionOf(entry);
        if (at < 0) {
            return null;
        }
        if (at + 1 < entries.size()) {
            return entries.get(at + 1).name;
        }
        return at > 0 ? entries.get(at - 1).name : null;
    }

    private void setClipboard(File file, boolean move) {
        clipboard = file;
        clipboardMove = move;
        pasteButton.setText((move ? "Move Here: " : "Paste: ") + ellipsized(file.getName()));
        pasteButton.setVisibility(View.VISIBLE);
    }

    private static String ellipsized(String name) {
        if (name.length() <= 18) {
            return name;
        }
        // A surrogate pair is kept whole rather than cut in half.
        int head = 8;
        while (head > 0 && Character.isHighSurrogate(name.charAt(head - 1))) {
            head--;
        }
        int tail = name.length() - 8;
        while (tail < name.length() && Character.isLowSurrogate(name.charAt(tail))) {
            tail++;
        }
        return name.substring(0, head) + "…" + name.substring(tail);
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
                    // "Forget It" lets go; a job that refuses the paste does
                    // not — what was held is kept for a second try.
                    if (index == 0 && running == null || index != 0) {
                        clipboard = null;
                        pasteButton.setVisibility(View.GONE);
                    }
                    backToItems();
                    if (index != 0) {
                        return;
                    }
                    final File into = folder;
                    // The name it lands under is what gets selected after, so a
                    // copy arriving as "a copy" is the row that lights. It is
                    // found inside the job, where the disk calls belong.
                    final String[] pasted = {source.getName()};
                    runJob(move ? "Moving" : "Copying", cancel -> {
                        pasted[0] = pastedName(source, into, move);
                        return FileOps.transfer(Collections.singletonList(source), into, move,
                                cancel,
                                (done, total, name) -> reportProgress(done, total, name));
                    }, pasted);
                });
    }

    // --- Long jobs ----------------------------------------------------------

    private interface Job {
        FileOps.Result run(AtomicBoolean cancel);
    }

    private Sheets.Progress progress;
    /** Read on the job's own thread, which changes from job to job. */
    private volatile long lastProgressAt;

    private void reportProgress(final long done, final long total, final String name) {
        long now = android.os.SystemClock.elapsedRealtime();
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

    private void runJob(String title, final Job job, final String select) {
        runJob(title, job, new String[]{select});
    }

    /** The select is a box so the job can put in the name it actually used. */
    private void runJob(String title, final Job job, final String[] select) {
        // One job at a time: a second would take over the progress and running
        // fields while the first still reports into them.
        if (running != null) {
            toast("Another job is still running.");
            return;
        }
        final AtomicBoolean cancel = new AtomicBoolean(false);
        running = cancel;
        progress = new Sheets.Progress(root, theme, title, () -> {
            cancel.set(true);
            backToItems();
        });
        sheet = progress.sheet;
        Thread worker = new Thread(() -> {
            FileOps.Result computed = null;
            try {
                computed = job.run(cancel);
            } catch (RuntimeException e) {
                computed = new FileOps.Result();
                computed.failed = 1;
                computed.problem = "Something went wrong and the job stopped.";
            } finally {
                if (computed == null) {
                    // Even an Error answers the sheet rather than leaving it stuck.
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
                    load(select[0]);
                    // The refresh always lands; the focus and the sheet only
                    // move when nothing newer claimed the screen. A job cancelled
                    // into a Quick Look or a dialog leaves both alone.
                    boolean free = quickLook == null && (sheet == null || sheet.closed());
                    if (free) {
                        backToItems();
                    }
                    if (result.failed > 0) {
                        String why = result.problem == null
                                ? "This location does not allow it." : result.problem;
                        if (free) {
                            complain(result.done > 0 ? "Some Items Were Not Finished"
                                    : "That Did Not Work", why);
                        } else {
                            toast(why);
                        }
                    } else if (result.cancelled) {
                        toast("Stopped. Anything not finished was left out.");
                    }
                });
            }
        }, "files-job");
        worker.setDaemon(true);
        worker.start();
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
        // An open sheet answers for itself, but it is no reason to skip the
        // refresh: a drive change during a dialog is exactly the case.
        View focused = getCurrentFocus();
        readPlaces();
        // A resume over the folder already shown lists nothing again; a place
        // that dropped out of the sidebar, or a folder never listed, does.
        if (quickLook == null && place != null && folder != null
                && !folder.equals(requested)
                && (!folder.equals(listed) || !places.contains(place))) {
            open(place, folder, null);
        }
        if ((sheet == null || sheet.closed())
                && (focused == null || !focused.isAttachedToWindow())) {
            focusSidebar();
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
        // Hidden means released; while the grid still shows, the cache is why
        // it can scroll at all.
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            thumbs.evictAll();
        }
    }

    @Override
    protected void onDestroy() {
        generation++;
        // A Quick Look open at recreate would leak its decoder thread.
        if (quickLook != null) {
            quickLook.close();
        }
        if (running != null) {
            running.set(true);
        }
        if (sizing != null) {
            sizing.set(true);
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
            return position < 0 || position >= entries.size() ? null : entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            ItemCell cell = convert instanceof ItemCell ? (ItemCell) convert
                    : new ItemCell(FilesActivity.this, theme, gridMode, dateFormat);
            FileLogic.Entry entry = getItem(position);
            if (entry == null) {
                cell.bind(null, false);
                return cell;
            }
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
        // A pipe or socket wearing a picture's name would hold the one
        // thumbnailer thread forever.
        if (!file.isFile()) {
            return null;
        }
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
