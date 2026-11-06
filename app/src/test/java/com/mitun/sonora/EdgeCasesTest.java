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
    public void browserHttpStaysOnTheLocalNetwork() {
        assertEquals("http://192.168.1.100:8000/share", BrowserActivity.localOrHttps(
                "http://192.168.1.100:8000/share"));
        assertEquals("http://10.0.0.5", BrowserActivity.localOrHttps("http://10.0.0.5"));
        assertEquals("http://172.16.0.9", BrowserActivity.localOrHttps("http://172.16.0.9"));
        assertEquals("http://172.31.255.9", BrowserActivity.localOrHttps("http://172.31.255.9"));
        assertEquals("http://localhost:8080", BrowserActivity.localOrHttps("http://localhost:8080"));
        assertEquals("http://tv:8080", BrowserActivity.localOrHttps("http://tv:8080"));
        assertEquals("http://printer.local", BrowserActivity.localOrHttps("http://printer.local"));
        assertEquals("http://[fe80::1]:8080", BrowserActivity.localOrHttps("http://[fe80::1]:8080"));
        assertEquals("http://[::1]", BrowserActivity.localOrHttps("http://[::1]"));
        assertEquals("https://example.com", BrowserActivity.localOrHttps("http://example.com"));
        assertEquals("https://172.32.0.9", BrowserActivity.localOrHttps("http://172.32.0.9"));
        assertEquals("https://192.169.0.9", BrowserActivity.localOrHttps("http://192.169.0.9"));
        assertEquals("https://example.com", BrowserActivity.localOrHttps("https://example.com"));
    }

    @Test
    public void browserHttpDoesNotTrustLookalikeHosts() {
        // A name that merely starts like a private address is not one.
        assertEquals("https://10.evil.com", BrowserActivity.localOrHttps("http://10.evil.com"));
        assertEquals("https://192.168.attacker.com",
                BrowserActivity.localOrHttps("http://192.168.attacker.com"));
        assertEquals("https://127.foo.com", BrowserActivity.localOrHttps("http://127.foo.com"));
        assertEquals("https://172.16.bad.com",
                BrowserActivity.localOrHttps("http://172.16.bad.com"));
        assertEquals("https://169.254.example.org",
                BrowserActivity.localOrHttps("http://169.254.example.org"));
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
    public void browserNormaliseHandlesColonForms() {
        // about: loads as it is; a bare IPv6 or a scheme-looking word with
        // no host goes to the search instead of a garbage http: address.
        assertEquals("about:blank", BrowserActivity.normalise("about:blank"));
        assertTrue(BrowserActivity.normalise("::1").startsWith("https://duckduckgo.com/?q="));
        assertTrue(BrowserActivity.normalise("foo:").startsWith("https://duckduckgo.com/?q="));
        assertTrue(BrowserActivity.normalise("foo:bar").startsWith("https://duckduckgo.com/?q="));
        assertEquals("http://tv:8080", BrowserActivity.normalise("tv:8080"));
    }

    @Test
    public void fileServerCopyFileOntoItselfRefuses() throws IOException {
        File src = temp.newFile("self.txt");
        try (FileOutputStream out = new FileOutputStream(src)) {
            out.write("keep me".getBytes());
        }
        assertFalse(FileServer.copyFile(src, src));
        assertEquals("keep me",
                new String(java.nio.file.Files.readAllBytes(src.toPath())));
    }

    @Test
    public void fileServerCopyFilePreventsInfiniteRecursionIntoSubfolder() throws IOException {
        File parent = temp.newFolder("parentDir");
        File child = new File(parent, "childDir");
        assertFalse(FileServer.copyFile(parent, child));
        assertFalse(FileServer.copyFile(parent, parent));
    }
}
