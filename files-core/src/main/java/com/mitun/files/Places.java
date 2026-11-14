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
        File folder = Environment.getExternalStoragePublicDirectory(directory);
        // A public folder that does not exist yet is a dead row, not a place.
        if (folder.isDirectory()) {
            out.add(new Place(label, mark, folder, true));
        }
    }

    /**
     * The TV's own storage and any USB drives. The app's folder on each drive is the
     * one path the platform hands out, so the drive's top is worked out from it.
     */
    static List<Place> locations(Context context) {
        List<Place> out = new ArrayList<>();
        File internal = Environment.getExternalStorageDirectory();
        // Only a mounted drive belongs in the sidebar; a read-only one can be
        // browsed even if it cannot be written.
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)
                || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            out.add(new Place("TV Storage", Marks.DRIVE, internal, true));
        }
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
            // The last match, not the first: a mount whose own name says
            // Android must not be cut off above it.
            int cut = path.lastIndexOf("/Android/");
            // The drive's top is normally cut off above Android/. The rare path
            // without it asks the volume for its mount instead of dropping the
            // drive, and as a last resort lists the folder it was handed.
            File top = cut > 0 ? new File(path.substring(0, cut)) : mountRoot(context, dir);
            if (top == null) {
                top = dir;
            }
            // Only the drive's own twin is dropped: a mount that sits inside
            // the internal tree is still a drive of its own.
            if (samePlace(top, internal)) {
                continue;
            }
            out.add(new Place(volumeName(context, dir), Marks.DRIVE, top, true));
        }
        return out;
    }

    /** The mount a volume reports, for a path with no Android folder to cut at. */
    private static File mountRoot(Context context, File dir) {
        // The mount path is only public from API 30; older answers fall back to
        // the folder the platform handed out.
        if (android.os.Build.VERSION.SDK_INT < 30) {
            return null;
        }
        StorageManager manager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        StorageVolume volume = manager == null ? null : manager.getStorageVolume(dir);
        return volume == null ? null : volume.getDirectory();
    }

    /** Same drive even when it answers to two paths, like /sdcard and its real one. */
    static boolean samePlace(File a, File b) {
        try {
            return a.getCanonicalPath().equals(b.getCanonicalPath());
        } catch (java.io.IOException e) {
            return a.equals(b);
        }
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
