package com.yagay.yenv;

import android.app.Application;
import android.content.SharedPreferences;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class YEnvApp extends Application implements XposedServiceHelper.OnServiceListener {
    public interface ServiceObserver { void onServiceChanged(); }
    private static volatile XposedService service;
    private static final List<ServiceObserver> observers = new CopyOnWriteArrayList<>();

    @Override public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }

    @Override public void onServiceBind(XposedService s) {
        service = s;
        notifyObservers();
    }

    @Override public void onServiceDied(XposedService s) {
        if (service == s) service = null;
        notifyObservers();
    }

    public static XposedService getService() { return service; }
    public static SharedPreferences remotePrefs() {
        XposedService s = service;
        if (s == null) return null;
        try { return s.getRemotePreferences("yenv"); }
        catch (Throwable ignored) { return null; }
    }
    public static void addObserver(ServiceObserver o) { observers.add(o); }
    public static void removeObserver(ServiceObserver o) { observers.remove(o); }
    private static void notifyObservers() { for (ServiceObserver o : observers) o.onServiceChanged(); }
}
