package com.mitun.sonora.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
        assertEquals("/", FileServer.requestPath("GET".split(" ")));
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
        assertNull(FileServer.readLine(new ByteArrayInputStream(
                sb.toString().getBytes(StandardCharsets.UTF_8))));
    }
}
