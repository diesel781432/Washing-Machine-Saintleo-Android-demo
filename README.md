# Campus Laundry App — Basic Java Android UI + Java API

A standalone Android Studio project, implemented in **Java** (and Android XML layouts). It follows the concept of a campus washer/dryer availability app, with a **real HTTP API built into the Android application for simulation**. No Python server, Kotlin source, or external server setup is needed for Demo Mode.

This is independently written sample code, not a fork of another student's repository.

## Features

- Displays three washers and three dryers and their status: **Available**, **In Use**, or **Out of Service**.
- **Demo Mode (default):** automatically starts a **pure-Java HTTP server** on `127.0.0.1:8765` in a background thread as soon as the Android process launches. The Android user interface talks to it with Java `HttpURLConnection`, exactly as it would talk to an external server.
- **Tap the Change button next to a machine** to send an HTTP POST that simulates its status. You can choose a **1-minute test cycle** or a **30-minute cycle**. Every In Use machine shows a live **MM:SS countdown that updates once a second** and automatically becomes Available when the cycle expires.
- **Reset Demo Machines** restores the default statuses and starts fresh timers for the in-use sample machines. Demo deadlines are stored in Android SharedPreferences, so time continues passing accurately while the app is paused, closed, or reopened. **Refresh Status** retrieves status with an HTTP GET; the screen fetches the API every ~15 seconds while visible, independently of the 1-second UI timer.
- **Switch to Real Mode** to fetch real data from your own authorized **HTTPS** API. The app never displays fake statuses as real. It shows a connection error or "No live machine data" until a real endpoint is provided. Real Mode is read-only so students cannot control actual machines.
- A pure Java **terminal API console** is included to change statuses from your development PC while the emulator is running (via `adb forward`).

## Opening and running

1. Extract the ZIP file and open the **CampusLaundryJava** directory in **Android Studio** using **File → Open**.
2. Allow Gradle to sync, and install SDK Platform 35 if prompted. Recommended JDK: 17.
3. Start an Android emulator (API 24+) and click **Run ▶** for the `app` configuration.
4. The embedded API starts automatically. You will see six simulated machines; **no second server process is required**.
5. Press **Change** next to an Available washer, choose **In Use (1-minute test)**, and watch the countdown `01:00 → 00:59 → ... → 00:00`. At the end it changes to **Available** automatically. Choose 30 minutes to test longer cycles. Press Refresh or Reset to test the Java API.
6. Press **Switch to Real Mode** to enter your real endpoint, or **Switch to Demo Mode** to return to the simulation.
7. To edit the basic screen with drag-and-drop, open `app/src/main/res/layout/activity_main.xml` and select **Design** or **Split** in Android Studio. Open `item_machine.xml` to edit one washer/dryer row. These layouts use only default Android widgets. The machine rows are added from Java so the API may return any number of machines.

**Lifecycle note:** The Java API is an in-process background server, **not** a guaranteed always-on server. It remains available while the Android app process is alive; Android may terminate the process after the user leaves the app. Launching the app starts it again. This is appropriate for a course demonstration but not a campus-wide server.

## API console on the development computer (also Java)

With Android emulator running, and the app launched:

```bash
adb forward tcp:8765 tcp:8765
javac tools/ApiConsole.java
java -cp tools ApiConsole
```

The interactive console supports:

```text
list
health
set washer-1 IN_USE 30
set dryer-2 OUT_OF_SERVICE
set washer-3 AVAILABLE
reset
exit
```

The Android app updates the timer text once per second and synchronizes machine data from the API about every 15 seconds. Press **Refresh Status** to fetch changes immediately. For a physical device, `adb -s <device-id> forward tcp:8765 tcp:8765` works while connected over USB with debugging enabled.

The fake server is bound to **127.0.0.1 only**, not exposed to a public network. Its endpoints require no login because it contains only simulated coursework data. Do not reuse this server for real campus laundry data.

## Real Mode — connecting to actual machine data

1. Obtain an authorized API/data feed from your laundry system provider or campus IT. The application cannot directly read real machines without one.
2. The remote server must serve **HTTPS** and have a `GET /machines` endpoint. For example, enter `https://laundry.example.edu/api` if the server supports `GET https://laundry.example.edu/api/machines`. (This is an example domain, not a working service.)
3. Tap **Switch to Real Mode**, enter the HTTPS base URL, then **Save URL & Connect**.
4. The app requests fresh JSON from the remote server every ~15 seconds while visible or when Refresh is pressed. It never sends commands to real machines.
5. No API authentication is implemented. If the campus provider requires a token, OAuth, or a custom response format, you must adapt `ApiClient.java` and use an approved credential-handling method before deployment. Do not put secret API keys into source code or store them unencrypted.

