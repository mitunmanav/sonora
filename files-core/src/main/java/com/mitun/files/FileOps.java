package com.mitun.files;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Copying, moving and deleting. It never overwrites, and it reports what failed
 * rather than stopping quietly. Call off the main thread.
 */
public final class FileOps {
    public interface Progress {
        void onProgress(long done, long total, String name);
    }

    /** What happened. */
    public static final class Result {
        public int done;
        public int failed;
        public boolean cancelled;
        /** The reason for the first failure, in words for the screen. */
        public String problem;

        void fail(String why) {
            failed++;
            if (problem == null) {
                problem = why;
            }
        }
    }

    private static final int BUFFER = 64 * 1024;

    private FileOps() {
    }

    /** The bytes under these files, for the progress bar and the free-space check. */
    public static long sizeOf(List<File> files, AtomicBoolean cancel) {
        long total = 0L;
        for (File file : files) {
            total += sizeOf(file, cancel);
        }
        return total;
    }

    private static long sizeOf(File file, AtomicBoolean cancel) {
        if (cancel.get() || FileLogic.isLink(file)) {
            return 0L;
        }
        if (!file.isDirectory()) {
            return file.length();
        }
        long total = 0L;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                total += sizeOf(child, cancel);
            }
        }
        return total;
    }

    /** Copies or moves these into a folder. */
    public static Result transfer(List<File> sources, File target, boolean move,
                                  AtomicBoolean cancel, Progress progress) {
        Result result = new Result();
        long total = sizeOf(sources, cancel);
        long free = target.getUsableSpace();
        // Moving within one drive takes no space, so only a copy is refused up front.
        if (!move && free > 0L && total > free) {
            result.fail("There is not enough free space. This needs "
                    + FileLogic.formatSize(total) + " and " + FileLogic.formatSize(free)
                    + " is available.");
            return result;
        }
        long[] done = {0L};
        for (File source : sources) {
            if (cancel.get()) {
                result.cancelled = true;
                return result;
            }
            if (!source.exists()) {
                result.fail(source.getName() + " is no longer there.");
                continue;
            }
            if (source.isDirectory() && FileLogic.isInside(path(source), path(target))) {
                result.fail("A folder cannot go inside itself.");
                continue;
            }
            File sourceParent = source.getParentFile();
            // Written two ways, the same folder still counts as the same.
            boolean sameFolder = sourceParent != null
                    && path(target).equals(path(sourceParent));
            if (move && sameFolder) {
                // Already here. Moving it onto itself is nothing, not a failure.
                result.done++;
                continue;
            }
            File destination = new File(target,
                    FileLogic.freeName(source.getName(), target, sameFolder));
            boolean ok = move ? moveTo(source, destination, cancel, done, total, progress, result)
                    : copyTo(source, destination, null, cancel, done, total, progress, result)
                            != null;
            if (ok) {
                result.done++;
            }
            if (cancel.get()) {
                result.cancelled = true;
                return result;
            }
        }
        return result;
    }

    private static boolean moveTo(File source, File destination, AtomicBoolean cancel, long[] done,
                                  long total, Progress progress, Result result) {
        // A rename would happily take a link or a pipe, so the guards a copy
        // applies are asked first.
        if (FileLogic.isLink(source)) {
            result.fail(source.getName() + " is a link and was skipped.");
            return false;
        }
        if (!source.isDirectory() && !source.isFile()) {
            result.fail(source.getName() + " is a special file and was skipped.");
            return false;
        }
        // A rename replaces whatever is there — even a dangling link, which
        // exists() cannot see — so the name must be free outright for the fast
        // path to run. A name taken since it was picked goes the copy way,
        // which gives it the next free one.
        if (!FileLogic.taken(destination) && source.renameTo(destination)) {
            return true;
        }
        // Across drives a rename cannot work, so it is a copy followed by a
        // delete — a copy in what it costs too, refused when there is no room.
        // This source's size is the ask, not the whole batch's: the earlier
        // files already paid for their share.
        File into = destination.getParentFile();
        long free = into == null ? 0L : into.getUsableSpace();
        long need = sizeOf(source, cancel);
        if (free > 0L && need > free) {
            result.fail("There is not enough free space. This needs "
                    + FileLogic.formatSize(need) + " and " + FileLogic.formatSize(free)
                    + " is available.");
            return false;
        }
        // The source goes only once the whole copy has landed, and only the
        // folder it actually landed in — its name could have moved since the
        // first pick — is proof of what to delete.
        File landed = copyTo(source, destination, null, cancel, done, total, progress, result);
        if (landed == null) {
            return false;
        }
        if (!deleteCopied(source, landed, cancel)) {
            result.fail("Copied " + source.getName() + ", but could not remove the original.");
            return false;
        }
        return true;
    }

    /**
     * The guard is the copy's root folder, set by its top level: wherever the
     * drive lets that tree be reached a second way, it is the one folder the
     * copy must never recurse into, however deep in the source it shows up.
     *
     * @return the file the copy landed as — its name can move after the pick —
     *         or null when it did not land
     */
    private static File copyTo(File source, File destination, File guard,
                               AtomicBoolean cancel, long[] done, long total,
                               Progress progress, Result result) {
        if (cancel.get()) {
            return null;
        }
        if (FileLogic.isLink(source)) {
            result.fail(source.getName() + " is a link and was skipped.");
            return null;
        }
        if (source.isDirectory()) {
            // A name taken since it was picked gets the next free one, as a
            // file does; anything else that stopped mkdirs is a real failure.
            // Only a folder this call made may be filled: a name left over
            // after the retries can still be a directory, and merging into —
            // then cleaning up — somebody else's tree is not an option.
            File into = destination;
            boolean made = false;
            try {
                made = into.mkdirs();
                for (int tries = 0; tries < 4 && !made && FileLogic.taken(into); tries++) {
                    into = new File(into.getParentFile(),
                            FileLogic.freeName(source.getName(), into.getParentFile(), true));
                    made = into.mkdirs();
                }
            } catch (SecurityException e) {
                result.fail("Could not create " + into.getName() + " here.");
                return null;
            }
            if (!made) {
                result.fail("Could not create " + into.getName() + " here.");
                return null;
            }
            File[] children;
            try {
                children = source.listFiles();
            } catch (SecurityException e) {
                children = null;
            }
            if (children == null) {
                // mkdirs made the folder, so an aborted copy removes it again —
                // even when the stop was asked for, which is why it never cancels.
                deleteTree(into, new AtomicBoolean(false), null, 0, 0);
                result.fail("Could not read " + source.getName() + ".");
                return null;
            }
            File root = guard == null ? into : guard;
            for (File child : children) {
                // A drive that answers to two paths can put the destination
                // inside the source itself; copying it into itself never ends.
                if (sameInode(child, root)) {
                    continue;
                }
                if (copyTo(child, new File(into, child.getName()), root, cancel, done, total,
                        progress, result) == null) {
                    deleteTree(into, new AtomicBoolean(false), null, 0, 0);
                    return null;
                }
            }
            // The folder's own date goes last: writing the children changed it.
            try {
                into.setLastModified(source.lastModified());
            } catch (SecurityException ignored) {
                // Still a copy, just stamped with today.
            }
            return into;
        }
        if (!source.exists()) {
            result.fail(source.getName() + " is no longer there.");
            return null;
        }
        // A pipe or a socket would hold the open forever, out of cancel's reach.
        if (!source.isFile()) {
            result.fail(source.getName() + " is a special file and was skipped.");
            return null;
        }
        // The empty file is made atomically first: a name taken since it was
        // picked gets the next free one rather than emptying whatever took it.
        File into = destination;
        for (int tries = 0; tries < 4; tries++) {
            boolean ours;
            try {
                ours = into.createNewFile();
            } catch (IOException | SecurityException e) {
                result.fail("Could not copy " + source.getName() + ".");
                return null;
            }
            if (!ours) {
                into = new File(into.getParentFile(),
                        FileLogic.freeName(source.getName(), into.getParentFile(), true));
                continue;
            }
            boolean finished = false;
            try (InputStream in = new FileInputStream(source);
                 OutputStream out = new FileOutputStream(into)) {
                byte[] buffer = new byte[BUFFER];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    if (cancel.get()) {
                        return null;
                    }
                    out.write(buffer, 0, read);
                    done[0] += read;
                    if (progress != null) {
                        progress.onProgress(done[0], total, source.getName());
                    }
                }
                out.flush();
                // Checked before a move takes the original away.
                if (into.length() != source.length()) {
                    result.fail(source.getName() + " did not copy completely.");
                    return null;
                }
                finished = true;
                // A copy keeps when the original was touched, for the Date column.
                try {
                    into.setLastModified(source.lastModified());
                } catch (SecurityException ignored) {
                    // Still a copy, just stamped with today.
                }
                return into;
            } catch (IOException | SecurityException e) {
                result.fail("Could not copy " + source.getName() + ".");
                return null;
            } finally {
                // A half-written file looks like a whole one later.
                if (!finished) {
                    into.delete();
                }
            }
        }
        result.fail("Could not copy " + source.getName() + ".");
        return null;
    }

    /** Deletes these files and everything under them. */
    public static Result delete(List<File> files, AtomicBoolean cancel, Progress progress) {
        Result result = new Result();
        int at = 0;
        for (File file : files) {
            if (cancel.get()) {
                result.cancelled = true;
                return result;
            }
            // Bytes are not counted for a delete; the items themselves are.
            if (progress != null) {
                progress.onProgress(at, files.size(), file.getName());
            }
            if (deleteTree(file, cancel, progress, at, files.size())) {
                result.done++;
            } else if (!cancel.get()) {
                result.fail("Could not delete " + file.getName() + ".");
            }
            at++;
        }
        // A stop asked for during the last tree falls out of the loop with
        // nothing recorded, neither failed nor stopped.
        result.cancelled = result.cancelled || cancel.get();
        return result;
    }

    private static boolean deleteTree(File file, AtomicBoolean cancel, Progress progress,
            long done, long total) {
        if (cancel.get()) {
            return false;
        }
        // A link goes on its own. What it points to belongs to somewhere else.
        if (file.isDirectory() && !FileLogic.isLink(file)) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    // One big tree still tells the screen it is alive: the name
                    // moves while the bar keeps the top-level item's count.
                    if (progress != null) {
                        progress.onProgress(done, total, child.getName());
                    }
                    if (!deleteTree(child, cancel, progress, done, total)) {
                        return false;
                    }
                }
            }
        }
        // Gone already is what was asked for. A broken link answers false to exists
        // but still deletes, so the delete has to be tried first.
        return file.delete() || !file.exists();
    }

    /**
     * Removes the pieces of a copied tree whose copies landed, for a move across
     * drives. A file that arrived in the source after the copy, or one the copy
     * left out, stays where it is — and keeps the folder it sits in.
     */
    private static boolean deleteCopied(File source, File destination, AtomicBoolean cancel) {
        if (cancel.get()) {
            return false;
        }
        if (source.isDirectory() && !FileLogic.isLink(source)) {
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    File copied = new File(destination, child.getName());
                    // A file only answers for a file, a folder for a folder:
                    // a late arrival sharing a copied name's kind of slot
                    // stays.
                    if (!copied.exists()
                            || copied.isDirectory() != child.isDirectory()
                            || (!copied.isDirectory() && copied.length() != child.length())) {
                        continue;
                    }
                    if (!deleteCopied(child, copied, cancel)) {
                        return false;
                    }
                }
            }
        }
        return source.delete() || !source.exists();
    }

    /**
     * Whether two paths land on the same inode, however they were spelled.
     * lstat is asked on purpose: a link keeps its own inode, so it falls
     * through to the usual "links are not copied" answer rather than slipping
     * past it.
     */
    static boolean sameInode(File a, File b) {
        try {
            android.system.StructStat left = android.system.Os.lstat(a.getPath());
            android.system.StructStat right = android.system.Os.lstat(b.getPath());
            if (left != null && right != null) {
                return left.st_dev == right.st_dev && left.st_ino == right.st_ino;
            }
        } catch (Exception | LinkageError e) {
            // Fall through to the other way of asking.
        }
        try {
            Object left = java.nio.file.Files.readAttributes(a.toPath(),
                    java.nio.file.attribute.BasicFileAttributes.class,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS).fileKey();
            return left != null && left.equals(java.nio.file.Files.readAttributes(b.toPath(),
                    java.nio.file.attribute.BasicFileAttributes.class,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS).fileKey());
        } catch (Exception | LinkageError e) {
            // Where neither can answer, the lexical check in transfer still
            // catches the usual way a folder ends up inside itself.
            return false;
        }
    }

    private static String path(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }
}
