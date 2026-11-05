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
    public void nothingIsEverOverwritten() throws IOException {
        File a = write(from, "a.txt", "new");
        write(to, "a.txt", "old");
        FileOps.transfer(Collections.singletonList(a), to, false, go, null);
        assertEquals("old", read(new File(to, "a.txt")));
        assertEquals("new", read(new File(to, "a copy.txt")));
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
        FileOps.Result result = FileOps.delete(Collections.singletonList(folder), go);
        assertEquals(1, result.done);
        assertFalse(folder.exists());
    }

    @Test
    public void deletingALinkLeavesWhatItPointsTo() throws IOException {
        File target = temp.newFolder("keep");
        write(target, "precious.txt", "x");
        File link = new File(from, "link");
        Files.createSymbolicLink(link.toPath(), target.toPath());
        FileOps.delete(Collections.singletonList(link), go);
        assertFalse(link.exists());
        assertTrue(new File(target, "precious.txt").exists());
    }

    @Test
    public void aFolderWithABrokenLinkStillDeletes() throws IOException {
        File folder = new File(from, "album");
        assertTrue(folder.mkdir());
        Files.createSymbolicLink(new File(folder, "broken").toPath(),
                new File(from, "missing").toPath());
        assertEquals(1, FileOps.delete(Collections.singletonList(folder), go).done);
        assertFalse(folder.exists());
    }

    @Test
    public void somethingAlreadyGoneCountsAsDeleted() {
        FileOps.Result result = FileOps.delete(
                Collections.singletonList(new File(from, "never-there")), go);
        assertEquals(1, result.done);
        assertEquals(0, result.failed);
    }

    @Test
    public void sizesAddUpAcrossAFolder() throws IOException {
        File folder = new File(from, "album");
        assertTrue(folder.mkdir());
        write(folder, "a", "12345");
        write(from, "b", "123");
        assertEquals(8L, FileOps.sizeOf(Arrays.asList(folder, new File(from, "b")), go));
    }
}
