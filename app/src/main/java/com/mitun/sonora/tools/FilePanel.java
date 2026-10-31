package com.mitun.sonora.tools;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Environment;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.mitun.sonora.SystemInfo;
import com.mitun.sonora.ui.SettingRow;
import com.mitun.sonora.ui.Ui;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The file manager.
 *
 * It browses the folders this app is allowed to use, and it is honest about
 * the ones it is not: on a locked-down Android the shared storage can be read
 * but not always written, and a refusal is reported rather than hidden.
 */
public final class FilePanel {
    private static final int SORT_NAME = 0;
    private static final int SORT_SIZE = 1;
    private static final int SORT_DATE = 2;

    /** The file being copied or moved, waiting for a destination. */
    private static File pending;

    private FilePanel() {
    }

    public static void show(ToolActivity host) {
        pending = null;
        showDirectory(host, FileServer.appFolder(host), "Sonora");
    }

    /** The places worth showing: the app's own folder and the shared storage. */
    private static File[] roots(Context context) {
        List<File> roots = new ArrayList<>();
        roots.add(FileServer.appFolder(context));
        File external = Environment.getExternalStorageDirectory();
        if (canList(external)) {
            roots.add(external);
        }
        File downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        if (canList(downloads)) {
            roots.add(downloads);
        }
        return roots.toArray(new File[0]);
    }

