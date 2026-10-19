package edu.saintleo.laundrydemo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tiny HTTP API implemented in Java and bound only to Android's loopback interface.
 * Starts on a daemon worker thread at application startup, not on the UI thread.
 * It runs while this Android app process exists; Android may terminate the process.
 *
 * Routes:
 * GET  /health
 * GET  /machines
 * POST /machines/{id}/status {"status":"IN_USE","minutes":30}
 * POST /reset
 */
public final class DemoApiServer {
    public static final int PORT = 8765;
    public static final String URL = "http://127.0.0.1:" + PORT;
    private static final int MAX_BODY = 8192;
    private final MachineStore store;
    private final CountDownLatch initialized = new CountDownLatch(1);
    private volatile ServerSocket serverSocket;
    private volatile IOException startupError;

    public DemoApiServer(MachineStore store) { this.store = store; }

    public void startAsync() {
        Thread thread = new Thread(this::serve, "laundry-demo-api");
        thread.setDaemon(true);
        thread.start();
    }

    public void awaitStarted() throws IOException {
        try {
            if (!initialized.await(4, TimeUnit.SECONDS)) throw new IOException("Local Java API startup timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for local Java API", e);
        }
        if (startupError != null) throw startupError;
    }

    private void serve() {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT));
            serverSocket = socket;
            initialized.countDown();
            while (!socket.isClosed()) {
                try (Socket client = socket.accept()) {
                    client.setSoTimeout(5000);
                    handle(client);
                } catch (IOException | RuntimeException ignored) {
                    // One malformed client should not stop the background API.
                }
            }
        } catch (IOException e) {
            startupError = e;
        } finally {
            initialized.countDown();
        }
    }

    private void handle(Socket client) throws IOException {
        InputStream input = new BufferedInputStream(client.getInputStream());
        OutputStream output = new BufferedOutputStream(client.getOutputStream());
        String firstLine = readLine(input);
        if (firstLine == null) return;
        String[] parts = firstLine.split(" ");
        if (parts.length != 3 || !parts[2].startsWith("HTTP/")) {
            send(output, 400, error("Invalid HTTP request"));
            return;
        }
        String method = parts[0].toUpperCase(Locale.US);
        String path = parts[1].split("\\?", 2)[0];
        int contentLength = 0;
        String contentType = "";
        String header;
        int lines = 0;
        while ((header = readLine(input)) != null && !header.isEmpty()) {
            if (++lines > 60) { send(output, 400, error("Too many headers")); return; }
            int sep = header.indexOf(':');
            if (sep <= 0) continue;
            String key = header.substring(0, sep).trim().toLowerCase(Locale.US);
            String val = header.substring(sep + 1).trim();
            if (key.equals("content-length")) {
                try { contentLength = Integer.parseInt(val); }
                catch (NumberFormatException e) { send(output, 400, error("Invalid content length")); return; }
            } else if (key.equals("content-type")) contentType = val.toLowerCase(Locale.US);
            else if (key.equals("transfer-encoding")) { send(output, 400, error("Chunked requests not supported")); return; }
        }
        if (contentLength < 0 || contentLength > MAX_BODY) {
            send(output, 413, error("Request body too large")); return;
        }
        byte[] body = new byte[contentLength];
        int offset = 0;
        while (offset < contentLength) {
            int count = input.read(body, offset, contentLength - offset);
            if (count < 0) throw new EOFException("Missing request body");
            offset += count;
        }
        try {
            if (method.equals("GET") && path.equals("/health")) {
                JSONObject json = new JSONObject();
                json.put("ok", true);
                json.put("mode", "demo");
                send(output, 200, json);
            } else if (method.equals("GET") && path.equals("/machines")) {
                send(output, 200, machinesJson());
            } else if (method.equals("POST") && path.equals("/reset")) {
                store.reset();
                send(output, 200, machinesJson());
            } else if (method.equals("POST") && path.startsWith("/machines/")
                    && path.endsWith("/status")) {
                if (!contentType.startsWith("application/json")) {
                    send(output, 415, error("Expected application/json")); return;
                }
                String id = path.substring("/machines/".length(), path.length() - "/status".length());
                JSONObject request = new JSONObject(new String(body, StandardCharsets.UTF_8));
                String status = request.getString("status");
                int minutes = request.optInt("minutes", 0);
                if (!Machine.isValidStatus(status) || minutes < 0 || minutes > 180) {
                    send(output, 400, error("Invalid status or minutes")); return;
                }
                Machine updated = store.update(id, status, minutes);
                if (updated == null) send(output, 404, error("Machine not found"));
                else send(output, 200, updated.toJson());
            } else {
                send(output, 404, error("Route not found"));
            }
        } catch (JSONException | IllegalArgumentException e) {
            send(output, 400, error("Invalid JSON data"));
        }
    }

    private JSONObject machinesJson() throws JSONException {
        List<Machine> machines = store.load();
        JSONArray array = new JSONArray();
        for (Machine machine : machines) array.put(machine.toJson());
        JSONObject result = new JSONObject();
        result.put("machines", array);
        result.put("source", "java-embedded-demo");
        return result;
    }

    private static JSONObject error(String message) {
        JSONObject response = new JSONObject();
        try { response.put("error", message); }
        catch (JSONException ignored) { }
        return response;
    }

    private static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int current;
        while ((current = input.read()) != -1) {
            if (current == '\n') break;
            if (line.size() > 8192) throw new IOException("Header line too long");
            if (current != '\r') line.write(current);
        }
        if (current == -1 && line.size() == 0) return null;
        return line.toString("ISO-8859-1");
    }

    private static void send(OutputStream output, int code, JSONObject json) throws IOException {
        String reason = code == 200 ? "OK" : code == 400 ? "Bad Request"
                : code == 404 ? "Not Found" : code == 413 ? "Payload Too Large"
                : code == 415 ? "Unsupported Media Type" : "Error";
        byte[] payload = json.toString().getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + code + " " + reason + "\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Cache-Control: no-store\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Content-Length: " + payload.length + "\r\n"
                + "Connection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.US_ASCII));
        output.write(payload);
        output.flush();
    }
}
