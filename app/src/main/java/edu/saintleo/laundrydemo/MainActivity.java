package edu.saintleo.laundrydemo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Java Android client with two intentionally separate data sources:
 * - DEMO: automatic embedded Java HTTP API, writable for simulation.
 * - REAL: configurable external HTTPS API, read-only; never fabricates live data.
 */
public final class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(17, 125, 102);
    private static final int AMBER = Color.rgb(171, 99, 13);
    private static final int RED = Color.rgb(170, 48, 55);
    private static final int REFRESH_INTERVAL_MS = 15_000;
    private static final int COUNTDOWN_INTERVAL_MS = 1_000;

    private TextView summary;
    private TextView lastUpdated;
    private TextView message;
    private TextView modeLabel;
    private LinearLayout washerContainer;
    private LinearLayout dryerContainer;
    private LinearLayout realSettings;
    private EditText serverUrl;
    private Button modeButton;
    private Button refreshButton;
    private Button resetButton;
    private Button connectButton;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private List<Machine> machines = new ArrayList<>();
    private final List<StatusView> statusViews = new ArrayList<>();
    private SharedPreferences settings;
    private boolean realMode;
    private boolean busy;
    private boolean active;
    private boolean hasResumedBefore;

    /** Views from item_machine.xml, so countdowns update without rebuilding the list. */
    private static final class StatusView {
        final Machine machine;
        final View row;
        final TextView status;
        final TextView timer;
        StatusView(Machine machine, View row, TextView status, TextView timer) {
            this.machine = machine;
            this.row = row;
            this.status = status;
            this.timer = timer;
        }
    }

    private final Runnable countdownTick = new Runnable() {
        @Override public void run() {
            if (!active) return;
            updateCountdowns();
            mainHandler.postDelayed(this, COUNTDOWN_INTERVAL_MS);
        }
    };

    private final Runnable autoRefresh = new Runnable() {
        @Override public void run() {
            if (!active) return;
            if (!busy && (!realMode || isValidRealUrl(currentServerUrl()))) refresh();
            mainHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        settings = getSharedPreferences("laundry_settings_v2", MODE_PRIVATE);
        summary = findViewById(R.id.summary);
        lastUpdated = findViewById(R.id.lastUpdated);
        message = findViewById(R.id.message);
        modeLabel = findViewById(R.id.modeLabel);
        washerContainer = findViewById(R.id.washerContainer);
        dryerContainer = findViewById(R.id.dryerContainer);
        realSettings = findViewById(R.id.realSettings);
        serverUrl = findViewById(R.id.serverUrl);
        modeButton = findViewById(R.id.modeButton);
        refreshButton = findViewById(R.id.refreshButton);
        resetButton = findViewById(R.id.resetButton);
        connectButton = findViewById(R.id.connectButton);

        realMode = settings.getBoolean("real_mode", false);
        serverUrl.setText(settings.getString("real_url", ""));

        modeButton.setOnClickListener(v -> switchMode());
        refreshButton.setOnClickListener(v -> refresh());
        connectButton.setOnClickListener(v -> connectToRealApi());
        resetButton.setOnClickListener(v -> confirmReset());

        updateModeUi();
        render();
        if (realMode && !isValidRealUrl(currentServerUrl())) {
            setMessage("Real Mode needs a real HTTPS machine API URL. No simulated values are displayed.");
        } else {
            refresh();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        active = true;
        mainHandler.removeCallbacks(autoRefresh);
        mainHandler.removeCallbacks(countdownTick);
        updateCountdowns();
        mainHandler.postDelayed(countdownTick, COUNTDOWN_INTERVAL_MS);
        mainHandler.postDelayed(autoRefresh, REFRESH_INTERVAL_MS);
        // Fetch fresh data after returning from another app / screen.
        if (hasResumedBefore && !busy && (!realMode || isValidRealUrl(currentServerUrl()))) refresh();
        hasResumedBefore = true;
    }

    @Override
    protected void onPause() {
        active = false;
        mainHandler.removeCallbacks(autoRefresh);
        mainHandler.removeCallbacks(countdownTick);
        super.onPause();
    }

    private String currentServerUrl() {
        return serverUrl.getText().toString().trim().replaceAll("/+$", "");
    }

    private boolean isValidRealUrl(String url) {
        if (!url.startsWith("https://")) return false;
        try {
            java.net.URL parsed = new java.net.URL(url);
            return parsed.getHost() != null && !parsed.getHost().isEmpty()
                    && (parsed.getPath().isEmpty() || parsed.getPath().equals("/"))
                    && (parsed.getQuery() == null) && (parsed.getRef() == null)
                    && parsed.getUserInfo() == null;
        } catch (Exception ignored) { return false; }
    }

    private void switchMode() {
        if (busy) return;
        realMode = !realMode;
        settings.edit().putBoolean("real_mode", realMode).apply();
        machines = new ArrayList<>(); // Never show demo data in Real Mode.
        updateModeUi();
        render();
        if (realMode) {
            if (isValidRealUrl(currentServerUrl())) refresh();
            else setMessage("Real Mode selected. Enter your actual HTTPS API address and tap Connect.");
        } else {
            refresh();
        }
    }

    private void updateModeUi() {
        modeLabel.setText(realMode ? "Mode: Real (external API)" : "Mode: Demo (sample data)");
        modeLabel.setTextColor(realMode ? GREEN : AMBER);
        modeButton.setText(realMode ? "Switch to Demo Mode" : "Switch to Real Mode");
        realSettings.setVisibility(realMode ? View.VISIBLE : View.GONE);
        resetButton.setVisibility(realMode ? View.GONE : View.VISIBLE);
    }

    private void connectToRealApi() {
        if (busy) return;
        String url = currentServerUrl();
        if (!isValidRealUrl(url)) {
            setMessage("Enter an HTTPS base URL such as https://laundry.example.edu/api (no /machines suffix).");
            return;
        }
        settings.edit().putString("real_url", url).apply();
        refresh();
    }

    private void refresh() {
        if (busy) return;
        if (realMode) {
            String url = currentServerUrl();
            if (!isValidRealUrl(url)) {
                machines = new ArrayList<>();
                render();
                setMessage("Real Mode needs a working HTTPS base URL. No live data loaded.");
                return;
            }
            settings.edit().putString("real_url", url).apply();
            work("Fetching live machine status…", () -> ApiClient.loadMachines(url), updated -> {
                machines = updated;
                render();
                setMessage("Live data loaded from " + url + ". This mode is read-only.");
            });
        } else {
            work("Reading built-in Java demo API…", () -> {
                ((LaundryApplication) getApplication()).getLocalApi().awaitStarted();
                return ApiClient.loadMachines(DemoApiServer.URL);
            }, updated -> {
                machines = updated;
                render();
                setMessage("Java demo API is running at " + DemoApiServer.URL
                        + ". Tap a machine to simulate its status.");
            });
        }
    }

    private void confirmReset() {
        if (busy || realMode) return;
        new AlertDialog.Builder(this)
                .setTitle("Reset demo statuses?")
                .setMessage("Restore the original six simulated machine statuses?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (dialog, which) ->
                        work("Resetting demo data…", () -> {
                            ApiClient.reset(DemoApiServer.URL);
                            return ApiClient.loadMachines(DemoApiServer.URL);
                        }, updated -> {
                            machines = updated;
                            render();
                            setMessage("Demo machines reset on the built-in Java API.");
                        })).show();
    }

    private void chooseStatus(Machine machine) {
        if (busy) return;
        if (realMode) {
            new AlertDialog.Builder(this)
                    .setTitle("Real Mode is read-only")
                    .setMessage("Machine statuses come from the real API. Use Demo Mode to simulate changes.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        String[] choices = {"Available", "In Use (1-minute test)",
                "In Use (30 minutes)", "Out of Service"};
        new AlertDialog.Builder(this)
                .setTitle("Simulate " + machine.name)
                .setItems(choices, (dialog, choice) -> {
                    String status = choice == 0 ? Machine.AVAILABLE
                            : (choice == 1 || choice == 2) ? Machine.IN_USE : Machine.OUT_OF_SERVICE;
                    int minutes = choice == 1 ? 1 : choice == 2 ? 30 : 0;
                    work("Updating the Java demo API…", () -> {
                        ApiClient.changeStatus(DemoApiServer.URL, machine, status, minutes);
                        return ApiClient.loadMachines(DemoApiServer.URL);
                    }, updated -> {
                        machines = updated;
                        render();
                        setMessage(machine.name + " was updated through the Java HTTP API.");
                    });
                }).show();
    }

    private interface Task<T> { T execute() throws Exception; }
    private interface Result<T> { void accept(T value); }

    private <T> void work(String waiting, Task<T> job, Result<T> success) {
        if (busy) return;
        busy = true;
        refreshButton.setEnabled(false);
        resetButton.setEnabled(false);
        connectButton.setEnabled(false);
        modeButton.setEnabled(false);
        setMessage(waiting);
        executor.execute(() -> {
            T value = null;
            Exception failure = null;
            try { value = job.execute(); }
            catch (Exception error) { failure = error; }
            final T result = value;
            final Exception error = failure;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                busy = false;
                refreshButton.setEnabled(true);
                resetButton.setEnabled(true);
                connectButton.setEnabled(true);
                modeButton.setEnabled(true);
                if (error != null) {
                    if (realMode) {
                        machines = new ArrayList<>(); // Never display stale/demo statuses as live.
                        render();
                    }
                    setMessage((realMode ? "Real API unavailable: " : "Demo API error: ")
                            + error.getMessage());
                    Toast.makeText(this, "Unable to refresh machines", Toast.LENGTH_SHORT).show();
                } else {
                    success.accept(result);
                }
            });
        });
    }

    private void render() {
        washerContainer.removeAllViews();
        dryerContainer.removeAllViews();
        statusViews.clear();

        if (machines.isEmpty()) {
            summary.setText(realMode ? "No live machines loaded" : "Loading demo machines...");
            lastUpdated.setText(realMode ? "External API not connected" : "Starting Java demo API");
            TextView empty = new TextView(this);
            empty.setText(realMode ? "Enter a real HTTPS API to see live data."
                    : "Preparing the sample machines...");
            washerContainer.addView(empty);
            return;
        }

        int available = 0;
        for (Machine machine : machines) {
            if (Machine.AVAILABLE.equals(machine.status)) available++;
        }
        summary.setText("Available: " + available + " / " + machines.size());
        lastUpdated.setText("Last checked: "
                + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));

        for (Machine machine : machines) {
            LinearLayout target = "dryer".equalsIgnoreCase(machine.type)
                    ? dryerContainer : washerContainer;

            // Plain Android XML widgets; edit item_machine.xml in the Design tab.
            View row = getLayoutInflater().inflate(R.layout.item_machine, target, false);
            TextView name = row.findViewById(R.id.machineName);
            TextView status = row.findViewById(R.id.machineStatus);
            TextView timer = row.findViewById(R.id.machineTimer);
            Button action = row.findViewById(R.id.machineActionButton);
            name.setText(machine.name);
            action.setVisibility(realMode ? View.GONE : View.VISIBLE);
            action.setOnClickListener(v -> chooseStatus(machine));
            target.addView(row);
            statusViews.add(new StatusView(machine, row, status, timer));
        }
        updateCountdowns();
    }

    /** Update text every second, using the exact deadline returned by the Java API. */
    private void updateCountdowns() {
        if (statusViews.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean demoCycleFinished = false;
        int available = 0;

        for (StatusView view : statusViews) {
            Machine machine = view.machine;
            if (!realMode && Machine.IN_USE.equals(machine.status)
                    && machine.endTimeMs > 0L && now >= machine.endTimeMs) {
                machine.status = Machine.AVAILABLE;
                machine.remainingMinutes = 0;
                machine.endTimeMs = 0L;
                demoCycleFinished = true;
            }
            if (Machine.AVAILABLE.equals(machine.status)) available++;

            String statusName;
            int color;
            if (Machine.AVAILABLE.equals(machine.status)) {
                statusName = "Available";
                color = GREEN;
            } else if (Machine.IN_USE.equals(machine.status)) {
                statusName = "In Use";
                color = AMBER;
            } else {
                statusName = "Out of Service";
                color = RED;
            }
            view.status.setText("Status: " + statusName);
            view.status.setTextColor(color);

            String countdown = "Time left: --";
            if (Machine.IN_USE.equals(machine.status)) {
                if (machine.endTimeMs > 0L) {
                    long seconds = Math.max(0L,
                            (machine.endTimeMs - now + 999L) / 1000L);
                    if (realMode && seconds == 0L) {
                        countdown = "Time left: awaiting server update";
                    } else {
                        countdown = String.format(Locale.US, "Time left: %02d:%02d",
                                seconds / 60L, seconds % 60L);
                    }
                } else {
                    countdown = "Time left: not provided";
                }
            }
            view.timer.setText(countdown);
            view.row.setContentDescription(machine.name + ", " + statusName + ", " + countdown);
        }
        summary.setText("Available: " + available + " / " + machines.size());
        if (demoCycleFinished && !busy) refresh();
    }

    private void setMessage(String text) { message.setText(text); }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(autoRefresh);
        mainHandler.removeCallbacks(countdownTick);
        executor.shutdownNow();
        super.onDestroy();
    }
}
