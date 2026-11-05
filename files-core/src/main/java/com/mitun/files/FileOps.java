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
        if (cancel.get() || isLink(file)) {
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
            boolean sameFolder = target.equals(source.getParentFile());
            if (move && sameFolder) {
                // Already here. Moving it onto itself is nothing, not a failure.
                result.done++;
                continue;
            }
            File destination = new File(target,
                    FileLogic.freeName(source.getName(), target, sameFolder));
            boolean ok = move ? moveTo(source, destination, cancel, done, total, progress, result)
                    : copyTo(source, destination, cancel, done, total, progress, result);
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
        if (source.renameTo(destination)) {
            return true;
        }
        // Across drives a rename cannot work, so it is a copy followed by a delete.
        // The source goes only once the whole copy has landed.
        if (!copyTo(source, destination, cancel, done, total, progress, result)) {
            return false;
        }
        if (!deleteTree(source, cancel)) {
            result.fail("Copied " + source.getName() + ", but could not remove the original.");
            return false;
        }
        return true;
    }

    private static boolean copyTo(File source, File destination, AtomicBoolean cancel, long[] done,
                                  long total, Progress progress, Result result) {
        if (cancel.get()) {
            return false;
        }
        if (isLink(source)) {
            result.fail(source.getName() + " is a link and was skipped.");
            return false;
        }
        if (source.isDirectory()) {
            if (!destination.mkdirs()) {
                result.fail("Could not create " + destination.getName() + " here.");
                return false;
            }
            File[] children = source.listFiles();
            if (children == null) {
                result.fail("Could not read " + source.getName() + ".");
                return false;
            }
            boolean all = true;
            for (File child : children) {
                all &= copyTo(child, new File(destination, child.getName()), cancel, done, total,
                        progress, result);
            }
            return all;
        }
        boolean finished = false;
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[BUFFER];
            int read;
            while ((read = in.read(buffer)) > 0) {
                if (cancel.get()) {
                    return false;
                }
                out.write(buffer, 0, read);
                done[0] += read;
                if (progress != null) {
                    progress.onProgress(done[0], total, source.getName());
                }
            }
            out.flush();
            // Checked before a move takes the original away.
            if (destination.length() != source.length()) {
                result.fail(source.getName() + " did not copy completely.");
                return false;
            }
            finished = true;
            return true;
        } catch (IOException | SecurityException e) {
            result.fail("Could not copy " + source.getName() + ".");
            return false;
        } finally {
            // A half-written file looks like a whole one later.
            if (!finished) {
                destination.delete();
            }
        }
    }

    /** Deletes these files and everything under them. */
    public static Result delete(List<File> files, AtomicBoolean cancel) {
        Result result = new Result();
        for (File file : files) {
            if (cancel.get()) {
                result.cancelled = true;
                return result;
            }
            if (deleteTree(file, cancel)) {
                result.done++;
            } else if (!cancel.get()) {
                result.fail("Could not delete " + file.getName() + ".");
            }
        }
        return result;
    }

    private static boolean deleteTree(File file, AtomicBoolean cancel) {
        if (cancel.get()) {
            return false;
        }
        // A link goes on its own. What it points to belongs to somewhere else.
        if (file.isDirectory() && !isLink(file)) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteTree(child, cancel)) {
                        return false;
                    }
                }
            }
        }
        // Gone already is what was asked for. A broken link answers false to exists
        // but still deletes, so the delete has to be tried first.
        return file.delete() || !file.exists();
    }

    private static String path(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

    private static boolean isLink(File file) {
        try {
            File parent = file.getParentFile();
            File resolved = parent == null ? file : new File(parent.getCanonicalFile(), file.getName());
            return !resolved.getCanonicalFile().equals(resolved.getAbsoluteFile());
        } catch (IOException e) {
            return false;
        }
    }
}
