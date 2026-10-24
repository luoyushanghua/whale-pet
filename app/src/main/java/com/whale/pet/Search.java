package com.whale.pet;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/** 免费检索：维基百科 + DuckDuckGo Instant Answer。失败就返回空，不抛错。 */
public class Search {

    public static class Result {
        public final String title;
        public final String snippet;
        public final String url;

        Result(String title, String snippet, String url) {
            this.title = title;
            this.snippet = snippet;
            this.url = url;
        }
    }

    private static final String[] TRIGGERS = {
            "查", "搜", "是什么", "什么是", "最新", "多少", "哪年", "谁", "为什么", "怎么",
            "search", "what is", "who is", "latest", "how much", "why"
    };

    public static boolean needs(String text) {
        if (text == null || text.length() < 2) return false;
        for (String t : TRIGGERS) if (text.contains(t)) return true;
        return false;
    }

    public static List<Result> run(String query) {
        List<Result> out = new ArrayList<>();
        try {
            out.addAll(wiki(query));
        } catch (Exception ignored) {
        }
        if (out.size() < 3) {
            try {
                out.addAll(ddg(query));
            } catch (Exception ignored) {
            }
        }
        return out.size() > 3 ? out.subList(0, 3) : out;
    }

    private static List<Result> wiki(String q) throws Exception {
        List<Result> out = new ArrayList<>();
        String url = "https://zh.wikipedia.org/w/api.php?action=query&list=search&format=json"
                + "&utf8=1&srlimit=2&srsearch=" + URLEncoder.encode(q, "UTF-8");
        JSONObject o = get(url);
        JSONArray arr = o.getJSONObject("query").getJSONArray("search");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject it = arr.getJSONObject(i);
            String title = it.getString("title");
            String snip = it.optString("snippet", "").replaceAll("<[^>]+>", "");
            out.add(new Result(title, snip,
                    "https://zh.wikipedia.org/wiki/" + URLEncoder.encode(title, "UTF-8")));
        }
        return out;
    }

    private static List<Result> ddg(String q) throws Exception {
        List<Result> out = new ArrayList<>();
        String url = "https://api.duckduckgo.com/?format=json&no_html=1&q="
                + URLEncoder.encode(q, "UTF-8");
        JSONObject o = get(url);
        String abs = o.optString("AbstractText", "");
        if (!abs.isEmpty()) {
            out.add(new Result(o.optString("Heading", q), abs,
                    o.optString("AbstractURL", "https://duckduckgo.com/?q="
                            + URLEncoder.encode(q, "UTF-8"))));
        }
        JSONArray topics = o.optJSONArray("RelatedTopics");
        if (topics != null) {
            for (int i = 0; i < topics.length() && out.size() < 3; i++) {
                JSONObject t = topics.optJSONObject(i);
                if (t == null) continue;
                String txt = t.optString("Text", "");
                String first = t.optString("FirstURL", "");
                if (!txt.isEmpty() && !first.isEmpty()) {
                    out.add(new Result(txt.split(" - ")[0], txt, first));
                }
            }
        }
        return out;
    }

    private static JSONObject get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(4000);
        conn.setRequestProperty("User-Agent", "whale-pet/1.0 (personal use)");
        int code = conn.getResponseCode();
        if (code != 200) throw new IllegalStateException("HTTP " + code);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return new JSONObject(sb.toString());
    }

    public static String asPrompt(List<Result> list) {
        if (list == null || list.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("以下是刚检索到的资料，请据此回答，"
                + "并在句末用 [1][2] 标注来源；资料里没有的就直说不知道，不要编：\n");
        for (int i = 0; i < list.size(); i++) {
            Result r = list.get(i);
            sb.append("[").append(i + 1).append("] ").append(r.title).append("：")
                    .append(r.snippet).append("（").append(r.url).append("）\n");
        }
        return sb.toString();
    }
}
