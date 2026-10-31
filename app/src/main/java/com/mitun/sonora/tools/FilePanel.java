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
        if (external != null && external.isDirectory()) {
            roots.add(external);
        }
        File downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        if (downloads != null && downloads.isDirectory()) {
            roots.add(downloads);
        }
        return roots.toArray(new File[0]);
    }

    private static void showDirectory(ToolActivity host, File dir, String title) {
        host.stack().pushContent(title, dir.getAbsolutePath(), buildList(host, dir));
    }

    private static View buildList(ToolActivity host, File dir) {
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);

        ListView list = new ListView(host);
        EntryAdapter adapter = new EntryAdapter(host, dir);
        adapter.toolbar(list);
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
        content.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        adapter.load();
        return content;
    }

    /** Opens a file with whatever app the device has for it. */
    private static void open(ToolActivity host, File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(com.mitun.sonora.SonoraProvider.uri(file), "*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            host.startActivity(intent);
        } catch (RuntimeException e) {
            Ui.toast(host, "Nothing on this TV can open that");
        }
    }

    private static void showActions(ToolActivity host, File file) {
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
                    Ui.toast(host, "Copied. Choose a folder and paste.");
                    break;
                case 4:
                    pending = file;
                    Ui.toast(host, "Ready to move. Choose a folder and paste.");
                    break;
                case 5:
                    confirmDelete(host, file);
                    break;
                case 6:
                    showProperties(host, file);
                    break;
                default:
                    break;
            }
        });
    }

    private static void confirmDelete(ToolActivity host, File file) {
        host.choose("Delete " + file.getName() + "?",
                new String[]{"Delete", "Keep"}, 1, index -> {
                    if (index == 0) {
                        boolean done = delete(file);
                        Ui.toast(host, done ? "Deleted" : "This TV would not let it be deleted");
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
        private final List<File> entries = new ArrayList<>();
        private int sort = SORT_NAME;
        private String search = "";

        EntryAdapter(ToolActivity host, File dir) {
            this.host = host;
            this.dir = dir;
        }

        /** Storage roots, one level up, and the paste action. */
        void toolbar(ListView list) {
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
                    sort();
                    notifyDataSetChanged();
                });
            }));
            bar.addView(button("Search", () -> askName(host, "Search", search, text -> {
                search = text == null ? "" : text.trim().toLowerCase(Locale.US);
                sort();
                notifyDataSetChanged();
            })));
            list.addHeaderView(bar, null, false);
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
            File target = new File(dir, pending.getName());
            if (target.exists()) {
                Ui.toast(host, "That file is already here");
                return;
            }
            boolean done = FileServer.copyFile(pending, target);
            if (done && pending.getParentFile() != null) {
                pending.delete();
            }
            pending = null;
            Ui.toast(host, done ? "Done" : "This TV would not let it write there");
            notifyDataSetChanged();
        }

        void load() {
            new Thread(() -> {
                File[] children = dir.listFiles();
                final List<File> found = new ArrayList<>();
                if (children != null) {
                    found.addAll(Arrays.asList(children));
                }
                host.runOnUiThread(() -> {
                    entries.clear();
                    entries.addAll(found);
                    sort();
                    notifyDataSetChanged();
                });
            }, "sonora-files").start();
        }

        private void sort() {
            Collections.sort(entries, new Comparator<File>() {
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
            if (search.length() > 0) {
                List<File> keep = new ArrayList<>();
                for (File file : entries) {
                    if (file.getName().toLowerCase(Locale.US).contains(search)) {
                        keep.add(file);
                    }
                }
                entries.clear();
                entries.addAll(keep);
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
            row.setFocusable(true);

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
            more.setFocusable(true);
            more.setContentDescription("More actions");
            more.setOnClickListener(v -> {
                File file = (File) row.getTag();
                if (file != null) {
                    showActions(host, file);
                }
            });
            row.addView(more, new LinearLayout.LayoutParams(Ui.dp(host, 40),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return row;
        }
    }
}
