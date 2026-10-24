package com.whale.pet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 通知栏快捷操作：收回 / 静音 / 打开设置 */
public class NotificationActionReceiver extends BroadcastReceiver {

    public static final String ACTION_STOP = "com.whale.pet.action.STOP";
    public static final String ACTION_MUTE = "com.whale.pet.action.MUTE";
    public static final String ACTION_SETTINGS = "com.whale.pet.action.SETTINGS";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        Prefs prefs = new Prefs(context);
        switch (action) {
            case ACTION_STOP:
                prefs.setWasRunning(false);
                context.stopService(new Intent(context, PetService.class));
                break;
            case ACTION_MUTE:
                prefs.setMuted(!prefs.muted());
                context.startService(new Intent(context, PetService.class)
                        .setAction(PetService.ACTION_RELOAD));
                break;
            case ACTION_SETTINGS:
                Intent ui = new Intent(context, MainActivity.class);
                ui.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(ui);
                break;
        }
    }
}
