package com.whale.pet;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** 读通知，让她知道"有人找你"——需要用户在系统里授权通知使用权。 */
public class PetNotificationListener extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        if (getPackageName().equals(sbn.getPackageName())) return;

        Notification n = sbn.getNotification();
        Bundle extras = n.extras;
        if (extras == null) return;

        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (title == null && text == null) return;

        String app = appLabel(sbn.getPackageName());
        String sender = (title == null ? "" : title.toString()).trim();
        String body = (text == null ? "" : text.toString()).trim();

        // 念短句：谁找你啦 / 谁@你啦 / 群里有人喊你
        String line;
        boolean atMe = body.contains("[有人@我]") || body.contains("@你")
                || body.contains("@我") || body.contains("有人@");
        boolean group = sender.contains("群") || body.contains("群聊");
        if (sender.isEmpty()) {
            line = app + " 来消息啦";
        } else if (atMe) {
            line = sender + " @你啦";
        } else if (group) {
            line = "群里有人找你：" + sender;
        } else {
            line = sender + " 找你啦";
        }
        // 正文只留很短一截，念起来才像"说话"
        String snippet = body.replace("[有人@我]", "").replaceAll("\\s+", " ").trim();
        if (snippet.length() > 18) snippet = snippet.substring(0, 18) + "…";
        if (!snippet.isEmpty() && !atMe) line = line + "：" + snippet;

        try {
            sendBroadcast(new Intent(PetService.ACTION_NOTIFY)
                    .setPackage(getPackageName())
                    .putExtra("text", line));
        } catch (Exception ignored) {
        }
    }

    private String appLabel(String pkg) {
        try {
            return getPackageManager()
                    .getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0))
                    .toString();
        } catch (Exception e) {
            return "有人";
        }
    }
}
