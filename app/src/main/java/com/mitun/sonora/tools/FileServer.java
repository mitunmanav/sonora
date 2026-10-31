package com.mitun.sonora.tools;

import android.os.Environment;

import com.mitun.sonora.ui.Ui;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * A tiny HTTP server for pulling files off the TV.
 *
 * It runs on a thread inside the visible Share screen and dies with it, so
 * there is no listening socket when the app is closed. It serves one
 * directory and nothing else, and it does not talk to the internet.
 */
public final class FileServer {
    private static final int BUFFER = 16384;

    private ServerSocket socket;
    private Thread thread;
    private volatile boolean running;
    private File root;
    private int port;

    /** Starts serving a directory. Returns the port it listens on. */
    public int start(File rootDirectory) throws IOException {
        stop();
        root = rootDirectory;
        socket = new ServerSocket(0);
        port = socket.getLocalPort();
        running = true;
        thread = new Thread(this::acceptLoop, "sonora-share");
        thread.setDaemon(true);
        thread.start();
        return port;
    }

    public void stop() {
        running = false;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            socket = null;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int port() {
        return port;
    }

    public File root() {
        return root;
    }

    /** The address the other device types in. */
    public String address() {
        String ip = SetupGuide.ipAddress();
        return ip.length() == 0 ? "" : "http://" + ip + ":" + port + "/";
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = socket.accept();
                new Thread(() -> handle(client), "sonora-share-client").start();
            } catch (IOException e) {
                if (running) {
                    // A single failed connection must not kill the server.
                    continue;
                }
                return;
            }
        }
    }

    private void handle(Socket client) {
        try {
            client.setSoTimeout(10000);
            InputStream in = new BufferedInputStream(client.getInputStream());
            String request = readLine(in);
            if (request == null) {
                return;
            }
            String[] parts = request.split(" ");
            String path = parts.length > 1 ? parts[1] : "/";
            int query = path.indexOf('?');
            if (query >= 0) {
                path = path.substring(0, query);
            }
            OutputStream out = new BufferedOutputStream(client.getOutputStream());
            if ("GET".equalsIgnoreCase(parts[0]) && serve(path, out)) {
                return;
            }
            writeError(out, 404);
            out.flush();
        } catch (IOException ignored) {
        } finally {
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }
    }

    private String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                sb.append((char) c);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** Serves a path inside the shared directory. Returns false when not found. */
    private boolean serve(String path, OutputStream out) throws IOException {
        String relative = path.startsWith("/") ? path.substring(1) : path;
        if (relative.endsWith("/")) {
            relative = relative + "index.html";
        }
        File file = new File(root, relative);
        // Never serve anything outside the shared directory.
        if (!file.getCanonicalPath().startsWith(root.getCanonicalPath())) {
            writeError(out, 403);
            out.flush();
            return true;
        }
        if (file.isDirectory()) {
            writeIndex(out, file);
            out.flush();
            return true;
        }
        if (!file.isFile()) {
            return false;
        }
        writeHeaders(out, 200, "application/octet-stream", file.length());
        out.flush();
        copy(file, out);
        out.flush();
        return true;
    }

    private void writeIndex(OutputStream out, File dir) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><meta charset=\"utf-8\"><title>Sonora</title>");
        html.append("<style>body{font-family:sans-serif;background:#111;color:#eee;padding:2em}");
        html.append("a{color:#8ab4f8;display:block;padding:.5em 0;text-decoration:none}");
        html.append("h1{font-weight:300}</style></head><body>");
        html.append("<h1>").append(escape(dir.getName())).append("</h1>");
        File[] children = dir.listFiles();
        if (children != null) {
            java.util.Arrays.sort(children);
            for (File child : children) {
                html.append("<a href=\"").append(escape(child.getName()))
                        .append(child.isDirectory() ? "/" : "")
                        .append("\">").append(escape(child.getName()));
                if (child.isDirectory()) {
                    html.append("/");
                } else {
                    html.append(" <span style=\"color:#888\">")
                            .append(com.mitun.sonora.SystemInfo.bytes(child.length()))
                            .append("</span>");
                }
                html.append("</a>");
            }
        }
        html.append("</body></html>");
        byte[] bytes = html.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeHeaders(out, 200, "text/html; charset=utf-8", bytes.length);
        out.write(bytes);
    }

    private void writeHeaders(OutputStream out, int code, String type, long length)
            throws IOException {
        String status = code == 200 ? "200 OK" : code == 403 ? "403 Forbidden" : "404 Not Found";
        String head = "HTTP/1.1 " + status + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void writeError(OutputStream out, int code) throws IOException {
        writeHeaders(out, code, "text/plain", 0);
    }

    private static void copy(File file, OutputStream out) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[BUFFER];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Copies a file, creating the destination folder when it is missing. */
    public static boolean copyFile(File source, File destination) {
        try {
            File parent = destination.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return false;
            }
            try (InputStream in = new BufferedInputStream(new FileInputStream(source));
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) {
                byte[] buffer = new byte[BUFFER];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** The folder the app may always write to, on every storage layout. */
    public static File appFolder(android.content.Context context) {
        File external = context.getExternalFilesDir(null);
        if (external != null) {
            return external;
        }
        return new File(Environment.getDataDirectory(), "sonora");
    }
}