    /**
     * True when the folder can really be listed.
     *
     * A locked-down Android lets an app see that the shared storage exists and
     * then refuses to open it. Offering a folder that shows nothing is worse
     * than not offering it.
     */
    private static boolean canList(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        try {
            return dir.listFiles() != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void showDirectory(ToolActivity host, File dir, String title) {
        host.stack().pushContent(title, dir.getAbsolutePath(), buildList(host, dir));
    }

    private static View buildList(ToolActivity host, File dir) {
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);

        ListView list = new ListView(host);
        EntryAdapter adapter = new EntryAdapter(host, dir, content);
        content.addView(adapter.toolbar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final TextView message = new TextView(host);
        message.setTextColor(0xB3FFFFFF);
        message.setTextSize(16);
        message.setPadding(Ui.dp(host, 22), Ui.dp(host, 16), Ui.dp(host, 22), Ui.dp(host, 16));
        message.setVisibility(View.GONE);
        content.addView(message, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        list.setSelector(Ui.listSelector(host));
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            File file = (File) list.getItemAtPosition(position);
            if (file != null) {
                if (file.isDirectory()) {
                    showDirectory(host, file, file.getName());
                } else {
                    open(host, file);
                }
            }
        });
        // The actions of a file are on the long press, the way the launcher
        // puts them on the app tiles.
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            File file = (File) list.getItemAtPosition(position);
            if (file == null) {
                return false;
            }
            showActions(host, adapter, file);
            return true;
        });
        adapter.load(list, message);
        return content;
    }

    /** Opens a file with whatever app the device has for it. */
    private static void open(ToolActivity host, File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        // The type comes from the provider, which knows the file's extension.
        // Announcing "*/*" would leave the receiving app with no idea what it
        // is being asked to open.
        intent.setData(com.mitun.sonora.SonoraProvider.uri(file));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            host.startActivity(intent);
        } catch (RuntimeException e) {
            Ui.toast(host, "Nothing on this TV can open that");
        }
    }

    private static void showActions(ToolActivity host, EntryAdapter adapter, File file) {
        String[] options = {"Open", "Share over the network", "Rename", "Copy", "Move",
                "Delete", "Properties"};
        host.choose(file.getName(), options, 0, index -> {
            switch (index) {
                case 0:
                    open(host, file);
                    break;
                case 1:
                    ShareActivity.start(host, file);
                    break;
                case 2:
                    askName(host, "Rename", file.getName(), name -> rename(host, file, name));
                    break;
                case 3:
                    pending = file;
                    adapter.rebuildToolbar();
                    Ui.toast(host, "Copied. Choose a folder and paste.");
                    break;
                case 4:
                    pending = file;
                    adapter.rebuildToolbar();
                    Ui.toast(host, "Ready to move. Choose a folder and paste.");
                    break;
                case 5:
                    confirmDelete(host, adapter, file);
                    break;
                case 6:
                    showProperties(host, file);
                    break;
                default:
                    break;
            }
        });
    }

    private static void confirmDelete(ToolActivity host, EntryAdapter adapter, File file) {
        host.choose("Delete " + file.getName() + "?",
                new String[]{"Delete", "Keep"}, 1, index -> {
                    if (index == 0) {
                        // Deleting a folder walks every file in it. On a slow
                        // card that is long enough to be an ANR.
                        new Thread(() -> {
                            final boolean done = delete(file);
                            host.runOnUiThread(() -> {
                                Ui.toast(host, done ? "Deleted"
                                        : "This TV would not let it be deleted");
                                adapter.reload();
                            });
                        }, "sonora-delete").start();
                    }
                });
    }

    private static boolean delete(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    delete(child);
                }
            }
        }
        return file.delete();
    }

    private static void rename(ToolActivity host, File file, String name) {
        if (name == null || name.trim().length() == 0) {
            return;
        }
        File target = new File(file.getParentFile(), name.trim());
        if (target.exists()) {
            Ui.toast(host, "That name is already taken");
            return;
        }
        boolean done = file.renameTo(target);
        Ui.toast(host, done ? "Renamed" : "This TV would not let it be renamed");
    }

    private static void showProperties(ToolActivity host, File file) {
        List<SettingRow> rows = new ArrayList<>();
        rows.add(SettingRow.header(file.getName()));
        rows.add(SettingRow.value("Type", file.isDirectory() ? "Folder" : "File"));
        rows.add(SettingRow.value("Size", file.isDirectory() ? "—" : SystemInfo.bytes(file.length())));
        rows.add(SettingRow.value("Path", file.getAbsolutePath()));
        String modified = DateFormat.getDateTimeInstance()
                .format(new Date(file.lastModified()));
        rows.add(SettingRow.value("Modified", modified));
        rows.add(SettingRow.value("Readable", file.canRead() ? "Yes" : "No"));
        rows.add(SettingRow.value("Writable", file.canWrite() ? "Yes" : "No"));
        host.stack().push("Properties", file.getName(), rows);
    }

    /** A plain text field, for renaming and searching. */
    private static void askName(ToolActivity host, String title, String initial,
                                NameCallback onDone) {
        EditText input = new EditText(host);
        input.setText(initial);
        input.setSelectAllOnFocus(true);
        input.setPadding(Ui.dp(host, 16), Ui.dp(host, 12), Ui.dp(host, 16), Ui.dp(host, 12));
        new AlertDialog.Builder(host)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("OK", (dialog, which) -> onDone.onName(input.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** A callback that takes a string. */
    private interface NameCallback {
        void onName(String name);
    }

    /** The rows of the file list, and the toolbar above them. */
    private static final class EntryAdapter extends BaseAdapter {
        private final ToolActivity host;
        private final File dir;
        private final LinearLayout content;
        /** Every file in the folder, before the search. */
        private final List<File> all = new ArrayList<>();
        /** The files on screen, after the search. */
        private final List<File> entries = new ArrayList<>();
        private int sort = SORT_NAME;
        private String search = "";
        private View toolbarView;
        private ListView listView;
        private TextView messageView;

        EntryAdapter(ToolActivity host, File dir, LinearLayout content) {
            this.host = host;
            this.dir = dir;
            this.content = content;
        }

        /** Storage roots, one level up, and the paste action. */
        View toolbar() {
            toolbarView = buildToolbar();
            return toolbarView;
        }

        /** Rebuilds the toolbar, so a paste button can appear when it is needed. */
        void rebuildToolbar() {
            if (content == null || toolbarView == null) {
                return;
            }
            content.removeView(toolbarView);
            toolbarView = buildToolbar();
            content.addView(toolbarView, 0, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        private View buildToolbar() {
            LinearLayout bar = new LinearLayout(host);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setPadding(Ui.dp(host, 14), Ui.dp(host, 8), Ui.dp(host, 14), Ui.dp(host, 4));
            if (pending != null) {
                bar.addView(button("Paste here", () -> paste()));
            }
            bar.addView(button("Up", () -> {
                File parent = dir.getParentFile();
                if (parent != null && parent.isDirectory()) {
                    showDirectory(host, parent, parent.getName());
                }
            }));
            bar.addView(button("Places", () -> showPlaces()));
            bar.addView(button("Sort", () -> {
                String[] options = {"Name", "Size", "Modified"};
                host.choose("Sort by", options, sort, index -> {
                    sort = index;
                    refresh();
                    notifyDataSetChanged();
                });
            }));
            bar.addView(button("Search", () -> askName(host, "Search", search, text -> {
                search = text == null ? "" : text.trim().toLowerCase(Locale.US);
                refresh();
                notifyDataSetChanged();
            })));
            return bar;
        }

        private View button(String text, Runnable action) {
            TextView view = new TextView(host);
            view.setText(text);
            view.setTextColor(Color.WHITE);
            view.setTextSize(15);
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            view.setPadding(Ui.dp(host, 16), Ui.dp(host, 8), Ui.dp(host, 16), Ui.dp(host, 8));
            view.setFocusable(true);
            view.setOnFocusChangeListener((v, focused) ->
                    view.setTextColor(focused ? Color.WHITE : 0x8CFFFFFF));
            view.setOnClickListener(v -> action.run());
            return view;
        }

        private void showPlaces() {
            File[] roots = roots(host);
            String[] names = new String[roots.length];
            for (int i = 0; i < roots.length; i++) {
                names[i] = roots[i].getAbsolutePath();
            }
            host.choose("Places", names, 0, index -> showDirectory(host, roots[index],
                    roots[index].getName()));
        }

        private void paste() {
            if (pending == null) {
                return;
            }
            final File source = pending;
            final File target = new File(dir, source.getName());
            if (target.exists()) {
                Ui.toast(host, "That file is already here");
                return;
            }
            // Copying a folder can take a while, so it is not done here.
            new Thread(() -> {
                final boolean done = FileServer.copyFile(source, target);
                if (done && source.getParentFile() != null) {
                    source.delete();
                }
                host.runOnUiThread(() -> {
                    pending = null;
                    rebuildToolbar();
                    Ui.toast(host, done ? "Done" : "This TV would not let it write there");
                    reload();
                });
            }, "sonora-paste").start();
        }

        void load(final ListView list, final TextView message) {
            listView = list;
            messageView = message;
            new Thread(() -> {
                File[] children = dir.listFiles();
                final List<File> found = new ArrayList<>();
                if (children != null) {
                    found.addAll(Arrays.asList(children));
                }
                final boolean readable = children != null;
                host.runOnUiThread(() -> {
                    all.clear();
                    all.addAll(found);
                    refresh();
                    notifyDataSetChanged();
                    if (!readable) {
                        // The folder exists but this app may not read it. Say
                        // so instead of showing an empty list.
                        list.setVisibility(View.GONE);
                        message.setText("This TV does not let apps open this folder.\n\n"
                                + "Sonora can always read and write its own folder, "
                                + "which is the first place in Places.");
                        message.setVisibility(View.VISIBLE);
                    }
                });
            }, "sonora-files").start();
        }

        /** Reads the folder again. */
        void reload() {
            if (listView != null && messageView != null) {
                load(listView, messageView);
            }
        }

        /** Sorts the folder and applies the search. */
        private void refresh() {
            Collections.sort(all, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    if (a.isDirectory() != b.isDirectory()) {
                        return a.isDirectory() ? -1 : 1;
                    }
                    switch (sort) {
                        case SORT_SIZE:
                            return Long.compare(a.length(), b.length());
                        case SORT_DATE:
                            return Long.compare(b.lastModified(), a.lastModified());
                        default:
                            return a.getName().compareToIgnoreCase(b.getName());
                    }
                }
            });
            entries.clear();
            for (File file : all) {
                if (search.length() == 0
                        || file.getName().toLowerCase(Locale.US).contains(search)) {
                    entries.add(file);
                }
            }
        }

        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public File getItem(int position) {
            return position >= 0 && position < entries.size() ? entries.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = buildRow();
            }
            File file = getItem(position);
            if (file == null) {
                return view;
            }
            view.setTag(file);
            ((TextView) view.findViewWithTag("name")).setText(file.getName());
            ((TextView) view.findViewWithTag("size")).setText(file.isDirectory()
                    ? "Folder" : SystemInfo.bytes(file.length()));
            return view;
        }

        private View buildRow() {
            LinearLayout row = new LinearLayout(host);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(host, 22), Ui.dp(host, 10), Ui.dp(host, 14), Ui.dp(host, 10));
            // The list holds the focus and reports the click. A focusable row
            // stops the list from ever doing that, and OK would do nothing.
            row.setFocusable(false);

            TextView name = new TextView(host);
            name.setTag("name");
            name.setTextColor(Color.WHITE);
            name.setTextSize(17);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(name, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView size = new TextView(host);
            size.setTag("size");
            size.setTextColor(0x8CFFFFFF);
            size.setTextSize(13);
            size.setGravity(Gravity.END);
            row.addView(size, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 0.6f));

            TextView more = new TextView(host);
            more.setText("⋯");
            more.setTextColor(0xB3FFFFFF);
            more.setTextSize(22);
            more.setGravity(Gravity.CENTER);
            // A hint, not a button: the actions are on the long press.
            more.setFocusable(false);
            more.setContentDescription("Hold OK for more actions");
            row.addView(more, new LinearLayout.LayoutParams(Ui.dp(host, 40),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return row;
        }
    }
}
