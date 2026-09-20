package com.yagay.yenv.hook;

import android.util.Log;

import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedModule;

public final class YEnvModule extends XposedModule {
    private static final String TAG = "YEnv";
    private final ConcurrentHashMap<String, EnvironmentHooks> installed = new ConcurrentHashMap<>();

    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "Loaded in " + param.getProcessName());
    }

    @Override public void onPackageReady(PackageReadyParam param) {
        if (!param.isFirstPackage()) return;
        String pkg = param.getPackageName();
        if (pkg == null || pkg.equals("com.yagay.yenv")) return;
        installed.computeIfAbsent(pkg, ignored -> {
            try {
                EnvironmentHooks hooks = new EnvironmentHooks(this, pkg);
                hooks.install();
                log(Log.INFO, TAG, "Hooks ready for " + pkg);
                return hooks;
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Failed to initialize " + pkg, t);
                return null;
            }
        });
    }
}
