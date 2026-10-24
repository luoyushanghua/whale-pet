package com.whale.pet;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** 桌面小组件：她的小头像 + 第 N 天，点一下打开 App。 */
public class WidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, build(context));
    }

    static void refresh(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, WidgetProvider.class));
            for (int id : ids) m.updateAppWidget(id, build(c));
        } catch (Exception ignored) {
        }
    }

    private static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_pet);
        Memory mem = Memory.load(c);
        Prefs p = new Prefs(c);
        v.setTextViewText(R.id.w_days, "第 " + mem.daysTogether() + " 天");
        v.setTextViewText(R.id.w_sub, "连续签到 " + p.streakDays() + " 天 · 心情 "
                + (p.mood() >= 70 ? "好" : p.mood() >= 40 ? "一般" : "低落"));
        Intent i = new Intent(c, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(c, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.w_avatar, pi);
        v.setOnClickPendingIntent(R.id.w_days, pi);
        return v;
    }
}
