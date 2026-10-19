package edu.saintleo.laundrydemo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Very small Java-only HTTP client. Call only from a worker thread. */
public final class ApiClient {
    private ApiClient() { }

    public static List<Machine> loadMachines(String baseUrl) throws IOException, JSONException {
        JSONObject response = request(baseUrl, "/machines", "GET", null);
        JSONArray items = response.getJSONArray("machines");
        List<Machine> list = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) list.add(Machine.fromJson(items.getJSONObject(i)));
        return list;
    }

    public static void changeStatus(String baseUrl, Machine machine, String status, int minutes)
            throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("status", status);
        body.put("minutes", minutes);
        request(baseUrl, "/machines/" + machine.id + "/status", "POST", body);
    }

    public static void reset(String baseUrl) throws IOException, JSONException {
        request(baseUrl, "/reset", "POST", new JSONObject());
    }

    private static JSONObject request(String baseUrl, String route, String method, JSONObject body)
            throws IOException, JSONException {
        String urlText = baseUrl.trim().replaceAll("/+$", "") + route;
        if (!urlText.startsWith("http://") && !urlText.startsWith("https://"))
            throw new IOException("URL must start with http:// or https://");
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = connection.getOutputStream()) { out.write(payload); }
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder json = new StringBuilder();
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) json.append(line);
                }
            }
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code + ": " + json);
            return new JSONObject(json.toString());
        } finally {
            connection.disconnect();
        }
    }
}
