# Java Embedded API Reference

Built-in HTTP server automatically runs within the Android app process on `http://127.0.0.1:8765`, accessible from inside the Android app. If you use an emulator, run `adb forward tcp:8765 tcp:8765` to expose its local loopback port on your development computer.

**GET `/health`** — returns `{ "ok": true, "mode": "demo" }`.

**GET `/machines`** — returns JSON such as:

```json
{
  "source":"java-embedded-demo",
  "machines":[
    {"id":"washer-1", "name":"Washer 1", "type":"washer", "status":"AVAILABLE", "remaining_minutes":0,"end_time_ms":0},
    {"id":"washer-2", "name":"Washer 2", "type":"washer", "status":"IN_USE", "remaining_minutes":24,"end_time_ms":1792369200000},
    {"id":"washer-3", "name":"Washer 3", "type":"washer", "status":"AVAILABLE", "remaining_minutes":0,"end_time_ms":0},
    {"id":"dryer-1", "name":"Dryer 1", "type":"dryer", "status":"AVAILABLE", "remaining_minutes":0,"end_time_ms":0},
    {"id":"dryer-2", "name":"Dryer 2", "type":"dryer", "status":"IN_USE", "remaining_minutes":38,"end_time_ms":1792370040000},
    {"id":"dryer-3", "name":"Dryer 3", "type":"dryer", "status":"AVAILABLE", "remaining_minutes":0,"end_time_ms":0}
  ]
}
```

The `end_time_ms` field contains the **exact finish timestamp (Unix epoch milliseconds)** for an IN_USE cycle and is `0` when no cycle is running. The illustrative timestamps above are examples only and will differ when the app is running. The UI uses this deadline to count down in `MM:SS` once per second, with the HTTP API polled approximately every 15 seconds. `remaining_minutes` is provided for compatibility and is rounded up to whole minutes. If a real API cannot supply `end_time_ms`, optional `remaining_seconds` is preferred to `remaining_minutes` for countdown accuracy.

**POST `/machines/{id}/status`** — sets the demo state of an existing machine. Requires `Content-Type: application/json`.

```json
{"status":"IN_USE", "minutes":30}
```

Status values: `AVAILABLE`, `IN_USE`, `OUT_OF_SERVICE`. `minutes` must be between 0 and 180. For Available or Out of Service, the API stores 0 minutes. Returns the updated machine record. Invalid status produces HTTP 400; unknown machine ID produces HTTP 404.

**POST `/reset`** — resets demo machine data and returns the current `/machines` result. Send `{}`.

The easiest way to interact is through `tools/ApiConsole.java`, which is implemented in Java and requires only a JDK and the Android SDK's `adb` command.

## Real Mode server requirement

The user-supplied external endpoint **must use HTTPS**, and must implement `GET /machines` returning the same `machines` JSON array described above. This project does not include a real remote server or a campus laundry provider integration. Real Mode cannot report live washer/dryer status until one is connected. It never POSTs mutations or reset requests to real machines.

The embedded Java API has **no authentication**, is **loopback only**, and must be used solely for simulation. Do not expose it as a production service.
