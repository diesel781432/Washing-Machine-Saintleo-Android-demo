package edu.saintleo.laundrydemo;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/** Local data source; machine states survive app restarts on this device. */
public final class MachineStore {
    private static final String PREFS = "laundry_demo_v1";
    private final SharedPreferences prefs;

    public MachineStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<Machine> load() {
        List<Machine> list = defaults();
        for (Machine machine : list) {
            machine.status = prefs.getString(machine.id + ".status", machine.status);
            machine.remainingMinutes = prefs.getInt(machine.id + ".minutes", machine.remainingMinutes);
            long deadline = prefs.getLong(machine.id + ".deadline", 0L);
            if (Machine.IN_USE.equals(machine.status) && deadline == 0L
                    && machine.remainingMinutes > 0) {
                // First launch: seed an exact deadline for default in-use demo machines.
                // Keeping the deadline in preferences lets the countdown survive restarts.
                deadline = System.currentTimeMillis() + machine.remainingMinutes * 60000L;
                prefs.edit().putLong(machine.id + ".deadline", deadline).apply();
            }
            machine.endTimeMs = deadline;
            if (Machine.IN_USE.equals(machine.status) && deadline > 0L) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    machine.status = Machine.AVAILABLE;
                    machine.remainingMinutes = 0;
                    machine.endTimeMs = 0L;
                    prefs.edit().putString(machine.id + ".status", Machine.AVAILABLE)
                            .putInt(machine.id + ".minutes", 0)
                            .remove(machine.id + ".deadline").apply();
                } else {
                    machine.remainingMinutes = (int) Math.ceil(remaining / 60000.0);
                }
            }
        }
        return list;
    }

    public synchronized void save(Machine machine) {
        SharedPreferences.Editor editor = prefs.edit()
                .putString(machine.id + ".status", machine.status)
                .putInt(machine.id + ".minutes", machine.remainingMinutes);
        if (Machine.IN_USE.equals(machine.status) && machine.remainingMinutes > 0) {
            machine.endTimeMs = System.currentTimeMillis() + machine.remainingMinutes * 60000L;
            editor.putLong(machine.id + ".deadline", machine.endTimeMs);
        } else {
            machine.endTimeMs = 0L;
            editor.remove(machine.id + ".deadline");
        }
        editor.apply();
    }

    public synchronized List<Machine> reset() {
        SharedPreferences.Editor editor = prefs.edit();
        for (Machine machine : defaults()) {
            editor.remove(machine.id + ".status");
            editor.remove(machine.id + ".minutes");
            editor.remove(machine.id + ".deadline");
        }
        editor.apply();
        return load();
    }

    public synchronized Machine update(String id, String status, int minutes) {
        if (!Machine.isValidStatus(status)) throw new IllegalArgumentException("Invalid status");
        if (minutes < 0 || minutes > 180) throw new IllegalArgumentException("Minutes must be 0 through 180");
        for (Machine machine : load()) {
            if (machine.id.equals(id)) {
                machine.status = status;
                machine.remainingMinutes = Machine.IN_USE.equals(status) ? minutes : 0;
                save(machine);
                return machine;
            }
        }
        return null;
    }

    private static List<Machine> defaults() {
        List<Machine> result = new ArrayList<>();
        result.add(new Machine("washer-1", "Washer 1", "washer", Machine.AVAILABLE, 0));
        result.add(new Machine("washer-2", "Washer 2", "washer", Machine.IN_USE, 24));
        result.add(new Machine("washer-3", "Washer 3", "washer", Machine.AVAILABLE, 0));
        result.add(new Machine("dryer-1", "Dryer 1", "dryer", Machine.AVAILABLE, 0));
        result.add(new Machine("dryer-2", "Dryer 2", "dryer", Machine.IN_USE, 38));
        result.add(new Machine("dryer-3", "Dryer 3", "dryer", Machine.AVAILABLE, 0));
        return result;
    }
}
