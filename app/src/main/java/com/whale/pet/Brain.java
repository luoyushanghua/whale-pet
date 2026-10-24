package com.whale.pet;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * 大脑：本地 Ollama 或 OpenAI 兼容 API（DeepSeek 等）。同一个接口，换后端只改配置。
 */
public class Brain {

    public interface Callback {
        void ok(String text);

        void fail(String message);

        default void sources(java.util.List<Search.Result> list) {
        }
    }

    /** 本轮检索到的资料（拼进 system）；联网失败时为 null，不阻塞回答 */
    private static volatile String lastSearchPrompt = null;

    public static void ask(Context ctx, Memory memory, List<String[]> history,
                           String userText, Callback cb) {
        new Thread(() -> {
            try {
                Prefs prefs = new Prefs(ctx);
                lastSearchPrompt = null;
                if (Search.needs(userText)) {
                    java.util.List<Search.Result> found = Search.run(userText);
                    if (!found.isEmpty()) {
                        lastSearchPrompt = Search.asPrompt(found);
                        cb.sources(found);
                    }
                }
                String text = "api".equals(prefs.brainMode())
                        ? askApi(prefs, memory, history, userText)
                        : askOllama(prefs, memory, history, userText);
                cb.ok(text);
            } catch (Exception e) {
                cb.fail(e.getMessage() == null ? "未知错误" : e.getMessage());
            }
        }).start();
    }

    private static JSONArray messages(Memory memory, List<String[]> history, String userText)
            throws Exception {
        JSONArray arr = new JSONArray();
        String sys = memory.systemPrompt();
        if (lastSearchPrompt != null) sys = sys + "\n" + lastSearchPrompt;
        arr.put(new JSONObject().put("role", "system").put("content", sys));
        for (String[] t : history) {
            arr.put(new JSONObject()
                    .put("role", "u".equals(t[0]) ? "user" : "assistant")
                    .put("content", t[1]));
        }
        arr.put(new JSONObject().put("role", "user").put("content", userText));
        return arr;
    }

    private static String askOllama(Prefs prefs, Memory memory, List<String[]> history,
                                    String userText) throws Exception {
        String base = prefs.brainHost();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        JSONObject body = new JSONObject();
        body.put("model", prefs.brainModel());
        body.put("messages", messages(memory, history, userText));
        body.put("stream", false);
        JSONObject resp = post(base + "/api/chat", body, null);
        return resp.getJSONObject("message").getString("content").trim();
    }

    private static String askApi(Prefs prefs, Memory memory, List<String[]> history,
                                 String userText) throws Exception {
        String base = prefs.apiBase();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        JSONObject body = new JSONObject();
        body.put("model", prefs.apiModel());
        body.put("messages", messages(memory, history, userText));
        body.put("temperature", 0.8);
        JSONObject resp = post(base + "/chat/completions", body, prefs.apiKey());
        JSONArray choices = resp.getJSONArray("choices");
        return choices.getJSONObject(0).getJSONObject("message")
                .getString("content").trim();
    }

    private static JSONObject post(String url, JSONObject body, String key) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(60000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        if (key != null && !key.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + key);
        }
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes("UTF-8"));
        }
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IllegalStateException("接口返回 " + code);
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return new JSONObject(sb.toString());
    }
}
