package com.whale.pet;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

/** 前台 Activity 计数：自己 App 一打开就让她暂时躲开，免得挡住界面。 */
public class PetApp extends Application implements Application.ActivityLifecycleCallbacks {

    private int started;
    private static volatile boolean visible;

    public static boolean isUiVisible() {
        return visible;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityStarted(Activity activity) {
        started++;
        visible = true;
        if (started == 1) {
            send(PetService.ACTION_UI_OPEN);
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        started--;
        if (started <= 0) {
            started = 0;
            visible = false;
            send(PetService.ACTION_UI_CLOSE);
        }
    }

    private void send(String action) {
        if (!PetService.running) return;
        Intent i = new Intent(action).setPackage(getPackageName());
        try {
            // 后台时 startService 会被系统拒绝（Android 8+），改用广播，
            // 服务里已动态注册了 ACTION_UI_OPEN / ACTION_UI_CLOSE
            sendBroadcast(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
