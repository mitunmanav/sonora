package com.mitun.files;

import android.content.Context;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** The sidebar: the usual folders, then every drive plugged into this TV. */
final class Places {
    static final class Place {
        final String label;
        final int mark;
        final File folder;
        /** False for a folder the app owns, which works without any permission. */
        final boolean needsAccess;

        Place(String label, int mark, File folder, boolean needsAccess) {
            this.label = label;
            this.mark = mark;
            this.folder = folder;
            this.needsAccess = needsAccess;
        }
    }

    private Places() {
    }

    static List<Place> favourites(Context context, String ownLabel, String ownPath) {
        List<Place> out = new ArrayList<>();
        if (ownPath != null) {
            out.add(new Place(ownLabel, Marks.FOLDER, new File(ownPath), false));
        }
        add(out, "Downloads", Marks.DOWNLOADS, Environment.DIRECTORY_DOWNLOADS);
        add(out, "Movies", Marks.MOVIES, Environment.DIRECTORY_MOVIES);
        add(out, "Music", Marks.MUSIC, Environment.DIRECTORY_MUSIC);
        add(out, "Pictures", Marks.PICTURES, Environment.DIRECTORY_PICTURES);
        add(out, "Documents", Marks.DOCUMENTS, Environment.DIRECTORY_DOCUMENTS);
        return out;
    }

    private static void add(List<Place> out, String label, int mark, String directory) {
        out.add(new Place(label, mark, Environment.getExternalStoragePublicDirectory(directory), true));
    }

    /**
     * The TV's own storage and any USB drives. The app's folder on each drive is the
     * one path the platform hands out, so the drive's top is worked out from it.
     */
    static List<Place> locations(Context context) {
        List<Place> out = new ArrayList<>();
        File internal = Environment.getExternalStorageDirectory();
        out.add(new Place("TV Storage", Marks.DRIVE, internal, true));
        File[] dirs = context.getExternalFilesDirs(null);
        if (dirs == null) {
            return out;
        }
        for (File dir : dirs) {
            // A drive that has just been pulled out is a null here.
            if (dir == null) {
                continue;
            }
            String path = dir.getAbsolutePath();
            int cut = path.indexOf("/Android/");
            if (cut <= 0) {
                continue;
            }
            File top = new File(path.substring(0, cut));
            if (!top.equals(internal)) {
                out.add(new Place(volumeName(context, dir), Marks.DRIVE, top, true));
            }
        }
        return out;
    }

    private static String volumeName(Context context, File onVolume) {
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            StorageManager manager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            StorageVolume volume = manager == null ? null : manager.getStorageVolume(onVolume);
            String name = volume == null ? null : volume.getDescription(context);
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        return "USB Drive";
    }
}
