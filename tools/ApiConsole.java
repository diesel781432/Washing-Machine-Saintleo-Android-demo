import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Scanner;

/**
 * Pure Java terminal controller for the embedded demo API inside the Android emulator.
 * Run "adb forward tcp:8765 tcp:8765" first, then:
 *   javac tools/ApiConsole.java
 *   java -cp tools ApiConsole
 */
public final class ApiConsole {
    private static final String BASE = "http://127.0.0.1:8765";

    public static void main(String[] args) {
        System.out.println("LaundryWatch Java API Console");
        System.out.println("Keep the Android emulator/app running; run adb forward tcp:8765 tcp:8765 first.");
        System.out.println("Commands: list | set washer-1 IN_USE 30 | set dryer-2 AVAILABLE | reset | health | help | exit");
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("laundry> ");
                if (!scanner.hasNextLine()) break;
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;
                String[] cmd = line.split("\\s+");
                String action = cmd[0].toLowerCase(Locale.US);
                if (action.equals("exit") || action.equals("quit")) break;
                if (action.equals("help")) {
                    System.out.println("list | health | reset | set <machine-id> <AVAILABLE|IN_USE|OUT_OF_SERVICE> [minutes] | exit");
                    continue;
                }
                try {
                    if (action.equals("list") && cmd.length == 1) {
                        System.out.println(request("GET", "/machines", null));
                    } else if (action.equals("health") && cmd.length == 1) {
                        System.out.println(request("GET", "/health", null));
                    } else if (action.equals("reset") && cmd.length == 1) {
                        System.out.println(request("POST", "/reset", "{}"));
                    } else if (action.equals("set") && (cmd.length == 3 || cmd.length == 4)) {
                        if (!cmd[1].matches("(washer|dryer)-[1-3]")) {
                            System.out.println("Valid demo IDs: washer-1..3, dryer-1..3");
                            continue;
                        }
                        String status = cmd[2].toUpperCase(Locale.US);
                        if (!(status.equals("AVAILABLE") || status.equals("IN_USE") || status.equals("OUT_OF_SERVICE"))) {
                            System.out.println("Invalid status"); continue;
                        }
                        int minutes = cmd.length == 4 ? Integer.parseInt(cmd[3]) : 0;
                        if (minutes < 0 || minutes > 180) {
                            System.out.println("Minutes must be from 0 to 180"); continue;
                        }
                        System.out.println(request("POST", "/machines/" + cmd[1] + "/status",
                                "{\"status\":\"" + status + "\",\"minutes\":" + minutes + "}"));
                    } else {
                        System.out.println("Unknown command. Enter help.");
                    }
                } catch (Exception error) {
                    System.out.println("Request error: " + error.getMessage());
                }
            }
        }
    }

    private static String request(String method, String path, String json) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE + path).openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        connection.setRequestMethod(method);
        try {
            if (json != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                byte[] data = json.getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) { output.write(data); }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            StringBuilder result = new StringBuilder("HTTP " + status + " ");
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) result.append(line);
                }
            }
            return result.toString();
        } finally {
            connection.disconnect();
        }
    }
}