Expected response (details in `API_REFERENCE.md`):

```json
{
  "machines": [
    {"id":"washer-1", "name":"Washer 1", "type":"washer", "status":"AVAILABLE", "remaining_minutes":0, "end_time_ms":0},
    {"id":"dryer-1", "name":"Dryer 1", "type":"dryer", "status":"IN_USE", "remaining_minutes":18, "end_time_ms":1792369200000}
  ]
}
```

## Beginner-style interface (Android Studio Design view)

The design is intentionally plain, like a simple layout made using Android Studio's Palette:
- `activity_main.xml` contains the main title, mode button, basic text labels, laundry lists, and refresh/reset buttons.
- `item_machine.xml` is one repeated list row with a machine name, Change button, status, and a separate timer label.
- Open either XML file in Android Studio and use the **Design** or **Split** tab to move or edit widgets visually.
- `MainActivity.java` fills these widgets with data from the Java API. The app remains **Java + XML**; no Kotlin or Python components are included.

This is an original beginner-style layout using normal Android widgets, rather than a copy of a third-party UI.

## How the live countdown works

- `MachineStore.java` saves an **absolute finish timestamp** in each simulated machine's `*.deadline` SharedPreferences key, rather than decrementing minutes in storage each second.
- The Java demo server exposes that timestamp as the optional JSON field **`end_time_ms`** (Unix epoch milliseconds). In Real Mode, an external API should supply this field for accurate countdowns. If it only supplies `remaining_seconds` or `remaining_minutes`, the app can estimate a deadline when the response arrives, but refreshes may cause small timing jumps.
- `MainActivity.java` runs a `Handler` once per second while the screen is active, calculating remaining time from `end_time_ms - System.currentTimeMillis()`. This updates text labels without requesting the server each second or running a battery-draining always-on job.
- When a demo deadline passes, the label becomes **Available** and the app fetches the authoritative new state from its Java API. While the app is in the background, Android can pause the screen updates; reopening it immediately calculates the correct remaining time.
- The countdown is only a **simulation** in Demo Mode. In Real Mode, the server remains authoritative, and elapsed estimates say "awaiting update" until it confirms the machine has finished.

## Project structure

- `app/src/main/java/edu/saintleo/laundrydemo/LaundryApplication.java` — launches the embedded Java server when the app starts.
- `.../DemoApiServer.java` — Java ServerSocket HTTP API, bound to 127.0.0.1:8765.
- `.../MainActivity.java` — Java screen, demo editing, real-mode button, timed refresh.
- `.../MachineStore.java` — durable Android demo data storage.
- `.../Machine.java` — JSON machine model.
- `.../ApiClient.java` — Java HTTP client for demo and real modes.
- `app/src/main/res/layout/activity_main.xml` — simple Android Studio XML screen (TextViews, EditText, LinearLayouts, Buttons).
- `app/src/main/res/layout/item_machine.xml` — individual washer/dryer row, designed for Android Studio's Design/Split editor.
- `tools/ApiConsole.java` — independent Java command-line controller.
- `API_REFERENCE.md` — route definitions and examples.

## Build details and verification

Project uses Android Gradle Plugin 8.6.1, Gradle 8.7, compile/target SDK 35, min SDK 24, and Java 17. The project source uses only Android SDK and standard Java libraries; there is **no Python or Kotlin**. Android Studio will need to download dependencies if they are not cached.

The included Gradle bootstrap is a self-built, checksum-validating Java launcher because the official wrapper JAR was not available when the initial project was created. Its Java source is in `tooling/GradleWrapperMain.java`. It verifies the pinned Gradle distribution checksum. To replace it with an official Gradle wrapper, run `gradle wrapper --gradle-version 8.7` from a trusted Gradle installation.

**Testing limit:** The Java terminal controller is compilable with a JDK, and the Android project's source and resources can be statically checked here, but an Android Studio emulator/build is required for an end-to-end test of the Android API server.

## Changelog

- **v0.1** — Six sample machine statuses displayed in a Java Android app.
- **v0.2** — Refresh, reset, and persistent demo statuses.
- **v0.3** — Replaced Python mock server with Java embedded API started on application launch; added Java API console, explicit Demo/Real Mode switch, HTTPS external API read-only mode, and foreground refresh.

- **v0.4** — Display live second-by-second `MM:SS` countdowns from persisted absolute deadlines, initialize demo timers on first launch, automatically mark completed demo cycles Available, and add a 1-minute test option.

- **v0.5** — Replaced styled dynamic cards with simple, beginner-style XML layouts (standard TextViews, Buttons, and LinearLayouts), separated washers and dryers, added a basic Change button on each demo machine, and displayed countdowns on their own `Time left: MM:SS` lines. Retained Java API and Demo/Real Mode.
