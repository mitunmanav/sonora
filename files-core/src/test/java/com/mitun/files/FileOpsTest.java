package com.mitun.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileOpsTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private final AtomicBoolean go = new AtomicBoolean(false);
    private File from;
    private File to;

    @Before
    public void folders() throws IOException {
        from = temp.newFolder("from");
        to = temp.newFolder("to");
    }

    private static File write(File dir, String name, String text) throws IOException {
        File file = new File(dir, name);
        Files.write(file.toPath(), text.getBytes());
        return file;
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()));
    }

    @Test
    public void aCopyLeavesTheOriginal() throws IOException {
        File a = write(from, "a.txt", "hello");
        FileOps.Result result = FileOps.transfer(Collections.singletonList(a), to, false, go, null);
        assertEquals(1, result.done);
        assertEquals(0, result.failed);
        assertEquals("hello", read(new File(to, "a.txt")));
        assertTrue(a.exists());
    }

    @Test
    public void aMoveTakesTheOriginalAway() throws IOException {
        File a = write(from, "a.txt", "hello");
        FileOps.transfer(Collections.singletonList(a), to, true, go, null);
        assertFalse(a.exists());
        assertEquals("hello", read(new File(to, "a.txt")));
    }

    @Test
    public void aCopyKeepsTheOriginalsDate() throws IOException {
        File a = write(from, "a.txt", "hello");
        long then = 1_234_567_000L;
        assertTrue(a.setLastModified(then));
        FileOps.transfer(Collections.singletonList(a), to, false, go, null);
        assertEquals(then, new File(to, "a.txt").lastModified());
    }

    @Test
    public void aCopiedFolderKeepsItsDateToo() throws IOException {
        File folder = new File(from, "album");
        assertTrue(folder.mkdir());
        File inner = write(folder, "1.jpg", "one");
        long fileThen = 1_234_560_000L;
        long dirThen = 1_234_561_000L;
        assertTrue(inner.setLastModified(fileThen));
        // The folder is stamped after its children exist, as the copy is too.
        assertTrue(folder.setLastModified(dirThen));
        FileOps.transfer(Collections.singletonList(folder), to, false, go, null);
        assertEquals(dirThen, new File(to, "album").lastModified());
        assertEquals(fileThen, new File(to, "album/1.jpg").lastModified());
    }

    @Test
    public void nothingIsEverOverwritten() throws IOException {
        File a = write(from, "a.txt", "new");
        write(to, "a.txt", "old");
        FileOps.transfer(Collections.singletonList(a), to, false, go, null);
        assertEquals("old", read(new File(to, "a.txt")));
        assertEquals("new", read(new File(to, "a copy.txt")));
    }

    @Test
    public void takenNamesKeepTheirFilesAndTheCopyLandsElsewhere() throws IOException {
        File a = write(from, "a.txt", "new");
        write(to, "a.txt", "old");
        write(to, "a copy.txt", "older copy");
        FileOps.Result result = FileOps.transfer(Collections.singletonList(a), to, false, go, null);
        assertEquals(1, result.done);
        assertEquals("old", read(new File(to, "a.txt")));
        assertEquals("older copy", read(new File(to, "a copy.txt")));
        assertEquals("new", read(new File(to, "a copy 2.txt")));
    }

    @Test(timeout = 10_000)
    public void aPipeIsSkippedRatherThanOpened() throws Exception {
        File pipe = new File(from, "pipe");
        org.junit.Assume.assumeTrue(0 == new ProcessBuilder("mkfifo", pipe.getAbsolutePath())
                .start().waitFor());
        FileOps.Result result = FileOps.transfer(Collections.singletonList(pipe), to, false, go, null);
        assertEquals(0, result.done);
        assertEquals(1, result.failed);
        assertNotNull(result.problem);
        assertFalse(new File(to, "pipe").exists());
    }

    @Test
    public void foldersCopyWithEverythingInThem() throws IOException {
        File folder = new File(from, "album");
        assertTrue(folder.mkdir());
        write(folder, "1.jpg", "one");
        File inner = new File(folder, "more");
        assertTrue(inner.mkdir());
        write(inner, "2.jpg", "two");
        FileOps.Result result = FileOps.transfer(Collections.singletonList(folder), to, false, go, null);
        assertEquals(1, result.done);
        assertEquals("two", read(new File(to, "album/more/2.jpg")));
    }

    @Test
    public void aFolderCannotGoInsideItself() throws IOException {
        File folder = new File(from, "album");
        File inner = new File(folder, "more");
        assertTrue(inner.mkdirs());
        write(folder, "1.jpg", "one");
        FileOps.Result result = FileOps.transfer(Collections.singletonList(folder), inner, true, go, null);
        assertEquals(1, result.failed);
        assertNotNull(result.problem);
        assertTrue(new File(folder, "1.jpg").exists());
    }

    @Test
    public void copyingIntoTheSameFolderMakesACopy() throws IOException {
        File a = write(from, "a.txt", "x");
        FileOps.transfer(Collections.singletonList(a), from, false, go, null);
        assertTrue(new File(from, "a copy.txt").exists());
    }

    @Test
    public void movingIntoTheSameFolderChangesNothing() throws IOException {
        File a = write(from, "a.txt", "x");
        FileOps.Result result = FileOps.transfer(Collections.singletonList(a), from, true, go, null);
        assertEquals(1, result.done);
        assertEquals(1, from.listFiles().length);
    }

    @Test
    public void aMoveThroughAnAliasStillCountsAsTheSameFolder() throws IOException {
        File a = write(from, "a.txt", "x");
        File alias = new File(temp.getRoot(), "alias");
        Files.createSymbolicLink(alias.toPath(), from.toPath());
        FileOps.Result result = FileOps.transfer(Collections.singletonList(a), alias, true, go, null);
        assertEquals(1, result.done);
        assertEquals(0, result.failed);
        assertTrue(new File(from, "a.txt").exists());
        assertFalse(new File(from, "a copy.txt").exists());
    }

    @Test
    public void aStoppedFolderCopyLeavesNothingBehind() throws IOException {
        File folder = new File(from, "album");
        assertTrue(folder.mkdir());
        write(folder, "a.txt", "x".repeat(300_000));
        AtomicBoolean stop = new AtomicBoolean(false);
        FileOps.Result result = FileOps.transfer(Collections.singletonList(folder), to, false, stop,
                (done, total, name) -> stop.set(true));
        assertTrue(result.cancelled);
        assertFalse(new File(to, "album").exists());
    }

    @Test
    public void aMoveLeavesWhatArrivedAfterTheCopy() throws IOException {
        // renameTo refuses, the way a different drive does, so the move is a
        // real copy and delete.
        File folder = new File(from, "album") {
            @Override
            public boolean renameTo(File dest) {
                return false;
            }
        };
        assertTrue(folder.mkdir());
        write(folder, "a.txt", "x".repeat(300_000));
        // A file that lands in the source while the copy runs is not the
        // copy's to take away.
        FileOps.Result result = FileOps.transfer(Collections.singletonList(folder), to, true,
                go, (done, total, name) -> {
                    try {
                        write(folder, "late.txt", "keep me");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        assertEquals("keep me", read(new File(folder, "late.txt")));
        assertFalse(new File(folder, "a.txt").exists());
        assertEquals("x".repeat(300_000), read(new File(to, "album/a.txt")));
        assertEquals(1, result.failed);
        assertNotNull(result.problem);
    }

    @Test
    public void aMoveCannotTakeADanglingLinksName() throws IOException {
        File a = write(from, "a.txt", "new");
        java.nio.file.Files.createSymbolicLink(new File(to, "a.txt").toPath(),
                new File(to, "missing").toPath());
        FileOps.Result result = FileOps.transfer(Collections.singletonList(a), to, true, go, null);
        assertEquals(1, result.done);
        assertEquals(0, result.failed);
        // The link keeps its name; the moved file lands on the next free one.
        assertTrue(java.nio.file.Files.isSymbolicLink(new File(to, "a.txt").toPath()));
        assertEquals("new", read(new File(to, "a copy.txt")));
        assertFalse(a.exists());
    }

    @Test
    public void aMissingFileIsReportedAndTheRestStillGo() throws IOException {
        File gone = new File(from, "gone.txt");
        File a = write(from, "a.txt", "x");
        FileOps.Result result = FileOps.transfer(Arrays.asList(gone, a), to, false, go, null);
        assertEquals(1, result.done);
        assertEquals(1, result.failed);
        assertTrue(new File(to, "a.txt").exists());
    }

    @Test
    public void stoppingLeavesNoHalfWrittenFile() throws IOException {
        File a = write(from, "a.txt", "x".repeat(300_000));
        AtomicBoolean stop = new AtomicBoolean(false);
        FileOps.Result result = FileOps.transfer(Collections.singletonList(a), to, false, stop,
                (done, total, name) -> stop.set(true));
        assertTrue(result.cancelled);
        assertFalse(new File(to, "a.txt").exists());
        assertTrue(a.exists());
    }

    @Test
    public void deletingRemovesAWholeTree() throws IOException {
        File folder = new File(from, "album");
        assertTrue(folder.mkdir());
        write(folder, "1.jpg", "one");
        FileOps.Result result = FileOps.delete(Collections.singletonList(folder), go, null);
        assertEquals(1, result.done);
        assertFalse(folder.exists());
    }

    @Test
    public void deletingALinkLeavesWhatItPointsTo() throws IOException {
        File target = temp.newFolder("keep");
        write(target, "precious.txt", "x");
        File link = new File(from, "link");
        Files.createSymbolicLink(link.toPath(), target.toPath());
        FileOps.delete(Collections.singletonList(link), go, null);
        assertFalse(link.exists());
        assertTrue(new File(target, "precious.txt").exists());
    }

    @Test
    public void aFolderWithABrokenLinkStillDeletes() throws IOException {
        File folder = new File(from, "album");
        assertTrue(folder.mkdir());
        Files.createSymbolicLink(new File(folder, "broken").toPath(),
                new File(from, "missing").toPath());
        assertEquals(1, FileOps.delete(Collections.singletonList(folder), go, null).done);
        assertFalse(folder.exists());
    }

    @Test
    public void somethingAlreadyGoneCountsAsDeleted() {
        FileOps.Result result = FileOps.delete(
                Collections.singletonList(new File(from, "never-there")), go, null);
        assertEquals(1, result.done);
        assertEquals(0, result.failed);
    }

    @Test
    public void twoPathsToTheSamePlaceCountAsOne() throws IOException {
        File target = temp.newFolder("real");
        File link = new File(temp.getRoot(), "alias");
        Files.createSymbolicLink(link.toPath(), target.toPath());
        assertTrue(Places.samePlace(link, target));
        assertFalse(Places.samePlace(target, from));
    }

    @Test
    public void sameInodeKnowsAFileFromALink() throws IOException {
        File a = write(from, "a.txt", "x");
        File b = write(from, "b.txt", "y");
        File link = new File(from, "alias");
        Files.createSymbolicLink(link.toPath(), a.toPath());
        assertTrue(FileOps.sameInode(a, a));
        assertFalse(FileOps.sameInode(a, b));
        // A link keeps an inode of its own: it is never "the same file" as what
        // it points at, so a copy still answers "links are not copied" for it.
        assertFalse(FileOps.sameInode(link, a));
    }

    @Test
    public void sizesAddUpAcrossAFolder() throws IOException {
        File folder = new File(from, "album");
        assertTrue(folder.mkdir());
        write(folder, "a", "12345");
        write(from, "b", "123");
        assertEquals(8L, FileOps.sizeOf(Arrays.asList(folder, new File(from, "b")), go));
    }

    @Test
    public void aSizeAlreadyStoppedCountsNothing() {
        assertEquals(0L, FileOps.sizeOf(Collections.singletonList(from),
                new AtomicBoolean(true)));
    }

    @Test
    public void aCopyOntoAFileFailsHonestly() throws IOException {
        File a = write(from, "a.txt", "hello");
        File target = write(to, "not-a-folder", "x");
        FileOps.Result result = FileOps.transfer(Collections.singletonList(a), target,
                false, go, null);
        assertEquals(0, result.done);
        assertEquals(1, result.failed);
        assertNotNull(result.problem);
        assertTrue(a.exists());
    }

    @Test
    public void aCopyRefusesAFolderItDidNotCreate() throws IOException {
        File folder = new File(from, "folder");
        assertTrue(folder.mkdir());
        write(folder, "a.txt", "hello");
        // Every name freeName can offer is a real folder already; the last one
        // holds somebody else's work that a merge must never touch.
        assertTrue(new File(to, "folder").mkdir());
        assertTrue(new File(to, "folder copy").mkdir());
        for (int n = 2; n < 1000; n++) {
            assertTrue(new File(to, "folder copy " + n).mkdir());
        }
        File mine = write(new File(to, "folder copy 999"), "mine.txt", "keep");
        FileOps.Result result = FileOps.transfer(Collections.singletonList(folder), to,
                false, go, null);
        assertEquals(0, result.done);
        assertEquals(1, result.failed);
        assertEquals("keep", read(mine));
        assertEquals("hello", read(new File(folder, "a.txt")));
    }
}
