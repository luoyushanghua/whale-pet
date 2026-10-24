package com.whale.pet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 检查 GitHub 上有没有新版本。
 * 只用系统自带的 HttpURLConnection + org.json，不引入任何第三方库。
 */
public class Update {

    private static final String API =
            "https://api.github.com/repos/luoyushanghua/whale-pet/releases/latest";
    private static final String FALLBACK =
            "https://github.com/luoyushanghua/whale-pet/releases/latest";

    /** 静默检查：有新版才弹窗；无网、超时、解析失败都当作没更新 */
    public static void check(final Activity act) {
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String tag = null;
            String url = FALLBACK;
            String notes = null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(API).openConnection();
                c.setConnectTimeout(6000);
                c.setReadTimeout(6000);
                c.setRequestProperty("User-Agent", "whale-pet/" + BuildConfig.VERSION_NAME);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                if (c.getResponseCode() == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader r = new BufferedReader(
                            new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                        String line;
                        while ((line = r.readLine()) != null) sb.append(line);
                    }
                    org.json.JSONObject o = new org.json.JSONObject(sb.toString());
                    tag = o.optString("tag_name", null);
                    String html = o.optString("html_url", null);
                    if (html != null && html.startsWith("http")) url = html;
                    notes = o.optString("body", null);
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }

            if (tag == null || !isNewer(tag, BuildConfig.VERSION_NAME)) return;
            final String fTag = tag, fUrl = url;
            final String fNotes = shortNotes(notes);
            ui.post(() -> {
                if (act.isFinishing() || act.isDestroyed()) return;
                new AlertDialog.Builder(act)
                        .setTitle("有新版本 " + fTag)
                        .setMessage(fNotes == null || fNotes.isEmpty()
                                ? "要看看新版改了什么吗？"
                                : fNotes)
                        .setPositiveButton("去下载", (d, w) -> {
                            try {
                                act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fUrl)));
                            } catch (Exception ignored) {
                            }
                        })
                        .setNegativeButton("以后再说", null)
                        .show();
            });
        }, "update-check").start();
    }

    /** 版本号比较：v1.0.10 > v1.0.9，只看数字段 */
    static boolean isNewer(String remote, String local) {
        int[] a = parse(remote), b = parse(local);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static int[] parse(String v) {
        if (v == null) return new int[0];
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        // 去掉 -beta 之类的后缀
        int dash = s.indexOf('-');
        if (dash > 0) s = s.substring(0, dash);
        String[] parts = s.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (Exception e) {
                out[i] = 0;
            }
        }
        return out;
    }

    private static String shortNotes(String body) {
        if (body == null) return null;
        String s = body.replace("\r", "").trim();
        // 去掉 markdown 记号，截断到 300 字
        s = s.replaceAll("(?m)^#{1,6}\\s*", "").replaceAll("\\*\\*", "");
        if (s.length() > 300) s = s.substring(0, 300) + "…";
        return s;
    }
}
