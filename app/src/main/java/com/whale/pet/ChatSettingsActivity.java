package com.whale.pet;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 对话设置：给"没有电脑、只能用 API"的朋友也能两分钟配好。
 */
public class ChatSettingsActivity extends Activity {

    private Prefs prefs;

    private LinearLayout modeLocal;
    private LinearLayout modeApi;
    private LinearLayout localBox;
    private LinearLayout apiBox;

    private EditText host;
    private EditText localModel;
    private EditText base;
    private EditText apiModel;
    private EditText key;
    private TextView testResult;

    private boolean apiMode;
    private TextView localTitle, localSub, apiTitle, apiSub;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        apiMode = !"local".equals(prefs.brainMode());

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundResource(R.drawable.bg_app);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 22);
        root.setPadding(p, Ui.dp(this, 44), p, Ui.dp(this, 40));

        root.addView(Ui.header(this, "对话设置", "她没有脑子就不会说话——先给她接一个"));

        // ---- 两个模式 ----
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 76));
        mLp.topMargin = Ui.dp(this, 16);
        root.addView(modes, mLp);

        modeLocal = modeCard("自己电脑", "跑 Ollama\n免费 · 要同一 Wi-Fi", false);
        modeLocal.setOnClickListener(v -> setMode(false));
        modes.addView(modeLocal, weight(0));

        modeApi = modeCard("API Key", "DeepSeek 等\n没电脑也能用", true);
        modeApi.setOnClickListener(v -> setMode(true));
        modes.addView(modeApi, weight(10));

        // ---- 本地 ----
        localBox = Ui.card(this);
        LinearLayout.LayoutParams lLp = Ui.lp();
        lLp.topMargin = Ui.dp(this, 14);
        root.addView(localBox, lLp);

        localBox.addView(Ui.text(this, "电脑地址", 12, 0xFF12405F));
        localBox.addView(Ui.text(this,
                "同一 Wi-Fi 填 http://电脑IP:11434；用数据线可先 adb reverse tcp:11434 tcp:11434 再填 127.0.0.1",
                9, 0xFF5B7E99));
        host = field(localBox, "http://192.168.1.10:11434", prefs.brainHost());
        localBox.addView(Ui.text(this, "模型", 12, 0xFF12405F));
        localModel = field(localBox, "qwen3.5:9b", prefs.brainModel());

        // ---- API ----
        apiBox = Ui.card(this);
        LinearLayout.LayoutParams aLp = Ui.lp();
        aLp.topMargin = Ui.dp(this, 14);
        root.addView(apiBox, aLp);

        apiBox.addView(Ui.text(this, "快速选择服务商", 12, 0xFF12405F));
        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams prLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 34));
        prLp.topMargin = Ui.dp(this, 6);
        apiBox.addView(presets, prLp);
        presets.addView(preset("DeepSeek", "https://api.deepseek.com", "deepseek-chat"), weight(0));
        presets.addView(preset("硅基流动", "https://api.siliconflow.cn/v1",
                "deepseek-ai/DeepSeek-V3"), weight(8));
        presets.addView(preset("OpenRouter", "https://openrouter.ai/api/v1",
                "deepseek/deepseek-chat"), weight(8));

        apiBox.addView(Ui.text(this, "API 地址", 12, 0xFF12405F));
        base = field(apiBox, "https://api.deepseek.com", prefs.apiBase());
        apiBox.addView(Ui.text(this, "模型名", 12, 0xFF12405F));
        apiModel = field(apiBox, "deepseek-chat", prefs.apiModel());
        apiBox.addView(Ui.text(this, "API Key", 12, 0xFF12405F));
        apiBox.addView(Ui.text(this,
                "只存在本机，不会上传。去 platform.deepseek.com 建一个，充 10 块能用很久。",
                9, 0xFF5B7E99));
        key = field(apiBox, "sk-...", prefs.apiKey());
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        // ---- 测试 ----
        Button test = new Button(this);
        test.setText("测试一下能不能连上");
        test.setAllCaps(false);
        test.setTextSize(13);
        test.setTextColor(0xFF1B4A6B);
        test.setBackgroundResource(R.drawable.btn_ghost);
        test.setStateListAnimator(null);
        test.setOnClickListener(v -> test());
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46));
        tLp.topMargin = Ui.dp(this, 16);
        root.addView(test, tLp);

        testResult = Ui.text(this, "", 11, 0xFF1E88C7);
        testResult.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rLp = Ui.wrap();
        rLp.topMargin = Ui.dp(this, 8);
        root.addView(testResult, rLp);

        // ---- 存档 ----
        LinearLayout saveBox = Ui.card(this);
        LinearLayout.LayoutParams svLp = Ui.lp();
        svLp.topMargin = Ui.dp(this, 16);
        root.addView(saveBox, svLp);

        saveBox.addView(Ui.text(this, "存档", 13, 0xFF12405F));
        saveBox.addView(Ui.text(this,
                "把她的记忆打包成一段文字，发给自己的 QQ / 邮箱 / 备忘录；换手机粘回来就复活。"
                        + "App 也会每天在本地留一份快照（files/archives）。",
                10, 0xFF5B7E99));

        Memory mem = Memory.load(this);

        Button export = new Button(this);
        export.setText("导出并发送");
        export.setAllCaps(false);
        export.setTextSize(13);
        export.setTextColor(0xFF1B4A6B);
        export.setBackgroundResource(R.drawable.btn_ghost);
        export.setStateListAnimator(null);
        export.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("要把我交出去吗…")
                .setMessage("人家会乖乖把记忆装进一个 .json 小文件，"
                        + "聊天工具不会把它截断。\n主人自己选发给谁，好不好嘛～")
                .setPositiveButton("给你就是了", (d, w) -> Archive.share(this, mem))
                .setNegativeButton("再抱一会儿", null)
                .show());
        LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44));
        eLp.topMargin = Ui.dp(this, 8);
        saveBox.addView(export, eLp);

        Button importBtn = new Button(this);
        importBtn.setText("从存档恢复");
        importBtn.setAllCaps(false);
        importBtn.setTextSize(13);
        importBtn.setTextColor(0xFF1B4A6B);
        importBtn.setBackgroundResource(R.drawable.btn_ghost);
        importBtn.setStateListAnimator(null);
        importBtn.setOnClickListener(v -> pickImport());
        LinearLayout.LayoutParams iLp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44));
        iLp2.topMargin = Ui.dp(this, 8);
        saveBox.addView(importBtn, iLp2);

        TextView snap = Ui.text(this,
                "本地快照：" + Archive.snapshotCount(this) + " 份", 9, 0xFF6C8CA6);
        LinearLayout.LayoutParams snLp = Ui.wrap();
        snLp.topMargin = Ui.dp(this, 6);
        saveBox.addView(snap, snLp);

        // ---- 保存 ----
        Button save = new Button(this);
        save.setText("保存");
        save.setAllCaps(false);
        save.setTextSize(15);
        save.setTextColor(0xFF06202F);
        save.setBackgroundResource(R.drawable.btn_primary);
        save.setStateListAnimator(null);
        save.setOnClickListener(v -> {
            saveToPrefs();
            Toast.makeText(this, "保存了", Toast.LENGTH_SHORT).show();
            finish();
        });
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52));
        sLp.topMargin = Ui.dp(this, 18);
        root.addView(save, sLp);

        scroll.addView(root);
        setContentView(scroll);

        setMode(apiMode);
    }

    private void setMode(boolean api) {
        apiMode = api;
        modeLocal.setBackgroundResource(api ? R.drawable.card_bg : R.drawable.btn_primary);
        modeApi.setBackgroundResource(api ? R.drawable.btn_primary : R.drawable.card_bg);
        if (localTitle != null) {
            localTitle.setTextColor(api ? 0xFF12405F : 0xFF06202F);
            localSub.setTextColor(api ? 0xFF5B7E99 : 0xFF0B3348);
            apiTitle.setTextColor(api ? 0xFF06202F : 0xFF12405F);
            apiSub.setTextColor(api ? 0xFF0B3348 : 0xFF5B7E99);
        }
        localBox.setVisibility(api ? View.GONE : View.VISIBLE);
        apiBox.setVisibility(api ? View.VISIBLE : View.GONE);
    }

    private LinearLayout modeCard(String title, String sub, boolean api) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundResource(api ? R.drawable.btn_primary : R.drawable.card_bg);

        TextView t = Ui.text(this, title, 14, 0xFF12405F);
        t.setGravity(Gravity.CENTER);
        box.addView(t);
        TextView s = Ui.text(this, sub, 9, 0xFF5B7E99);
        s.setGravity(Gravity.CENTER);
        box.addView(s);
        if (api) { apiTitle = t; apiSub = s; } else { localTitle = t; localSub = s; }
        return box;
    }

    private View preset(String label, String url, String model) {
        TextView t = Ui.text(this, label, 11, 0xFF1E88C7);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.chip_bg);
        t.setOnClickListener(v -> {
            base.setText(url);
            apiModel.setText(model);
            testResult.setText("");
        });
        return t;
    }

    private EditText field(LinearLayout parent, String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextSize(12);
        e.setTextColor(0xFF0E3552);
        e.setHintTextColor(0xFF7A97AD);
        e.setSingleLine(true);
        e.setBackgroundResource(R.drawable.input_bg);
        int p = Ui.dp(this, 10);
        e.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 4);
        lp.bottomMargin = Ui.dp(this, 8);
        parent.addView(e, lp);
        return e;
    }

    private static final int REQ_IMPORT = 1001;

    private void pickImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK || data == null) return;
        Memory m = Archive.importFrom(this, data.getData());
        if (m == null) {
            Toast.makeText(this, "这个文件我看不懂…是不是拿错了？", Toast.LENGTH_SHORT).show();
            return;
        }
        m.save(this);
        Toast.makeText(this, "接回来了！认识第 " + m.daysTogether() + " 天～", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void showImportDialog(Memory mem) {
        EditText e = new EditText(this);
        e.setHint("把之前导出的那段文字粘在这里");
        e.setTextSize(11);
        e.setMinLines(4);
        e.setGravity(android.view.Gravity.TOP);
        int p = Ui.dp(this, 16);
        e.setPadding(p, p, p, p);

        new android.app.AlertDialog.Builder(this)
                .setTitle("从存档恢复")
                .setView(e)
                .setPositiveButton("导入", (d, w) -> {
                    Memory parsed = Archive.parse(e.getText().toString());
                    if (parsed == null) {
                        Toast.makeText(this, "这段不是有效存档", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    parsed.save(this);
                    Toast.makeText(this, "恢复好了：第 " + parsed.daysTogether() + " 天",
                            Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveToPrefs() {
        prefs.setBrainMode(apiMode ? "api" : "local");
        prefs.setBrainHost(host.getText().toString().trim());
        prefs.setBrainModel(localModel.getText().toString().trim());
        prefs.setApiBase(base.getText().toString().trim());
        prefs.setApiModel(apiModel.getText().toString().trim());
        prefs.setApiKey(key.getText().toString().trim());
    }

    private void test() {
        saveToPrefs();
        testResult.setText("正在连…");
        Memory m = new Memory();
        Brain.ask(this, m, new java.util.ArrayList<>(), "在吗？只回两个字。", new Brain.Callback() {
            @Override
            public void ok(String text) {
                runOnUiThread(() -> testResult.setText("✓ 连上了：" + text));
            }

            @Override
            public void fail(String message) {
                runOnUiThread(() -> testResult.setText("✗ " + message));
            }
        });
    }

    private LinearLayout.LayoutParams weight(int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.leftMargin = Ui.dp(this, left);
        return lp;
    }
}
