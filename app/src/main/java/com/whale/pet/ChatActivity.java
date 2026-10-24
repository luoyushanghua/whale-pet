package com.whale.pet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 聊天页：人设与记忆常驻（Memory），大脑可切本地 Ollama / OpenAI 兼容 API（Brain）。
 */
public class ChatActivity extends Activity {

    private LinearLayout list;
    private ScrollView scroll;
    private EditText input;
    private TextView subtitle;
    private Memory memory;
    private Prefs prefs;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        memory = Memory.load(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_app);
        root.addView(buildHeader());

        scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 18);
        list.setPadding(p, Ui.dp(this, 14), p, Ui.dp(this, 14));
        scroll.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(buildInputBar());
        setContentView(root);

        restoreHistory();
        maybeShowSetupHint();
        Archive.autoSnapshot(this, memory);
    }

    /** 还没配 key（或者本地没连过）就先递一张说明书，别让人对着一句话发呆 */
    private void maybeShowSetupHint() {
        boolean api = !"local".equals(prefs.brainMode());
        if (api && prefs.apiKey().isEmpty()) {
            addSetupCard("还差一个 API Key",
                    "点「去配置」选服务商、贴 Key，两分钟就好；她只把 Key 存在本机。");
        } else if (!api) {
            addSetupCard("连的是自己电脑",
                    "电脑要开着 Ollama，手机和电脑在同一 Wi-Fi。连不上就点「去配置」换成 API。");
        }
    }

    private void addSetupCard(String title, String body) {
        LinearLayout card = Ui.card(this);
        card.setBackgroundResource(R.drawable.card_bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 12);
        card.setLayoutParams(lp);

        card.addView(Ui.text(this, title, 12, 0xFF1E88C7));
        TextView d = Ui.text(this, body, 10, 0xFF5B7E99);
        LinearLayout.LayoutParams dLp = Ui.wrap();
        dLp.topMargin = Ui.dp(this, 3);
        card.addView(d, dLp);

        TextView go = Ui.text(this, "去配置 →", 11, 0xFF12A97A);
        go.setPadding(0, Ui.dp(this, 8), 0, 0);
        go.setOnClickListener(v -> startActivity(
                new Intent(this, ChatSettingsActivity.class)));
        card.addView(go);

        list.addView(card, 0);
        scrollDown();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSubtitle();
    }

    private void restoreHistory() {
        if (memory.turns.isEmpty()) {
            addHer("在呢。想聊什么？");
            addHer("我记性还行——你说过的事我都会记住，下次还认得你。");
        } else {
            int from = Math.max(0, memory.turns.size() - 10);
            for (int i = from; i < memory.turns.size(); i++) {
                String[] t = memory.turns.get(i);
                if ("u".equals(t[0])) addMe(t[1]);
                else addHer(t[1]);
            }
            addHer("（翻到上次聊到这儿）");
        }
    }

    private View buildHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 14);
        bar.setPadding(p, Ui.dp(this, 38), p, Ui.dp(this, 10));
        bar.setBackgroundColor(0xFFE9F6FF);

        TextView back = Ui.text(this, "‹", 26, 0xFF12405F);
        back.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 14), 0);
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.drawable.hero_crop);
        bar.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 42)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nLp.leftMargin = Ui.dp(this, 10);
        bar.addView(names, nLp);

        TextView name = Ui.text(this, memory.name, 16, 0xFF0E3552);
        name.setTypeface(getResources().getFont(R.font.ruantang));
        names.addView(name);

        subtitle = Ui.text(this, "", 10, 0xFF5B7E99);
        names.addView(subtitle);
        refreshSubtitle();

        WhaleBadge badge = new WhaleBadge(this);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                Ui.dp(this, 34), Ui.dp(this, 28));
        bLp.rightMargin = Ui.dp(this, 4);
        bar.addView(badge, bLp);

        TextView gear = Ui.text(this, "⚙", 18, 0xFF1E88C7);
        gear.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
        gear.setOnClickListener(v -> startActivity(
                new Intent(this, ChatSettingsActivity.class)));
        bar.addView(gear);

        return bar;
    }

    private void refreshSubtitle() {
        boolean local = "local".equals(prefs.brainMode());
        String brain = local ? "本地 " + prefs.brainModel()
                : "API " + prefs.apiModel();
        subtitle.setText("第 " + memory.daysTogether() + " 天 · " + brain);
    }

    private void addHer(String text) {
        TextView tv = Ui.text(this, text, 14, 0xFF10395A);
        tv.setBackgroundResource(R.drawable.chat_her);
        int p = Ui.dp(this, 14);
        tv.setPadding(p, Ui.dp(this, 10), p, Ui.dp(this, 10));
        tv.setMaxWidth(Math.round(getResources().getDisplayMetrics().widthPixels * 0.78f));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.START);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = Ui.dp(this, 8);
        row.addView(tv);
        list.addView(row, rowLp);
        scrollDown();
    }

    private void addMe(String text) {
        TextView tv = Ui.text(this, text, 14, 0xFF06202F);
        tv.setBackgroundResource(R.drawable.chat_me);
        int p = Ui.dp(this, 14);
        tv.setPadding(p, Ui.dp(this, 10), p, Ui.dp(this, 10));
        tv.setMaxWidth(Math.round(getResources().getDisplayMetrics().widthPixels * 0.78f));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = Ui.dp(this, 8);
        row.addView(tv);
        list.addView(row, rowLp);
        scrollDown();
    }

    private void addNote(String text) {
        TextView tv = Ui.text(this, text, 10, 0xFF5B7E99);
        int p = Ui.dp(this, 12);
        tv.setPadding(p, Ui.dp(this, 6), p, Ui.dp(this, 6));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.START);
        row.addView(tv);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 6);
        list.addView(row, lp);
        scrollDown();
    }

    private void scrollDown() {
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private View buildInputBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 14);
        bar.setPadding(p, Ui.dp(this, 8), p, Ui.dp(this, 18));

        input = new EditText(this);
        input.setHint("跟她说点什么…");
        input.setHintTextColor(0xFF7A97AD);
        input.setTextColor(0xFF0E3552);
        input.setTextSize(14);
        input.setBackgroundResource(R.drawable.input_bg);
        input.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        input.setMaxLines(3);
        bar.addView(input, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button send = new Button(this);
        send.setText("发送");
        send.setAllCaps(false);
        send.setTextSize(13);
        send.setTextColor(0xFF06202F);
        send.setTypeface(Typeface.DEFAULT_BOLD);
        send.setBackgroundResource(R.drawable.btn_primary);
        send.setStateListAnimator(null);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                Ui.dp(this, 66), Ui.dp(this, 42));
        sLp.leftMargin = Ui.dp(this, 10);
        send.setOnClickListener(v -> send());
        bar.addView(send, sLp);

        return bar;
    }

    private void send() {
        if (busy) return;
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        input.setText("");
        addMe(text);

        // "记住……" 直接进长期记忆
        String lower = text.replace(" ", "");
        if (lower.startsWith("记住") && text.length() > 3) {
            memory.remember(text.substring(text.indexOf("记住") + 2).trim());
        }
        memory.addTurn("u", text);
        prefs.bumpChat();
        memory.save(this);

        final View typing = showTyping();
        busy = true;

        Brain.ask(this, memory, memory.recent(12), text, new Brain.Callback() {
            @Override
            public void sources(java.util.List<Search.Result> found) {
                runOnUiThread(() -> {
                    StringBuilder sb = new StringBuilder("查到这些：\n");
                    for (int i = 0; i < found.size(); i++) {
                        sb.append("[").append(i + 1).append("] ")
                                .append(found.get(i).title).append("\n")
                                .append(found.get(i).url).append("\n");
                    }
                    addNote(sb.toString().trim());
                });
            }

            @Override
            public void ok(String answer) {
                runOnUiThread(() -> {
                    busy = false;
                    list.removeView(typing);
                    addHer(answer);
                    memory.addTurn("a", answer);
                    memory.save(ChatActivity.this);
                    pushToPet(answer);
                });
            }

            @Override
            public void fail(String message) {
                runOnUiThread(() -> {
                    busy = false;
                    list.removeView(typing);
                    String local = "local".equals(prefs.brainMode())
                            ? "连不上电脑上的模型（" + prefs.brainHost() + "），看看 Ollama 开着没？"
                            : "接口出错了：" + message + "，检查一下 Key 和余额。";
                    addHer(local);
                    addHer("设置 -> 右上角 ⚙ 可以改。");
                });
            }
        });

        try {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        } catch (Exception ignored) {
        }
    }

    private View showTyping() {
        TextView tv = Ui.text(this, "· · ·", 14, 0xFF1E88C7);
        tv.setBackgroundResource(R.drawable.chat_her);
        int p = Ui.dp(this, 14);
        tv.setPadding(p, Ui.dp(this, 7), p, Ui.dp(this, 7));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.START);
        row.addView(tv);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = Ui.dp(this, 8);
        list.addView(row, rowLp);
        scrollDown();
        return row;
    }

    private void pushToPet(String text) {
        if (!PetService.running) return;
        sendBroadcast(new Intent(PetService.ACTION_SAY)
                .setPackage(getPackageName())
                .putExtra("text", text));
    }

    private void showConfig() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 18);
        box.setPadding(p, p, p, p);

        TextView tip = Ui.text(this,
                "本地：填 Ollama 地址（手机与电脑同一 Wi-Fi，或 adb reverse 用 127.0.0.1）\n"
                        + "API：填兼容 OpenAI 的地址与 Key",
                10, 0xFF5B7E99);
        box.addView(tip);

        EditText mode = field(box, "模式（local / api）", prefs.brainMode());
        EditText host = field(box, "本地地址", prefs.brainHost());
        EditText model = field(box, "本地模型", prefs.brainModel());
        EditText base = field(box, "API 地址", prefs.apiBase());
        EditText apiModel = field(box, "API 模型", prefs.apiModel());
        EditText key = field(box, "API Key", prefs.apiKey());
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle("对话设置")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    prefs.setBrainMode(mode.getText().toString().trim());
                    prefs.setBrainHost(host.getText().toString().trim());
                    prefs.setBrainModel(model.getText().toString().trim());
                    prefs.setApiBase(base.getText().toString().trim());
                    prefs.setApiModel(apiModel.getText().toString().trim());
                    prefs.setApiKey(key.getText().toString().trim());
                    refreshSubtitle();
                    Toast.makeText(this, "保存了", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private EditText field(LinearLayout parent, String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextSize(12);
        e.setTextColor(0xFF0E3552);
        e.setHintTextColor(0xFF7A97AD);
        e.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 6);
        parent.addView(e, lp);
        return e;
    }
}
