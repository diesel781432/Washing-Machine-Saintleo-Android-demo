package edu.saintleo.laundrydemo;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.Locale;

/** A washer or dryer returned by the local Java API or the external API. */
public final class Machine {
    public static final String AVAILABLE = "AVAILABLE";
    public static final String IN_USE = "IN_USE";
    public static final String OUT_OF_SERVICE = "OUT_OF_SERVICE";

    public final String id;
    public final String name;
    public final String type;
    public String status;
    public int remainingMinutes;
    /** Exact Unix epoch time at which the current cycle finishes, in milliseconds. */
    public long endTimeMs;

    public Machine(String id, String name, String type, String status, int remainingMinutes) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.status = status;
        this.remainingMinutes = remainingMinutes;
    }

    public Machine(String id, String name, String type, String status,
                   int remainingMinutes, long endTimeMs) {
        this(id, name, type, status, remainingMinutes);
        this.endTimeMs = endTimeMs;
    }

    public static Machine fromJson(JSONObject json) throws JSONException {
        String status = json.getString("status");
        if (!isValidStatus(status)) throw new JSONException("Invalid machine status: " + status);
        int minutes = Math.max(0, json.optInt("remaining_minutes", 0));
        long endTime = json.optLong("end_time_ms", 0L);
        // Compatibility with real APIs that expose duration rather than an exact deadline.
        // An authoritative end_time_ms is preferable because it avoids drift during refreshes.
        if (IN_USE.equals(status) && endTime <= 0L) {
            long seconds = json.has("remaining_seconds")
                    ? Math.max(0L, json.optLong("remaining_seconds", 0L))
                    : minutes * 60L;
            if (seconds > 0L) endTime = System.currentTimeMillis() + seconds * 1000L;
        }
        return new Machine(
                json.getString("id"),
                json.getString("name"),
                json.optString("type", "washer"),
                status,
                minutes,
                endTime);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("type", type);
        json.put("status", status);
        json.put("remaining_minutes", remainingMinutes);
        json.put("end_time_ms", endTimeMs);
        return json;
    }

    public static boolean isValidStatus(String value) {
        return AVAILABLE.equals(value) || IN_USE.equals(value) || OUT_OF_SERVICE.equals(value);
    }

    public String statusLabel() {
        return statusLabel(System.currentTimeMillis());
    }

    /** The screen calls this every second; there is no need to poll the API every second. */
    public String statusLabel(long nowMs) {
        if (AVAILABLE.equals(status)) return "Available";
        if (IN_USE.equals(status)) {
            if (endTimeMs > 0L) {
                long millisLeft = Math.max(0L, endTimeMs - nowMs);
                long secondsLeft = (millisLeft + 999L) / 1000L;
                long minutes = secondsLeft / 60L;
                long seconds = secondsLeft % 60L;
                return "In Use · " + String.format(Locale.US, "%02d:%02d", minutes, seconds);
            }
            return "In Use";
        }
        return "Out of Service";
    }
}
