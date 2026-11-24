package com.mitun.sonora.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The request the little file server has to survive.
 *
 * Anything on the network can send anything, so the parsing is the part that
 * has to hold up: a line of nothing but spaces, a broken escape and a line
 * that never ends.
 */
public class FileServerTest {

    @Test
    public void aRequestAsksForItsPath() {
        assertEquals("/photos", FileServer.requestPath("GET /photos HTTP/1.1".split(" ")));
    }

    @Test
    public void aMalformedLineIsNotARequest() {
        // A verb with no path, a gap where the path belongs, and a line
        // that is one token are all malformed, not requests for the root.
        assertNull(FileServer.requestPath("GET".split(" ")));
        assertNull(FileServer.requestPath("GET  /x".split(" ")));
        assertNull(FileServer.requestPath("GET\t/x".split(" ")));
    }

    @Test
    public void theQueryIsNotPartOfThePath() {
        assertEquals("/photos", FileServer.requestPath("GET /photos?a=1 HTTP/1.1".split(" ")));
    }

    @Test
    public void aLineOfSpacesIsNotARequest() {
        // " ".split(" ") has no parts at all, which is what a client that
        // sends nothing but spaces produces.
        assertNull(FileServer.requestPath(" ".split(" ")));
    }

    @Test
    public void anEscapeIsUndone() {
        assertEquals("/my photos/a.jpg", FileServer.decodePath("/my%20photos/a.jpg"));
    }

    @Test
    public void aBrokenEscapeIsNotAPath() {
        assertNull(FileServer.decodePath("/%zz"));
    }

    @Test
    public void aPlusIsAPlusNotASpace() {
        assertEquals("/a+b.txt", FileServer.decodePath("/a+b.txt"));
        assertEquals("/a+b.txt", FileServer.decodePath("/a%2Bb.txt"));
        assertEquals("/a b.txt", FileServer.decodePath("/a%20b.txt"));
    }

    @Test
    public void aLineIsRead() throws IOException {
        assertEquals("GET /a HTTP/1.1",
                FileServer.readLine(new ByteArrayInputStream(
                        "GET /a HTTP/1.1\r\n".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void anEndlessLineIsRefused() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            sb.append('x');
        }
        assertSame(FileServer.TOO_LONG, FileServer.readLine(new ByteArrayInputStream(
                sb.toString().getBytes(StandardCharsets.UTF_8))));
    }

    /**
     * A folder asked for without the slash answers a redirect to the
     * address that has it: the links in a listing are relative, so they
     * only resolve from an address that ends in the slash.
     */
    @Test
    public void aFolderWithoutItsSlashRedirects() throws IOException {
        File root = Files.createTempDirectory("sonora-share-test").toFile();
        assertTrue(new File(root, "sub").mkdirs());
        FileServer server = new FileServer();
        Socket socket = null;
        try {
            socket = askSocket(server.start(root), "GET /sub HTTP/1.1\r\n\r\n");
            assertEquals("HTTP/1.1 301 Moved Permanently",
                    FileServer.readLine(socket.getInputStream()));
            assertEquals("Location: /sub/",
                    FileServer.readLine(socket.getInputStream()));
        } finally {
            if (socket != null) {
                socket.close();
            }
            server.stop();
        }
    }

    /** The root is a folder asked for with its slash: it is listed. */
    @Test
    public void theRootIsListed() throws IOException {
        File root = Files.createTempDirectory("sonora-share-test").toFile();
        FileServer server = new FileServer();
        try {
            assertEquals("HTTP/1.1 200 OK",
                    ask(server.start(root), "GET / HTTP/1.1\r\n\r\n"));
        } finally {
            server.stop();
        }
    }

    /**
     * A folder asked for with the slash already encoded ("/sub%2F") asked
     * for the folder: it is served, not redirected into a double slash.
     */
    @Test
    public void aFolderWithAnEncodedSlashIsServed() throws IOException {
        File root = Files.createTempDirectory("sonora-share-test").toFile();
        assertTrue(new File(root, "sub").mkdirs());
        FileServer server = new FileServer();
        try {
            assertEquals("HTTP/1.1 200 OK",
                    ask(server.start(root), "GET /sub%2F HTTP/1.1\r\n\r\n"));
        } finally {
            server.stop();
        }
    }

    @Test
    public void aMalformedLineAnswers400() throws IOException {
        File root = Files.createTempDirectory("sonora-share-test").toFile();
        FileServer server = new FileServer();
        try {
            assertEquals("HTTP/1.1 400 Bad Request",
                    ask(server.start(root), "GET\r\n\r\n"));
        } finally {
            server.stop();
        }
    }

    @Test
    public void anOverlongLineAnswers414() throws IOException {
        File root = Files.createTempDirectory("sonora-share-test").toFile();
        FileServer server = new FileServer();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 9000; i++) {
            sb.append('x');
        }
        try {
            assertEquals("HTTP/1.1 414 URI Too Long",
                    ask(server.start(root), sb.toString() + "\r\n\r\n"));
        } finally {
            server.stop();
        }
    }

    /** Sends a request and returns the first line of the answer. */
    private static String ask(int port, String request) throws IOException {
        try (Socket socket = askSocket(port, request)) {
            return FileServer.readLine(socket.getInputStream());
        }
    }

    private static Socket askSocket(int port, String request) throws IOException {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(5000);
        socket.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
        return socket;
    }

    /**
     * A link that points back at its own folder would copy the disk forever:
     * the copy has to leave it out. Real files still land.
     */
    @Test
    public void aCopyDoesNotFollowALinkOutOfItsFolder() throws IOException {
        File temp = Files.createTempDirectory("sonora-test").toFile();
        File source = new File(temp, "source");
        assertTrue(source.mkdirs());
        Files.write(new File(source, "a.txt").toPath(), "a".getBytes(StandardCharsets.UTF_8));
        try {
            Files.createSymbolicLink(new File(source, "loop").toPath(), source.toPath());
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            // A filesystem without links cannot run this check.
            return;
        }
        File destination = new File(temp, "destination");
        assertTrue(FileServer.copyFile(source, destination));
        assertTrue(new File(destination, "a.txt").isFile());
        assertFalse(new File(destination, "loop").exists());
    }
}
