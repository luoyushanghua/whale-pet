package com.whale.pet;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 悬浮式动作面板：不启动 Activity，直接在 WindowManager 里加一层小窗，
 * 长得像她自己的气泡，点外面就收起来。
 */
public class PetPanel {

    public interface Listener {
        void onPlay(String action);

        void onClose();
    }

    private final Context ctx;
    private final Listener listener;
    private WindowManager wm;
    private View root;
    private WindowManager.LayoutParams lp;
    private LinearLayout grid;
    private LinearLayout chips;
    private final Map<String, List<String>> groups = new LinkedHashMap<>();
    private String current;

    public PetPanel(Context ctx, Listener l) {
        this.ctx = ctx;
        this.listener = l;
    }

    public boolean isShowing() {
        return root != null;
    }

    private int lastW, lastH;

    /** 竖屏偏矮、横屏偏窄，随屏幕比例自适应 */
    private int[] computeSize() {
        android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        boolean land = dm.widthPixels > dm.heightPixels;
        int w = land ? (int) (dm.widthPixels * 0.46f) : (int) (dm.widthPixels * 0.80f);
        int h = land ? (int) (dm.heightPixels * 0.80f) : (int) (dm.heightPixels * 0.48f);
        return new int[]{w, h};
    }

    /** 旋转屏幕后重新摆一次，不然还是旧尺寸 */
    public void relayout() {
        if (root == null || wm == null) return;
        int[] sz = computeSize();
        if (sz[0] == lastW && sz[1] == lastH) return;
        lastW = sz[0];
        lastH = sz[1];
        lp.width = sz[0];
        lp.height = sz[1];
        try {
            wm.updateViewLayout(root, lp);
        } catch (Exception ignored) {
        }
    }

    public void show() {
        if (root != null) return;
        loadActions();
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;

        int[] sz = computeSize();
        int w = sz[0], h = sz[1];
        this.lastW = w; this.lastH = h;
LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xC8EAF7FF);
        bg.setCornerRadius(dp(18));
        box.setBackground(bg);
        int pad = dp(8);
        box.setPadding(pad, dp(10), pad, dp(8));

        TextView title = Ui.text(ctx, "想让我做什么？", 13, 0xFF0E3552);
        box.addView(title);

        HorizontalScrollView hs = new HorizontalScrollView(ctx);
        hs.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(ctx);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(chips);
        box.addView(hs);

        ScrollView sv = new ScrollView(ctx);
        grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.VERTICAL);
        sv.addView(grid);
        box.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout quick = new LinearLayout(ctx);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.addView(btn("摸头", "purr", 0), weight(0));
        quick.addView(btn("投喂", "eat_rice", 1), weight(1));
        quick.addView(btn("关掉", null, 1), weight(1));
        box.addView(quick);
        box.addView(fpsRow());

        root = box;
        lp = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(8);
        root.setOnTouchListener((v, e) -> {
            if (e.getAction() == android.view.MotionEvent.ACTION_OUTSIDE) {
                hide();
                return true;
            }
            return false;
        });
        try {
            wm.addView(root, lp);
        } catch (Exception e) {
            root = null;
        }
        showGroup(groups.containsKey("吃什么") ? "吃什么" : firstKey());
    }

    public void hide() {
        if (root == null || wm == null) return;
        try {
            wm.removeView(root);
        } catch (Exception ignored) {
        }
        root = null;
        listener.onClose();
    }

    private String firstKey() {
        return groups.isEmpty() ? "" : groups.keySet().iterator().next();
    }

    private void loadActions() {
        if (!groups.isEmpty()) return;
        try {
            String[] names = ctx.getAssets().list("actions");
            List<String> all = new ArrayList<>();
            if (names != null) {
                for (String n : names) {
                    if (!n.endsWith(".mp4")) continue;
                    String base = n.substring(0, n.length() - 4);
                    if (base.equals("idle")) continue;
                    all.add(base);
                }
            }
            add("全部", all);
            add("吃什么", pick(all, "吃", "饭", "火锅", "西瓜", "糖", "蟹", "面", "零食", "冰淇淋"));
            add("玩耍", pick(all, "玩", "球", "棋", "风筝", "水枪", "镜", "扇"));
            add("时节", pick(all, "烟花", "雪", "圣诞", "中秋", "月", "春", "夏", "秋", "冬"));
            add("小动作", pick(all, "哈欠", "懒腰", "张西望", "漂浮", "踏步", "转", "走路"));
            add("情绪", pick(all, "点击", "吓", "生气", "害羞", "笑", "哭", "睡"));
        } catch (Exception ignored) {
        }
    }

    private List<String> pick(List<String> all, String... keys) {
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

    private void add(String name, List<String> items) {
        if (!items.isEmpty()) groups.put(name, items);
    }

    private void showGroup(String name) {
        current = name;
        chips.removeAllViews();
        for (String g : groups.keySet()) {
            TextView c = Ui.text(ctx, g, 10, g.equals(current) ? 0xFF0E3552 : 0xFF2E5A78);
            GradientDrawable cd = new GradientDrawable();
            cd.setColor(g.equals(current) ? 0xE68FD0FF : 0x40FFFFFF);
            cd.setCornerRadius(dp(14));
            c.setBackground(cd);
            c.setPadding(dp(10), dp(5), dp(10), dp(5));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.rightMargin = dp(6);
            c.setOnClickListener(v -> showGroup(g));
            chips.addView(c, p);
        }

        grid.removeAllViews();
        LinearLayout row = null;
        List<String> items = groups.get(name);
        if (items == null) return;
        for (int i = 0; i < items.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
                rp.topMargin = dp(6);
                grid.addView(row, rp);
            }
            row.addView(item(items.get(i)), weight(6));
        }
    }

    private View item(String action) {
        TextView t = Ui.text(ctx, action, 11, 0xFF12405F);
        t.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCCFFFFFF);
        bg.setCornerRadius(dp(14));
        t.setBackground(bg);
        t.setOnClickListener(v -> {
            listener.onPlay(action);
            hide();
        });
        return t;
    }

    /** 底部一行：帧率切换，立即生效 */
    private View fpsRow() {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, 0);
        TextView label = Ui.text(ctx, "帧率", 12, 0xFF2E5A78);
        row.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int cur = new Prefs(ctx).fps();
        for (int v : new int[]{60, 120}) {
            TextView b = Ui.text(ctx, v + "帧", 11, 0xFF06202F);
            b.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(cur == v ? 0xE68FD0FF : 0x40FFFFFF);
            bg.setCornerRadius(dp(14));
            b.setBackground(bg);
            b.setPadding(dp(12), dp(5), dp(12), dp(5));
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp2.leftMargin = dp(8);
            b.setOnClickListener(view -> {
                new Prefs(ctx).setFps(v);
                ((android.content.Context) ctx).sendBroadcast(
                        new android.content.Intent(PetService.ACTION_FPS)
                                .setPackage(ctx.getPackageName()));
                hide();
            });
            row.addView(b, lp2);
        }
        return row;
    }
    private View btn(String label, String action, int marginLeft) {
        TextView t = Ui.text(ctx, label, 12, 0xFF06202F);
        t.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF9BD9FF);
        bg.setCornerRadius(dp(16));
        t.setBackground(bg);
        t.setPadding(0, dp(7), 0, dp(7));
        t.setOnClickListener(v -> {
            if (action == null) {
                hide();
            } else {
                listener.onPlay(action);
                hide();
            }
        });
        return t;
    }

    private LinearLayout.LayoutParams weight(int left) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.leftMargin = dp(left);
        return p;
    }

    private int dp(float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
