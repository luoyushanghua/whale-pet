package com.whale.pet;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

/**
 * 存档：把人设 + 记忆打包成一段可发送的文本（QQ/邮件/备忘录都行），也能读回来。
 * 另外每天在 files/archives/ 留一份本地快照，删不掉也丢不了。
 */
public class Archive {

    private static final int KEEP = 7;

    public static String exportText(Memory m) {
        try {
            JSONObject o = new JSONObject();
            o.put("app", "whale-pet");
            o.put("v", 1);
            o.put("exported", System.currentTimeMillis());
            o.put("name", m.name);
            o.put("user_call", m.userCall);
            o.put("persona", m.persona);
            o.put("first_seen", m.firstSeen);
            o.put("facts", new JSONArray(m.facts));
            JSONArray turns = new JSONArray();
            for (String[] t : m.turns) {
                turns.put(new JSONObject().put("r", t[0]).put("t", t[1]));
            }
            o.put("turns", turns);
            return o.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 返回 null 表示格式不对 */
    public static Memory parse(String text) {
        try {
            JSONObject o = new JSONObject(text.trim());
            if (!"whale-pet".equals(o.optString("app"))) return null;
            Memory m = new Memory();
            m.name = o.optString("name", m.name);
            m.userCall = o.optString("user_call", m.userCall);
            m.persona = o.optString("persona", m.persona);
            m.firstSeen = o.optLong("first_seen", System.currentTimeMillis());
            JSONArray fa = o.optJSONArray("facts");
            if (fa != null) for (int i = 0; i < fa.length(); i++) m.facts.add(fa.getString(i));
            JSONArray ta = o.optJSONArray("turns");
            if (ta != null) {
                for (int i = 0; i < ta.length(); i++) {
                    JSONObject t = ta.getJSONObject(i);
                    m.turns.add(new String[]{t.optString("r"), t.optString("t")});
                }
            }
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 以「文件」形式分享：聊天工具不会截断，邮箱能当附件，QQ 能直接发文件。
     * 文件落在 files/export，通过 ExportProvider 借给系统，不需要存储权限。
     */
    public static void share(Context ctx, Memory m) {
        try {
            File dir = new File(ctx.getFilesDir(), "export");
            if (!dir.exists()) dir.mkdirs();
            String name = "whale-memory-" + m.daysTogether() + "d-"
                    + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.CHINA)
                    .format(new java.util.Date()) + ".json";
            File f = new File(dir, name);
            java.nio.file.Files.write(f.toPath(),
                    exportText(m).getBytes("UTF-8"));

            Uri uri = new Uri.Builder()
                    .scheme("content")
                    .authority(ExportProvider.AUTHORITY)
                    .appendPath(name)
                    .build();

            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.putExtra(Intent.EXTRA_SUBJECT, "鲸鱼娘的存档 · 第 " + m.daysTogether() + " 天");
            i.putExtra(Intent.EXTRA_TEXT, "这是我的记忆存档，换手机时用「从存档恢复」把我接回来～");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(i, "把存档发给谁？");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(chooser);
        } catch (Exception ignored) {
        }
    }

    /** 从文件导入用：给系统文件选择器读到的 URI 解析 */
    public static Memory importFrom(Context ctx, Uri uri) {
        try (java.io.InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return parse(bos.toString("UTF-8"));
        } catch (Exception e) {
            return null;
        }
    }

    /** 每天留一份本地快照，最多 7 份，存在 files/archives（不是缓存） */
    public static void autoSnapshot(Context ctx, Memory m) {
        try {
            File dir = new File(ctx.getFilesDir(), "archives");
            if (!dir.exists()) dir.mkdirs();
            String day = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.CHINA)
                    .format(new java.util.Date());
            File f = new File(dir, "memory-" + day + ".json");
            if (f.exists()) return;
            java.nio.file.Files.write(f.toPath(),
                    exportText(m).getBytes("UTF-8"));
            File[] all = dir.listFiles();
            if (all != null && all.length > KEEP) {
                java.util.Arrays.sort(all, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                for (int i = 0; i < all.length - KEEP; i++) all[i].delete();
            }
        } catch (Exception ignored) {
        }
    }

    public static int snapshotCount(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "archives");
        File[] all = dir.listFiles();
        return all == null ? 0 : all.length;
    }
}
