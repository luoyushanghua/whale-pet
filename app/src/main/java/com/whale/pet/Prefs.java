package com.whale.pet;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** 全部本地设置与陪伴数据。 */
public class Prefs {

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences("pet", Context.MODE_PRIVATE);
    }

    public boolean agreed() {
        return sp.getBoolean("agreed", false);
    }

    public void setAgreed(boolean v) {
        sp.edit().putBoolean("agreed", v).apply();
    }

    public boolean interactive() {
        return sp.getBoolean("interactive", false);
    }

    public void setInteractive(boolean v) {
        sp.edit().putBoolean("interactive", v).apply();
    }

    public boolean gameAway() {
        return sp.getBoolean("game_away", true);
    }

    public boolean clickThrough() {
        return sp.getBoolean("click_through", false);
    }

    public void setClickThrough(boolean v) {
        sp.edit().putBoolean("click_through", v).apply();
    }

    public boolean edgeSnap() {
        return sp.getBoolean("edge_snap", true);
    }

    public void setEdgeSnap(boolean v) {
        sp.edit().putBoolean("edge_snap", v).apply();
    }

    public boolean autoStart() {
        return sp.getBoolean("auto_start", false);
    }

    public void setAutoStart(boolean v) {
        sp.edit().putBoolean("auto_start", v).apply();
    }

    public boolean muted() {
        return sp.getBoolean("muted", false);
    }

    public void setMuted(boolean v) {
        sp.edit().putBoolean("muted", v).apply();
    }

    public boolean wasRunning() {
        return sp.getBoolean("was_running", false);
    }

    public void setWasRunning(boolean v) {
        sp.edit().putBoolean("was_running", v).apply();
    }

    public int sizePct() {
        return sp.getInt("size_pct", 55);
    }

    public void setSizePct(int v) {
        sp.edit().putInt("size_pct", v).apply();
    }

    public int alphaPct() {
        return sp.getInt("alpha_pct", 100);
    }

    public void setAlphaPct(int v) {
        sp.edit().putInt("alpha_pct", v).apply();
    }

    public int ambientSec() {
        return sp.getInt("ambient_sec", 28);
    }

    public void setAmbientSec(int v) {
        sp.edit().putInt("ambient_sec", v).apply();
    }

    public int runSpeed() {
        return sp.getInt("run_speed", 380);
    }

    public void setRunSpeed(int v) {
        sp.edit().putInt("run_speed", v).apply();
    }

    public int sharpen() {
        return sp.getInt("sharpen", 35);
    }

    public void setSharpen(int v) {
        sp.edit().putInt("sharpen", v).apply();
    }

    public Set<String> getAwayPackages() {
        return new HashSet<>(sp.getStringSet("away_packages", new HashSet<>()));
    }

    public void setAwayPackages(Set<String> set) {
        sp.edit().putStringSet("away_packages", new HashSet<>(set)).apply();
    }

    public int mood() {
        int m = sp.getInt("mood", 72);
        long last = sp.getLong("mood_at", 0);
        if (last > 0) {
            long hours = (System.currentTimeMillis() - last) / 3600000L;
            m -= (int) Math.min(40, hours * 3);
        }
        return Math.max(0, Math.min(100, m));
    }

    public void setMood(int m) {
        sp.edit().putInt("mood", Math.max(0, Math.min(100, m)))
                .putLong("mood_at", System.currentTimeMillis()).apply();
    }

    public void bumpMood(int delta) {
        setMood(mood() + delta);
    }

    public long firstDay() {
        long v = sp.getLong("first_day", 0);
        if (v == 0) {
            v = java.time.LocalDate.now().toEpochDay();
            sp.edit().putLong("first_day", v).apply();
        }
        return v;
    }

    public int daysTogether() {
        long d = java.time.LocalDate.now().toEpochDay() - firstDay() + 1;
        return (int) Math.max(1, d);
    }

    public int interactions() {
        return sp.getInt("interactions", 0);
    }

    public void bumpInteraction() {
        sp.edit().putInt("interactions", interactions() + 1).apply();
    }

    public long lastInteraction() {
        return sp.getLong("last_interaction", 0);
    }

    public void touchInteraction() {
        sp.edit().putLong("last_interaction", System.currentTimeMillis()).apply();
    }

    public String flag(String key) {
        return sp.getString(key, "");
    }

    public void setFlag(String key, String value) {
        sp.edit().putString(key, value).apply();
    }

    private static String today() {
        return java.time.LocalDate.now().toString();
    }

    public Set<String> checkinDates() {
        return new HashSet<>(sp.getStringSet("checkin_dates", new HashSet<>()));
    }

    public boolean checkedToday() {
        return checkinDates().contains(today());
    }

    public void checkin() {
        Set<String> s = checkinDates();
        s.add(today());
        sp.edit().putStringSet("checkin_dates", s).apply();
    }

    public int totalCheckins() {
        return checkinDates().size();
    }

    public int streakDays() {
        Set<String> s = checkinDates();
        java.time.LocalDate d = java.time.LocalDate.now();
        int n = 0;
        if (!s.contains(d.toString())) d = d.minusDays(1);
        while (s.contains(d.toString())) {
            n++;
            d = d.minusDays(1);
        }
        return n;
    }

    public void bumpCounter(String key) {
        sp.edit().putInt(key, sp.getInt(key, 0) + 1).apply();
    }

    public int counter(String key) {
        return sp.getInt(key, 0);
    }

    public int fps() {
        return sp.getInt("fps", 60);
    }

    public void setFps(int v) {
        sp.edit().putInt("fps", v).apply();
    }

    public int usageWarnCount() {
        return sp.getInt("usage_warn", 0);
    }

    public void bumpUsageWarn() {
        sp.edit().putInt("usage_warn", usageWarnCount() + 1).apply();
    }

    public int chatCount() {
        return sp.getInt("chat_count", 0);
    }

    public void bumpChat() {
        sp.edit().putInt("chat_count", chatCount() + 1).apply();
    }

    public String brainMode() {
        return sp.getString("brain_mode", "api");
    }

    public void setBrainMode(String v) {
        sp.edit().putString("brain_mode", v).apply();
    }

    public String brainHost() {
        return sp.getString("brain_host", "http://127.0.0.1:11434");
    }

    public void setBrainHost(String v) {
        sp.edit().putString("brain_host", v).apply();
    }

    public String brainModel() {
        return sp.getString("brain_model", "qwen3.5:9b");
    }

    public void setBrainModel(String v) {
        sp.edit().putString("brain_model", v).apply();
    }

    public String apiBase() {
        return sp.getString("api_base", "https://api.deepseek.com");
    }

    public void setApiBase(String v) {
        sp.edit().putString("api_base", v).apply();
    }

    public String apiKey() {
        return sp.getString("api_key", "");
    }

    public void setApiKey(String v) {
        sp.edit().putString("api_key", v).apply();
    }

    public String apiModel() {
        return sp.getString("api_model", "deepseek-chat");
    }

    public void setApiModel(String v) {
        sp.edit().putString("api_model", v).apply();
    }
}
