package com.mitun.sonora;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.mitun.sonora.browser.BrowserActivity;
import com.mitun.sonora.tools.FilePanel;
import com.mitun.sonora.tools.FileServer;
import com.mitun.sonora.ui.ScreensaverView;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class EdgeCasesTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void screensaverWrapHandlesNegativeAndPositiveIndices() {
        assertEquals(0, ScreensaverView.wrap(0, 5));
        assertEquals(4, ScreensaverView.wrap(4, 5));
        assertEquals(0, ScreensaverView.wrap(5, 5));
        assertEquals(1, ScreensaverView.wrap(6, 5));
        assertEquals(4, ScreensaverView.wrap(-1, 5));
        assertEquals(3, ScreensaverView.wrap(-2, 5));
        assertEquals(4, ScreensaverView.wrap(-6, 5));
    }

    @Test
    public void browserNormaliseHandlesLocalHostAndIpAddresses() {
        assertEquals("http://localhost", BrowserActivity.normalise("localhost"));
        assertEquals("http://localhost:8080", BrowserActivity.normalise("localhost:8080"));
        assertEquals("http://192.168.1.100", BrowserActivity.normalise("192.168.1.100"));
        assertEquals("http://192.168.1.100:8000", BrowserActivity.normalise("192.168.1.100:8000"));
        assertEquals("http://[::1]:8080", BrowserActivity.normalise("[::1]:8080"));
        assertEquals("http://tv:8080", BrowserActivity.normalise("tv:8080"));
        assertEquals("https://google.com", BrowserActivity.normalise("google.com"));
        assertEquals("https://example.com/path", BrowserActivity.normalise("https://example.com/path"));
        assertTrue(BrowserActivity.normalise("hello world").startsWith("https://duckduckgo.com/?q="));
        assertTrue(BrowserActivity.normalise("searchterm").startsWith("https://duckduckgo.com/?q=searchterm"));
    }

    @Test
    public void filePanelIsSameContentDetectsTruncation() throws IOException {
        File a = temp.newFile("a.txt");
        File b = temp.newFile("b.txt");
        try (FileOutputStream outA = new FileOutputStream(a);
             FileOutputStream outB = new FileOutputStream(b)) {
            outA.write("Hello World".getBytes());
            outB.write("Hello".getBytes()); // truncated
        }
        assertFalse(FilePanel.isSameContent(a, b));

        File c = temp.newFile("c.txt");
        try (FileOutputStream outC = new FileOutputStream(c)) {
            outC.write("Hello World".getBytes());
        }
        assertTrue(FilePanel.isSameContent(a, c));
    }

    @Test
    public void fileServerCopyFileVerifiesSize() throws IOException {
        File src = temp.newFile("source.txt");
        try (FileOutputStream out = new FileOutputStream(src)) {
            out.write("Payload data for test".getBytes());
        }
        File dst = new File(temp.getRoot(), "dest.txt");
        assertTrue(FileServer.copyFile(src, dst));
        assertEquals(src.length(), dst.length());
        assertTrue(FilePanel.isSameContent(src, dst));
    }

    @Test
    public void filePanelDeleteCleansUpRecursively() throws IOException {
        File folder = temp.newFolder("subfolder");
        File child1 = new File(folder, "child1.txt");
        File child2 = new File(folder, "child2.txt");
        assertTrue(child1.createNewFile());
        assertTrue(child2.createNewFile());

        assertTrue(FilePanel.delete(folder));
        assertFalse(folder.exists());
        assertFalse(child1.exists());
        assertFalse(child2.exists());
    }

    @Test
    public void appEntrySettingsTileNeverLaunches() {
        AppEntry settings = new AppEntry("Settings", null, null, null, null, 0, true);
        assertFalse(settings.launch(null));
    }

    @Test
    public void filePanelDeleteHandlesNullOrMissingGracefully() {
        assertTrue(FilePanel.delete(null));
        assertTrue(FilePanel.delete(new File("/non/existent/path/for/test")));
    }

    @Test
    public void browserNormaliseHandlesEmptyAndWhitespace() {
        assertEquals("https://duckduckgo.com/", BrowserActivity.normalise(""));
        assertEquals("https://duckduckgo.com/", BrowserActivity.normalise("   "));
        assertEquals("https://duckduckgo.com/", BrowserActivity.normalise(null));
    }

    @Test
    public void filePanelDeleteRemovesDirectoryWithBrokenSymlink() throws IOException {
        File folder = temp.newFolder("folderWithBrokenLink");
        File brokenLink = new File(folder, "brokenLink");
        java.nio.file.Files.createSymbolicLink(brokenLink.toPath(),
                new File(temp.getRoot(), "missing_target").toPath());
        assertFalse(brokenLink.exists());

        assertTrue(FilePanel.delete(folder));
        assertFalse(folder.exists());
    }

    @Test
    public void filePanelIsSymlinkDetectsSymlinkAndRegularFile() throws IOException {
        File regularFolder = temp.newFolder("regular");
        File link = new File(temp.getRoot(), "linkToFolder");
        java.nio.file.Files.createSymbolicLink(link.toPath(), regularFolder.toPath());

        assertTrue(FilePanel.isSymlink(link));
        assertFalse(FilePanel.isSymlink(regularFolder));
        assertFalse(FilePanel.isSymlink(null));
    }

    @Test
    public void fileServerCopyFilePreventsInfiniteRecursionIntoSubfolder() throws IOException {
        File parent = temp.newFolder("parentDir");
        File child = new File(parent, "childDir");
        assertFalse(FileServer.copyFile(parent, child));
        assertFalse(FileServer.copyFile(parent, parent));
    }

    @Test
    public void sonoraProviderFileOfRejectsNullContext() {
        org.junit.Assert.assertNull(SonoraProvider.fileOf(null, null));
        assertFalse(SonoraProvider.isAllowed(null, new File("/etc/passwd")));
        assertFalse(SonoraProvider.isAllowed(null, null));
    }

    @Test
    public void sonoraProviderOpenFileRejectsWriteMode() throws Exception {
        SonoraProvider provider = new SonoraProvider();
        try {
            provider.openFile(null, "w");
            org.junit.Assert.fail("Expected SecurityException");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("read-only"));
        }

        try {
            provider.openFile(null, "rw");
            org.junit.Assert.fail("Expected SecurityException");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("read-only"));
        }
    }
}
