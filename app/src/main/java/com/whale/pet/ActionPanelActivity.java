package com.whale.pet;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 长按她弹出的动作面板：分类横滑 + 两列网格，点一下她就演。 */
public class ActionPanelActivity extends Activity {

    private LinearLayout grid;
    private LinearLayout chips;
    private final Map<String, List<String>> groups = new LinkedHashMap<>();
    private String current;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadActions();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_app);
        int p = Ui.dp(this, 16);
        root.setPadding(p, Ui.dp(this, 34), p, Ui.dp(this, 12));

        TextView title = Ui.text(this, "想让我做什么？", 16, 0xFF0E3552);
        title.setTypeface(getResources().getFont(R.font.ruantang));
        root.addView(title);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 6));
        hs.addView(chips);
        root.addView(hs);

        ScrollView sv = new ScrollView(this);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        sv.addView(grid);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setPadding(0, Ui.dp(this, 6), 0, 0);
        quick.addView(quickBtn("摸头", "purr"), weight(0));
        quick.addView(quickBtn("投喂", "eat_rice"), weight(8));
        quick.addView(quickBtn("跑一下", "run"), weight(8));
        quick.addView(quickBtn("关掉", null), weight(8));
        root.addView(quick);

        setContentView(root);
        setupWindow();
        // 默认进「吃什么」，比 105 项的「全部」好找
        showGroup(groups.containsKey("吃什么") ? "吃什么" : groups.keySet().iterator().next());
    }

    /** 小范围浮窗：不要占满全屏，底部弹出、背景透出桌面 */
    private void setupWindow() {
        android.view.Window w = getWindow();
        if (w == null) return;
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int wpx = (int) (dm.widthPixels * 0.92f);
        int hpx = (int) (dm.heightPixels * 0.62f);
        w.setLayout(wpx, hpx);
        android.view.WindowManager.LayoutParams lp = w.getAttributes();
        lp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
        lp.dimAmount = 0.18f;
        w.setAttributes(lp);
        w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }

    private void loadActions() {
        try {
            String[] names = getAssets().list("actions");
            List<String> all = new ArrayList<>();
            if (names != null) {
                for (String n : names) {
                    if (!n.endsWith(".mp4")) continue;
                    String base = n.substring(0, n.length() - 4);
                    if (base.equals("idle")) continue;
                    all.add(base);
                }
            }
            addGroup("全部", all);
            addGroup("吃什么", filter(all, "吃", "饭", "火锅", "西瓜", "糖", "蟹", "面", "零食", "冰淇淋"));
            addGroup("玩耍", filter(all, "玩", "球", "棋", "风筝", "水枪", "镜", "扇"));
            addGroup("时节", filter(all, "烟花", "雪", "圣诞", "中秋", "月", "春", "夏", "秋", "冬"));
            addGroup("小动作", filter(all, "哈欠", "懒腰", "张西望", "漂浮", "踏步", "转", "走路"));
            addGroup("情绪", filter(all, "点击", "吓", "生气", "害羞", "笑", "哭", "睡"));
        } catch (Exception ignored) {
        }
    }

    private List<String> filter(List<String> all, String... keys) {
        List<String> out = new ArrayList<>();
        for (String a : all) {
            for (String k : keys) {
                if (a.contains(k)) {
                    out.add(a);
                    break;
                }
            }
        }
        return out;
    }

    private void addGroup(String name, List<String> items) {
        if (!items.isEmpty()) groups.put(name, items);
    }

    private void showGroup(String name) {
        current = name;
        chips.removeAllViews();
        for (String g : groups.keySet()) {
            TextView c = Ui.text(this, g, 12, g.equals(current) ? 0xFF0E3552 : 0xFF5B7E99);
            c.setBackgroundResource(g.equals(current) ? R.drawable.btn_primary : R.drawable.chip_bg);
            c.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 6);
            c.setOnClickListener(v -> showGroup(g));
            chips.addView(c, lp);
        }

        grid.removeAllViews();
        LinearLayout row = null;
        List<String> items = groups.get(name);
        for (int i = 0; i < items.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = Ui.dp(this, 6);
                grid.addView(row, rlp);
            }
            row.addView(itemBtn(items.get(i)), weight(6));
        }
    }

    private View itemBtn(String action) {
        TextView t = Ui.text(this, action, 11, 0xFF12405F);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.card_bg);
        t.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        t.setOnClickListener(v -> {
            sendBroadcast(new Intent(PetService.ACTION_PLAY)
                    .setPackage(getPackageName())
                    .putExtra("name", action));
            finish();
        });
        return t;
    }

    private View quickBtn(String label, String action) {
        TextView t = Ui.text(this, label, 12, 0xFF06202F);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.btn_primary);
        t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        t.setOnClickListener(v -> {
            if (action == null) {
                finish();
                return;
            }
            sendBroadcast(new Intent(PetService.ACTION_PLAY)
                    .setPackage(getPackageName())
                    .putExtra("name", action));
            finish();
        });
        return t;
    }

    private LinearLayout.LayoutParams weight(int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dp(this, left);
        return lp;
    }
}
