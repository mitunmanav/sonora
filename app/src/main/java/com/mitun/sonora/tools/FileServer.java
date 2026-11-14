package com.mitun.sonora.tools;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A tiny HTTP server for pulling files off the TV.
 *
 * It runs on a thread inside the visible Share screen and dies with it, so
 * there is no listening socket when the app is closed. It serves one
 * directory and nothing else, and it does not talk to the internet.
 */
public final class FileServer {
    private static final int BUFFER = 16384;
    /** A request line longer than this is not a request, it is an attack. */
    private static final int MAX_REQUEST_LINE = 8192;

    private ServerSocket socket;
    private Thread thread;
    private ExecutorService pool;
    /** Every accepted connection, so stop() can end transfers already running. */
    private final Set<Socket> clients = Collections.synchronizedSet(new HashSet<Socket>());
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
        pool = new ThreadPoolExecutor(
                2, 4, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(16),
                runnable -> {
                    Thread worker = new Thread(runnable, "sonora-share-io");
                    worker.setDaemon(true);
                    return worker;
                },
                new ThreadPoolExecutor.AbortPolicy());
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
        // An accepted transfer keeps streaming when only the listener dies,
        // so every client the server still knows about is closed too.
        synchronized (clients) {
            for (Socket client : clients) {
                try {
                    client.close();
                } catch (IOException ignored) {
                }
            }
            clients.clear();
        }
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
        Thread accept = thread;
        thread = null;
        if (accept != null && accept != Thread.currentThread()) {
            try {
                accept.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        port = 0;
    }

    public boolean isRunning() {
        return running;
    }


    /** The address the other device types in. */
    public String address() {
        String ip = SetupGuide.ipAddress();
        return ip.length() == 0 ? "" : "http://" + ip + ":" + port + "/";
    }

    private void acceptLoop() {
        int failures = 0;
        while (running) {
            try {
                // A local copy: stop() can null the field while this thread is
                // between the check and the call, and a null socket here is a
                // crash on a background thread.
                ServerSocket listener = socket;
                if (listener == null) {
                    return;
                }
                Socket client = listener.accept();
                failures = 0;
                // Tracked from here on: a socket queued in the pool is still
                // closed by stop(), not just one a worker already took.
                clients.add(client);
                try {
                    client.setSoTimeout(15000);
                    ExecutorService p = pool;
                    if (p != null && !p.isShutdown()) {
                        p.execute(() -> handle(client));
                    } else {
                        closeQuietly(client);
                    }
                } catch (RejectedExecutionException | IOException e) {
                    closeQuietly(client);
                }
            } catch (IOException e) {
                // A single failed connection must not kill the server, but a
                // listener that keeps failing - out of file descriptors, or
                // a socket closed under it - must not spin either.
                if (!running || ++failures >= 5) {
                    running = false;
                    return;
                }
            }
        }
    }

    private void closeQuietly(Socket client) {
        clients.remove(client);
        try {
            client.close();
        } catch (IOException ignored) {
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
            // The headers are not needed, but they must leave the wire: a
            // close with them still incoming can RST the answer on its way.
            while (readLine(in) != null) {
                // Read and forgotten.
            }
            OutputStream out = new BufferedOutputStream(client.getOutputStream());
            if (request == TOO_LONG) {
                writeError(out, 414);
                out.flush();
                return;
            }
            String[] parts = request.split(" ");
            String path = requestPath(parts);
            if (path == null) {
                // A line of nothing but spaces is not a request, and anything
                // on the network can send one.
                writeError(out, 400);
                out.flush();
                return;
            }
            // A HEAD gets the same answer a GET would, minus the body. Every
            // other verb is one this server does not do.
            boolean get = "GET".equalsIgnoreCase(parts[0]);
            boolean head = "HEAD".equalsIgnoreCase(parts[0]);
            if (get || head) {
                if (serve(path, out, head)) {
                    return;
                }
                writeError(out, 404);
                out.flush();
                return;
            }
            writeError(out, 405);
            out.flush();
        } catch (IOException | RuntimeException ignored) {
        } finally {
            closeQuietly(client);
        }
    }

    /**
     * The path a request asks for, or null when the request is not one.
     *
     * The query string is not part of the path.
     */
    static String requestPath(String[] parts) {
        // A request is at least a verb and a path: "GET" alone, or a gap
        // where the path belongs, is a malformed line, not a root request.
        if (parts.length < 2 || parts[1].isEmpty()) {
            return null;
        }
        String path = parts[1];
        int query = path.indexOf('?');
        return query >= 0 ? path.substring(0, query) : path;
    }

    /**
     * The path with the escapes undone, or null when the escapes are broken.
     */
    static String decodePath(String path) {
        try {
            // URLDecoder turns a plus into a space, but a plus in a path is a
            // plus: it is escaped first, so only real escapes are undone.
            return java.net.URLDecoder.decode(path.replace("+", "%2B"), "UTF-8");
        } catch (java.io.UnsupportedEncodingException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * The marker readLine returns for a line that ran past the limit, so a
     * too-long request gets an answer instead of a silent close.
     */
    static final String TOO_LONG = "\uE000";

    /**
     * One line of the request, null when there is none, or TOO_LONG when it
     * ran past the limit.
     *
     * A line longer than any real request is not read: the socket timeout
     * only bounds a read that blocks, and a client that keeps sending would
     * otherwise fill the memory on its own.
     */
    static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                if (sb.length() >= MAX_REQUEST_LINE) {
                    return TOO_LONG;
                }
                sb.append((char) c);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** Serves a path inside the shared directory. Returns false when not found. */
    private boolean serve(String path, OutputStream out, boolean head) throws IOException {
        String relative = path.startsWith("/") ? path.substring(1) : path;
        // A name with a space arrives as %20, and the folder has to be found
        // under its real name before its index page can be checked.
        String decoded = decodePath(relative);
        if (decoded == null) {
            // A broken escape is not a path. It simply does not exist.
            writeError(out, 400);
            out.flush();
            return true;
        }
        relative = decoded;
        // The slash matters on either spelling: "/dir%2F" already asked for
        // the folder, and "/" strips to an empty relative that must not be
        // read as slash-less or the root would redirect to "//".
        boolean askedSlash = path.endsWith("/") || relative.endsWith("/");
        if (relative.endsWith("/")) {
            // A folder with an index of its own is served as that page; every
            // other folder is listed, or the links in the listing are dead.
            File candidate = new File(root, relative);
            if (new File(candidate, "index.html").isFile()) {
                relative = relative + "index.html";
            } else {
                relative = relative.substring(0, relative.length() - 1);
            }
        }
        File file = new File(root, relative);
        // Never serve anything outside the shared directory. The separator
        // matters: without it a folder that merely starts with the same name
        // would pass.
        String inside = root.getCanonicalPath() + File.separator;
        String target = file.getCanonicalPath();
        if (!target.equals(root.getCanonicalPath()) && !target.startsWith(inside)) {
            writeError(out, 403);
            out.flush();
            return true;
        }
        if (file.isDirectory()) {
            // The listing's links are relative to the address they were
            // served from, so a folder asked for without the slash is sent
            // to the address that has one.
            if (!askedSlash) {
                writeRedirect(out, path + "/");
                out.flush();
                return true;
            }
            writeIndex(out, file, head);
            out.flush();
            return true;
        }
        if (!file.isFile()) {
            return false;
        }
        long length = file.length();
        writeHeaders(out, 200, mimeOf(file.getName()), length);
        out.flush();
        if (!head) {
            copy(file, out, length);
            out.flush();
        }
        return true;
    }

    private void writeIndex(OutputStream out, File dir, boolean head) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><meta charset=\"utf-8\"><title>Sonora</title>");
        html.append("<style>body{font-family:sans-serif;background:#111;color:#eee;padding:2em}");
        html.append("a{color:#8ab4f8;display:block;padding:.5em 0;text-decoration:none}");
        html.append("h1{font-weight:300}</style></head><body>");
        // The filesystem root has no name of its own; its path is the heading.
        String name = dir.getName();
        html.append("<h1>")
                .append(escape(name.length() == 0 ? dir.getPath() : name))
                .append("</h1>");
        File[] children = dir.listFiles();
        if (children != null) {
            java.util.Arrays.sort(children);
            for (File child : children) {
                html.append("<a href=\"").append(encode(child.getName()))
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
        if (!head) {
            out.write(bytes);
        }
    }

    private void writeHeaders(OutputStream out, int code, String type, long length)
            throws IOException {
        String status = code == 200 ? "200 OK"
                : code == 400 ? "400 Bad Request"
                : code == 403 ? "403 Forbidden"
                : code == 405 ? "405 Method Not Allowed"
                : code == 414 ? "414 URI Too Long"
                : "404 Not Found";
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

    /** Sends the client to the address with the slash it left out. */
    private void writeRedirect(OutputStream out, String location) throws IOException {
        String head = "HTTP/1.1 301 Moved Permanently\r\n"
                + "Location: " + location + "\r\n"
                + "Content-Length: 0\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Streams exactly the length the header promised. A file that grew since
     * it was statted is cut at the advertised size instead of streaming on;
     * one that shrank ends early, which the closed connection reports.
     */
    private static void copy(File file, OutputStream out, long length) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[BUFFER];
            long left = length;
            while (left > 0) {
                int read = in.read(buffer, 0, (int) Math.min(buffer.length, left));
                if (read == -1) {
                    break;
                }
                out.write(buffer, 0, read);
                left -= read;
            }
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String encode(String text) {
        try {
            return java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            return text;
        }
    }

    /** The type a browser should show the file as. */
    private static String mimeOf(String name) {
        String lower = name.toLowerCase(java.util.Locale.US);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".txt") || lower.endsWith(".log")) {
            return "text/plain; charset=utf-8";
        }
        if (lower.endsWith(".srt")) {
            return "text/plain";
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "text/html; charset=utf-8";
        }
        if (lower.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (lower.endsWith(".mkv")) {
            return "video/x-matroska";
        }
        if (lower.endsWith(".avi")) {
            return "video/x-msvideo";
        }
        if (lower.endsWith(".mov")) {
            return "video/quicktime";
        }
        if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        return "application/octet-stream";
    }

    /**
     * Copies a file or a whole folder, creating the destination when it is
     * missing. A folder that cannot be copied says so instead of pretending.
     */
    public static boolean copyFile(File source, File destination) {
        if (source == null || destination == null || !source.exists()) {
            return false;
        }
        try {
            // A file opened as its own destination is truncated the moment
            // the output stream opens, so the same file is not a copy.
            if (source.getCanonicalPath().equals(destination.getCanonicalPath())) {
                return false;
            }
            if (source.isDirectory()) {
                String srcPath = source.getCanonicalPath();
                String dstPath = destination.getCanonicalPath();
                if (dstPath.equals(srcPath) || dstPath.startsWith(srcPath + File.separator)) {
                    return false;
                }
                return copyFolder(source, destination);
            }
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
                out.flush();
            }
            return destination.exists() && destination.length() == source.length();
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean copyFolder(File source, File destination) {
        if (!destination.exists() && !destination.mkdirs()) {
            return false;
        }
        File[] children = source.listFiles();
        if (children == null) {
            return false;
        }
        String inside;
        try {
            inside = source.getCanonicalPath() + File.separator;
        } catch (IOException e) {
            return false;
        }
        for (File child : children) {
            // A link that resolves outside the folder - or back into one of
            // its parents - would copy the disk forever. A real child keeps
            // its canonical path inside the folder it was listed from.
            try {
                if (!child.getCanonicalPath().startsWith(inside)) {
                    continue;
                }
            } catch (IOException e) {
                return false;
            }
            if (!copyFile(child, new File(destination, child.getName()))) {
                return false;
            }
        }
        return true;
    }

    /** The folder the app may always write to, on every storage layout. */
    public static File appFolder(android.content.Context context) {
        File external = context.getExternalFilesDir(null);
        if (external != null) {
            return external;
        }
        // Without external storage the app's own folder inside its sandbox is
        // the one place it can always read and write. It exists, and it needs
        // no permission.
        File folder = new File(context.getFilesDir(), "sonora");
        //noinspection ResultOfMethodCallIgnored
        folder.mkdirs();
        return folder;
    }
}
