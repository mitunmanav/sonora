package com.mitun.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FileLogicTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static FileLogic.Entry file(String name, long size, long modified) {
        return new FileLogic.Entry(new File(name), name, false, size, modified);
    }

    private static FileLogic.Entry folder(String name) {
        return new FileLogic.Entry(new File(name), name, true, 0, 0);
    }

    private static List<String> names(List<FileLogic.Entry> entries) {
        List<String> out = new ArrayList<>();
        for (FileLogic.Entry entry : entries) {
            out.add(entry.name);
        }
        return out;
    }

    @Test
    public void kindsFollowTheExtensionWhateverItsCase() {
        assertEquals(FileLogic.VIDEO, FileLogic.kindOf("Movie.MKV"));
        assertEquals(FileLogic.IMAGE, FileLogic.kindOf("a.jpeg"));
        assertEquals(FileLogic.APP, FileLogic.kindOf("vlc.apk"));
        assertEquals(FileLogic.DOCUMENT, FileLogic.kindOf("noextension"));
        assertEquals(FileLogic.DOCUMENT, FileLogic.kindOf(".hidden"));
        assertEquals(FileLogic.DOCUMENT, FileLogic.kindOf("trailing."));
    }

    @Test
    public void mimeTypesAreTheRealOnes() {
        assertEquals("video/x-matroska", FileLogic.mimeOf("a.mkv"));
        assertEquals("image/jpeg", FileLogic.mimeOf("a.jpg"));
        assertEquals("audio/mpeg", FileLogic.mimeOf("a.mp3"));
        assertEquals("application/vnd.android.package-archive", FileLogic.mimeOf("a.apk"));
        assertEquals("application/octet-stream", FileLogic.mimeOf("a.bin"));
    }

    @Test
    public void sizesAreWrittenTheAppleWay() {
        assertEquals("0 bytes", FileLogic.formatSize(0));
        assertEquals("1 byte", FileLogic.formatSize(1));
        assertEquals("999 bytes", FileLogic.formatSize(999));
        assertEquals("1 KB", FileLogic.formatSize(1000));
        assertEquals("1.5 MB", FileLogic.formatSize(1_500_000));
        assertEquals("2.0 GB", FileLogic.formatSize(2_000_000_000L));
    }

    @Test
    public void aNumberIsReadAsANumber() {
        assertTrue(FileLogic.compareNatural("Part 2", "Part 10") < 0);
        assertTrue(FileLogic.compareNatural("a", "B") < 0);
        assertEquals(0, FileLogic.compareNatural("Same", "same"));
        assertTrue(FileLogic.compareNatural("a", "ab") < 0);
        assertTrue(FileLogic.compareNatural("7", "007") != 0);
    }

    @Test
    public void sortingByNameIsNatural() {
        List<FileLogic.Entry> list = Arrays.asList(file("e10.mkv", 1, 1), file("e2.mkv", 1, 1));
        assertEquals(Arrays.asList("e2.mkv", "e10.mkv"),
                names(FileLogic.sorted(list, FileLogic.SORT_NAME, false)));
    }

    @Test
    public void sortingByDateAndSizePutsTheLargestFirst() {
        List<FileLogic.Entry> list = Arrays.asList(file("old", 5, 1), file("new", 1, 9));
        assertEquals(Arrays.asList("new", "old"),
                names(FileLogic.sorted(list, FileLogic.SORT_DATE, false)));
        assertEquals(Arrays.asList("old", "new"),
                names(FileLogic.sorted(list, FileLogic.SORT_SIZE, false)));
    }

    @Test
    public void sortingByKindGroupsFoldersFirst() {
        List<FileLogic.Entry> list = Arrays.asList(file("a.mp3", 1, 1), folder("z"));
        assertEquals(Arrays.asList("z", "a.mp3"),
                names(FileLogic.sorted(list, FileLogic.SORT_KIND, false)));
    }

    @Test
    public void hiddenFilesStayOutUntilAskedFor() {
        List<FileLogic.Entry> list = Arrays.asList(file(".nomedia", 1, 1), file("a", 1, 1));
        assertEquals(1, FileLogic.sorted(list, FileLogic.SORT_NAME, false).size());
        assertEquals(2, FileLogic.sorted(list, FileLogic.SORT_NAME, true).size());
    }

    @Test
    public void badNamesAreRefusedWithAReason() {
        assertNotNull(FileLogic.nameProblem(""));
        assertNotNull(FileLogic.nameProblem("  "));
        assertNotNull(FileLogic.nameProblem(".."));
        assertNotNull(FileLogic.nameProblem("a/b"));
        assertNotNull(FileLogic.nameProblem("x".repeat(256)));
        assertNull(FileLogic.nameProblem("Holiday 2026.mp4"));
    }

    @Test
    public void insideMeansInsideNotJustAPrefix() {
        assertTrue(FileLogic.isInside("/a/b", "/a/b"));
        assertTrue(FileLogic.isInside("/a/b", "/a/b/c"));
        assertFalse(FileLogic.isInside("/a/b", "/a/bc"));
    }

    @Test
    public void aCopyIsNamedTheWayFinderNamesIt() throws IOException {
        File dir = temp.newFolder();
        assertTrue(new File(dir, "a.txt").createNewFile());
        assertEquals("a copy.txt", FileLogic.freeName("a.txt", dir, true));
        assertTrue(new File(dir, "a copy.txt").createNewFile());
        assertEquals("a copy 2.txt", FileLogic.freeName("a.txt", dir, true));
        assertEquals("Folder copy", FileLogic.freeName("Folder", dir, true));
    }

    @Test
    public void aMoveKeepsItsNameWhenItIsFree() throws IOException {
        File dir = temp.newFolder();
        assertEquals("a.txt", FileLogic.freeName("a.txt", dir, false));
        assertTrue(new File(dir, "a.txt").createNewFile());
        assertEquals("a copy.txt", FileLogic.freeName("a.txt", dir, false));
    }

    @Test
    public void crumbsRunFromTheLocationToTheFolder() {
        File root = new File("/s");
        assertEquals(Arrays.asList(root, new File("/s/a"), new File("/s/a/b")),
                FileLogic.crumbs(root, new File("/s/a/b")));
        assertEquals(Arrays.asList(new File("/other")), FileLogic.crumbs(root, new File("/other")));
    }
}
