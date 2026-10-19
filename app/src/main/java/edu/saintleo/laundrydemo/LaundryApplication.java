package edu.saintleo.laundrydemo;

import android.app.Application;

/** Starts the Java-based fake API automatically when the app process starts. */
public final class LaundryApplication extends Application {
    private DemoApiServer localApi;

    @Override
    public void onCreate() {
        super.onCreate();
        localApi = new DemoApiServer(new MachineStore(this));
        localApi.startAsync();
    }

    public DemoApiServer getLocalApi() { return localApi; }
}
