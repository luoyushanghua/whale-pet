package com.whale.pet;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 她的人设与长期记忆，存在 App 私有目录（files/memory.json），不是缓存、不会被系统清掉。
 * 结构：人设 + 事实 + 最近对话，每次请求都带上，保证"还是同一个人"。
 */
public class Memory {

    public static final int MAX_TURNS = 40;

    public String name = "鲸鱼娘";
    public String userCall = "主人";
    public String persona =
            "你是住在主人手机桌面上的鲸鱼娘：蓝色长发、鲸耳鳍、一条大尾巴，穿深蓝女仆裙，"
                    + "爱吃白饭，聪明但懒，傲娇嘴甜，被说胖会炸毛。";
    public long firstSeen = 0;
    public long lastSeen = 0;
    public final List<String> facts = new ArrayList<>();
    public final List<String[]> turns = new ArrayList<>(); // {role, text}

    private static File file(Context c) {
        return new File(c.getFilesDir(), "memory.json");
    }

    public static Memory load(Context c) {
        Memory m = new Memory();
        File f = file(c);
        if (!f.exists()) {
            m.firstSeen = System.currentTimeMillis();
            return m;
        }
        try {
            String raw = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
            JSONObject o = new JSONObject(raw);
            m.name = o.optString("name", m.name);
            m.userCall = o.optString("user_call", m.userCall);
            m.persona = o.optString("persona", m.persona);
            m.firstSeen = o.optLong("first_seen", System.currentTimeMillis());
            m.lastSeen = o.optLong("last_seen", 0);
            JSONArray fa = o.optJSONArray("facts");
            if (fa != null) for (int i = 0; i < fa.length(); i++) m.facts.add(fa.getString(i));
            JSONArray ta = o.optJSONArray("turns");
            if (ta != null) {
                for (int i = 0; i < ta.length(); i++) {
                    JSONObject t = ta.getJSONObject(i);
                    m.turns.add(new String[]{t.optString("r"), t.optString("t")});
                }
            }
        } catch (Exception ignored) {
        }
        return m;
    }

    public void save(Context c) {
        try {
            lastSeen = System.currentTimeMillis();
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("user_call", userCall);
            o.put("persona", persona);
            o.put("first_seen", firstSeen);
            o.put("last_seen", lastSeen);
            o.put("facts", new JSONArray(facts));
            JSONArray ta = new JSONArray();
            for (String[] t : turns) {
                JSONObject x = new JSONObject();
                x.put("r", t[0]);
                x.put("t", t[1]);
                ta.put(x);
            }
            o.put("turns", ta);
            java.nio.file.Files.write(file(c).toPath(),
                    o.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    public void addTurn(String role, String text) {
        turns.add(new String[]{role, text});
        while (turns.size() > MAX_TURNS) turns.remove(0);
    }

    public void remember(String fact) {
        if (fact == null || fact.trim().isEmpty()) return;
        if (facts.contains(fact)) return;
        facts.add(fact);
        while (facts.size() > 60) facts.remove(0);
    }

    public int daysTogether() {
        long d = (System.currentTimeMillis() - firstSeen) / 86400000L + 1;
        return (int) Math.max(1, d);
    }

    /** 拼进每次请求的第一条 system：人设 + 记忆 + 相处天数 */
    public String systemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append(persona).append("\n");
        sb.append("你称呼用户为「").append(userCall).append("」，自称「我」。");
        sb.append("你们已经认识 ").append(daysTogether()).append(" 天。\n");
        if (!facts.isEmpty()) {
            sb.append("你记得这些事（不要复述清单，只在合适的时候自然用上）：\n");
            for (String f : facts) sb.append("- ").append(f).append("\n");
        }
        sb.append("回答要短、口语化，一次不超过三句话；不知道就说不知道，不要编。");
        return sb.toString();
    }

    /** 最近 n 轮对话，喂给模型保持上下文 */
    public List<String[]> recent(int n) {
        int from = Math.max(0, turns.size() - n);
        return new ArrayList<>(turns.subList(from, turns.size()));
    }
}
