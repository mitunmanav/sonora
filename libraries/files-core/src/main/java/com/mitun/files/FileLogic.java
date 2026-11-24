package com.mitun.files;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** What the file manager decides, with nothing from Android in it, so it can be tested. */
public final class FileLogic {
    public static final int FOLDER = 0;
    public static final int IMAGE = 1;
    public static final int VIDEO = 2;
    public static final int AUDIO = 3;
    public static final int TEXT = 4;
    public static final int ARCHIVE = 5;
    public static final int APP = 6;
    public static final int DOCUMENT = 7;

    public static final int SORT_NAME = 0;
    public static final int SORT_DATE = 1;
    public static final int SORT_SIZE = 2;
    public static final int SORT_KIND = 3;

    private FileLogic() {
    }

    /** One line of a folder listing, read once off the main thread. */
    public static final class Entry {
        public final File file;
        public final String name;
        public final boolean folder;
        public final long size;
        public final long modified;
        public final int kind;

        public Entry(File file, String name, boolean folder, long size, long modified) {
            this.file = file;
            this.name = name;
            this.folder = folder;
            this.size = size;
            this.modified = modified;
            this.kind = folder ? FOLDER : kindOf(name);
        }

        public static Entry of(File file) {
            boolean folder = file.isDirectory();
            return new Entry(file, file.getName(), folder, folder ? 0L : file.length(),
                    file.lastModified());
        }
    }

    public static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 || dot == name.length() - 1 ? ""
                : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static int kindOf(String name) {
        switch (extensionOf(name)) {
            case "jpg": case "jpeg": case "png": case "gif": case "webp": case "bmp": case "heic":
                return IMAGE;
            case "mp4": case "mkv": case "avi": case "mov": case "webm": case "m4v": case "ts":
            case "mpg": case "mpeg": case "wmv": case "flv": case "3gp":
                return VIDEO;
            case "mp3": case "m4a": case "aac": case "flac": case "wav": case "ogg": case "opus":
            case "wma":
                return AUDIO;
            case "txt": case "md": case "log": case "json": case "xml": case "csv": case "srt":
            case "ini": case "conf": case "html": case "htm":
                return TEXT;
            case "zip": case "rar": case "7z": case "tar": case "gz": case "bz2": case "xz":
                return ARCHIVE;
            case "apk": case "apks": case "xapk":
                return APP;
            default:
                return DOCUMENT;
        }
    }

    /** The word Finder's Kind column would use. */
    public static String kindName(int kind) {
        switch (kind) {
            case FOLDER:
                return "Folder";
            case IMAGE:
                return "Image";
            case VIDEO:
                return "Video";
            case AUDIO:
                return "Audio";
            case TEXT:
                return "Text";
            case ARCHIVE:
                return "Archive";
            case APP:
                return "Android App";
            default:
                return "Document";
        }
    }

    public static String mimeOf(String name) {
        String ext = extensionOf(name);
        switch (kindOf(name)) {
            case IMAGE:
                return "jpg".equals(ext) ? "image/jpeg" : "image/" + ext;
            case VIDEO:
                switch (ext) {
                    case "mkv":
                        return "video/x-matroska";
                    case "mov":
                        return "video/quicktime";
                    case "avi":
                        return "video/x-msvideo";
                    case "ts":
                        return "video/mp2t";
                    case "m4v":
                        return "video/x-m4v";
                    case "3gp":
                        return "video/3gpp";
                    case "mpg":
                        return "video/mpeg";
                    case "wmv":
                        return "video/x-ms-wmv";
                    case "flv":
                        return "video/x-flv";
                    default:
                        return "video/" + ext;
                }
            case AUDIO:
                switch (ext) {
                    case "mp3":
                        return "audio/mpeg";
                    case "m4a":
                        return "audio/mp4";
                    case "wma":
                        return "audio/x-ms-wma";
                    case "opus":
                        return "audio/ogg";
                    default:
                        return "audio/" + ext;
                }
            case TEXT:
                return "html".equals(ext) || "htm".equals(ext) ? "text/html" : "text/plain";
            case APP:
                return "application/vnd.android.package-archive";
            default:
                return "pdf".equals(ext) ? "application/pdf" : "application/octet-stream";
        }
    }

