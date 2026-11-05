package com.mitun.sonora;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.mitun.sonora.browser.BrowserActivity;
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
    public void fileServerCopyFileVerifiesSize() throws IOException {
        File src = temp.newFile("source.txt");
        try (FileOutputStream out = new FileOutputStream(src)) {
            out.write("Payload data for test".getBytes());
        }
        File dst = new File(temp.getRoot(), "dest.txt");
        assertTrue(FileServer.copyFile(src, dst));
        assertEquals(src.length(), dst.length());
        assertEquals(new String(java.nio.file.Files.readAllBytes(src.toPath())),
                new String(java.nio.file.Files.readAllBytes(dst.toPath())));
    }

    @Test
    public void appEntrySettingsTileNeverLaunches() {
        AppEntry settings = new AppEntry("Settings", null, null, null, null, 0, true);
        assertFalse(settings.launch(null));
    }

    @Test
    public void browserNormaliseHandlesEmptyAndWhitespace() {
        assertEquals("https://duckduckgo.com/", BrowserActivity.normalise(""));
        assertEquals("https://duckduckgo.com/", BrowserActivity.normalise("   "));
        assertEquals("https://duckduckgo.com/", BrowserActivity.normalise(null));
    }

    @Test
    public void fileServerCopyFilePreventsInfiniteRecursionIntoSubfolder() throws IOException {
        File parent = temp.newFolder("parentDir");
        File child = new File(parent, "childDir");
        assertFalse(FileServer.copyFile(parent, child));
        assertFalse(FileServer.copyFile(parent, parent));
    }
}