    /** Sizes the way Apple writes them: decimal units, whole bytes under a kilobyte. */
    public static String formatSize(long bytes) {
        if (bytes < 1000L) {
            return bytes == 1L ? "1 byte" : bytes + " bytes";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        do {
            value /= 1000d;
            unit++;
        } while (value >= 999.95d && unit < units.length - 1);
        return unit == 0 ? Math.round(value) + " KB"
                : String.format(Locale.US, "%.1f %s", value, units[unit]);
    }

    /** "3 items", with the one case English treats differently. */
    public static String itemCount(int count) {
        return count == 1 ? "1 item" : count + " items";
    }

    /** Compares names as people read them, so "Part 2" comes before "Part 10". */
    public static int compareNatural(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int startA = i;
                int startB = j;
                while (i < a.length() && a.charAt(i) == '0') {
                    i++;
                }
                while (j < b.length() && b.charAt(j) == '0') {
                    j++;
                }
                int digitsA = i;
                int digitsB = j;
                while (digitsA < a.length() && Character.isDigit(a.charAt(digitsA))) {
                    digitsA++;
                }
                while (digitsB < b.length() && Character.isDigit(b.charAt(digitsB))) {
                    digitsB++;
                }
                int lengthA = digitsA - i;
                int lengthB = digitsB - j;
                if (lengthA != lengthB) {
                    return lengthA - lengthB;
                }
                for (int k = 0; k < lengthA; k++) {
                    int difference = a.charAt(i + k) - b.charAt(j + k);
                    if (difference != 0) {
                        return difference;
                    }
                }
                i = digitsA;
                j = digitsB;
                if (i - startA != j - startB && i >= a.length() && j >= b.length()) {
                    return (i - startA) - (j - startB);
                }
                continue;
            }
            int difference = Character.toLowerCase(ca) - Character.toLowerCase(cb);
            if (difference != 0) {
                return difference;
            }
            i++;
            j++;
        }
        return (a.length() - i) - (b.length() - j);
    }

    /** A new, sorted list. Sorting a huge folder is why this runs off the main thread. */
    public static List<Entry> sorted(List<Entry> entries, int mode, boolean showHidden) {
        List<Entry> out = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            if (showHidden || !entry.name.startsWith(".")) {
                out.add(entry);
            }
        }
        final Comparator<Entry> byName = (x, y) -> compareNatural(x.name, y.name);
        Comparator<Entry> order;
        switch (mode) {
            case SORT_DATE:
                order = (x, y) -> Long.compare(y.modified, x.modified);
                break;
            case SORT_SIZE:
                // Folders have no size of their own, so they gather at the end.
                order = (x, y) -> Long.compare(y.size, x.size);
                break;
            case SORT_KIND:
                order = (x, y) -> x.kind - y.kind;
                break;
            default:
                order = byName;
                break;
        }
        final Comparator<Entry> primary = order;
        Collections.sort(out, (x, y) -> {
            int result = primary.compare(x, y);
            return result != 0 ? result : byName.compare(x, y);
        });
        return out;
    }

    /** Whether something can be called this. Null when it can, the reason when not. */
    public static String nameProblem(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "A name cannot be empty.";
        }
        if (name.equals(".") || name.equals("..")) {
            return "That name is reserved by the system.";
        }
        if (name.contains("/")) {
            return "A name cannot contain a slash.";
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return "A name cannot contain control characters.";
            }
            // A USB stick is usually FAT or exFAT, where none of these may appear.
            if ("\\?*:\"<>|".indexOf(c) >= 0) {
                return "A name cannot contain “" + c + "”.";
            }
        }
        if (name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 255) {
            return "That name is too long.";
        }
        // FAT drops a trailing dot or space, so the file could not be found back.
        if (name.endsWith(".") || name.endsWith(" ")) {
            return "A name cannot end with a dot or a space.";
        }
        // On FAT these belong to old devices, whatever the extension is. DOS
        // drops the stem's trailing dots and spaces before checking, so
        // "con .txt" names the device just the same as "con.txt" does.
        int dot = name.indexOf('.');
        String stem = (dot < 0 ? name : name.substring(0, dot)).toUpperCase(Locale.ROOT);
        int end = stem.length();
        while (end > 0 && (stem.charAt(end - 1) == ' ' || stem.charAt(end - 1) == '.')) {
            end--;
        }
        stem = stem.substring(0, end);
        if (stem.equals("CON") || stem.equals("PRN") || stem.equals("AUX")
                || stem.equals("NUL") || stem.equals("CONIN$") || stem.equals("CONOUT$")
                || (stem.length() == 4
                && (stem.startsWith("COM") || stem.startsWith("LPT"))
                && stem.charAt(3) > '0' && stem.charAt(3) <= '9')) {
            return "That name is reserved by the system.";
        }
        return null;
    }

    /** True when {@code child} is {@code parent} or anything inside it. */
    public static boolean isInside(String parent, String child) {
        String base = parent.endsWith("/") ? parent : parent + "/";
        return child.equals(parent) || child.startsWith(base);
    }

    /**
     * Whether this path is a link to somewhere else. lstat is asked first: a
     * canonical path cannot see a link whose target has died. Where nothing
     * can answer, it errs on "link" — no caller's safe move is to walk
     * through it.
     */
    public static boolean isLink(File file) {
        try {
            android.system.StructStat stat =
                    android.system.Os.lstat(file.getAbsolutePath());
            if (stat != null) {
                return android.system.OsConstants.S_ISLNK(stat.st_mode);
            }
        } catch (Exception | LinkageError e) {
            // Fall through to the other ways of asking.
        }
        try {
            // isSymbolicLink swallows errors as "no", which would say a file it
            // could not inspect is safe to walk; readAttributes throws instead.
            return java.nio.file.Files.readAttributes(file.toPath(),
                    java.nio.file.attribute.BasicFileAttributes.class,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS).isSymbolicLink();
        } catch (Exception | LinkageError e) {
            try {
                File parent = file.getParentFile();
                File resolved = parent == null ? file
                        : new File(parent.getCanonicalFile(), file.getName());
                return !resolved.getCanonicalFile().equals(resolved.getAbsoluteFile());
            } catch (IOException ex) {
                return true;
            }
        }
    }

    /** Whether the name is held: something is there, or a dangling link is. */
    static boolean taken(File file) {
        return file.exists() || isLink(file);
    }

    /**
     * A name free in this folder, in Finder's form: "a copy.txt", then "a copy 2.txt".
     *
     * @param copy false to keep the name itself when it is free, as a move does
     */
    public static String freeName(String name, File folder, boolean copy) {
        if (!copy && !taken(new File(folder, name))) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        boolean hasExtension = dot > 0 && dot < name.length() - 1;
        String stem = hasExtension ? name.substring(0, dot) : name;
        String extension = hasExtension ? name.substring(dot) : "";
        String candidate = stem + " copy" + extension;
        // A drive that cannot answer "is the name taken" says taken to every
        // try, so the asking stops somewhere: whoever creates re-checks the
        // name for real, and a taken answer fails honestly there instead.
        for (int n = 2; n < 1000 && taken(new File(folder, candidate)); n++) {
            candidate = stem + " copy " + n + extension;
        }
        return candidate;
    }

    /** The folders from a location down to here, for the path bar. */
    public static List<File> crumbs(File root, File current) {
        List<File> out = new ArrayList<>();
        File walk = current;
        while (walk != null) {
            out.add(0, walk);
            if (walk.equals(root)) {
                return out;
            }
            walk = walk.getParentFile();
        }
        // Not under this location after all, so the path bar shows only the folder.
        out.clear();
        out.add(current);
        return out;
    }
}
