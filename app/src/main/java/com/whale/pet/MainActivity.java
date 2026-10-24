package com.whale.pet;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int INK = 0xFF0E3552;
    private static final int MUTED = 0xFF5B7E99;

    private TextView statusText;
    private View statusDot;
    private Button summon;
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        if (!prefs.agreed()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("使用前请先看一眼")
                    .setCancelable(false)
                    .setMessage("1. 这是个人自用工具，非商业产品，按现状提供，不保证不出错。\n\n"
                            + "2. 会读通知标题与摘要、前台应用包名，只在本机处理，不上传。\n\n"
                            + "3. 联网聊天时，你说的话会发给你自己配置的模型（本地 Ollama 或你填的 API）。\n\n"
                            + "4. 形象为社区二创（CC BY-NC-SA 4.0），请勿商用。\n\n"
                            + "5. 继续即表示你理解并接受以上内容。")
                    .setPositiveButton("我知道了", (d, w) -> {
                        prefs.setAgreed(true);
                        recreate();
                    })
                    .setNegativeButton("退出", (d, w) -> finish())
                    .show();
        }

        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundResource(R.drawable.bg_app);

        ImageView glow = new ImageView(this);
        glow.setImageResource(R.drawable.hero_glow);
        FrameLayout.LayoutParams glowLp = new FrameLayout.LayoutParams(dp(340), dp(340));
        glowLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        glowLp.topMargin = dp(6);
        screen.addView(glow, glowLp);

        WhaleScene bubbles = new WhaleScene(this);
        screen.addView(bubbles, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        screen.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(24);
        root.setPadding(pad, dp(28), pad, dp(26));

        ImageView hero = new ImageView(this);
        hero.setImageResource(R.drawable.hero);
        hero.setAdjustViewBounds(true);
        root.addView(hero, new LinearLayout.LayoutParams(dp(150), dp(112)));

        ObjectAnimator floatAnim = ObjectAnimator.ofFloat(hero, "translationY", 0f, -dp(8));
        floatAnim.setDuration(2600);
        floatAnim.setRepeatCount(ValueAnimator.INFINITE);
        floatAnim.setRepeatMode(ValueAnimator.REVERSE);
        floatAnim.setInterpolator(new DecelerateInterpolator());
        floatAnim.start();

        TextView title = new TextView(this);
        title.setText("鲸鱼娘");
        title.setTextColor(INK);
        title.setTextSize(27);
        title.setTypeface(getResources().getFont(R.font.ruantang));
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = text("桌 面 陪 伴 · 本 地 版", 11, MUTED);
        sub.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams subLp = wrap();
        subLp.topMargin = dp(4);
        root.addView(sub, subLp);

        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackgroundResource(R.drawable.chip_bg);
        chip.setPadding(dp(14), dp(7), dp(16), dp(7));
        LinearLayout.LayoutParams chipLp = wrap();
        chipLp.topMargin = dp(12);
        root.addView(chip, chipLp);

        statusDot = new View(this);
        statusDot.setBackgroundResource(R.drawable.dot);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(7), dp(7));
        dotLp.rightMargin = dp(8);
        chip.addView(statusDot, dotLp);

        statusText = text("", 12, 0xFF23506F);
        chip.addView(statusText);

        summon = new Button(this);
        summon.setText("召唤她出来");
        summon.setAllCaps(false);
        summon.setTextSize(16);
        summon.setTypeface(Typeface.DEFAULT_BOLD);
        summon.setTextColor(0xFF06202F);
        summon.setBackgroundResource(R.drawable.btn_primary);
        summon.setStateListAnimator(null);
        summon.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                askOverlay();
                return;
            }
            startPetService();
        });
        LinearLayout.LayoutParams summonLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        summonLp.topMargin = dp(16);
        root.addView(summon, summonLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        rowLp.topMargin = dp(10);
        root.addView(row, rowLp);

        Button perm = ghost("权限设置");
        perm.setOnClickListener(v -> startActivity(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))));
        row.addView(perm, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        Button stop = ghost("收回桌面");
        stop.setOnClickListener(v -> {
            prefs.setWasRunning(false);
            stopService(new Intent(this, PetService.class));
        });
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        stopLp.leftMargin = dp(10);
        row.addView(stop, stopLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(12);
        root.addView(card, cardLp);

        LinearLayout cardHead = new LinearLayout(this);
        cardHead.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(cardHead, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        cardHead.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        titles.addView(text("互动模式", 15, 0xFF12405F));
        TextView hint = text("点屏幕任意位置，她会跑过去", 11, MUTED);
        LinearLayout.LayoutParams hintLp = wrap();
        hintLp.topMargin = dp(2);
        titles.addView(hint, hintLp);

        Switch interactive = new Switch(this);
        interactive.setChecked(prefs.interactive());
        interactive.setOnCheckedChangeListener((b, checked) -> {
            prefs.setInteractive(checked);
            restartServiceIfRunning();
        });
        cardHead.addView(interactive);

        TextView gestures = text("单击回应 · 双击投喂 · 长按摸头 · 拖起来能甩", 10, 0xFF6C8CA6);
        LinearLayout.LayoutParams gLp = wrap();
        gLp.topMargin = dp(6);
        gLp.leftMargin = dp(1);
        card.addView(gestures, gLp);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setGravity(Gravity.CENTER_VERTICAL);
        stats.setBackgroundResource(R.drawable.card_bg);
        stats.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsLp.topMargin = dp(10);
        root.addView(stats, statsLp);

        stats.addView(stat("陪她的第 " + prefs.daysTogether() + " 天", "从第一次召唤算起"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        View sep = new View(this);
        sep.setBackgroundColor(0xFFBFE2F7);
        stats.addView(sep, new LinearLayout.LayoutParams(dp(1), dp(28)));
        LinearLayout rightStat = stat("被戳了 " + prefs.interactions() + " 次", "现在还记着");
        LinearLayout.LayoutParams rsLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rsLp.leftMargin = dp(8);
        stats.addView(rightStat, rsLp);

        // ---- 签到 / 成就 / 专注 ----
        LinearLayout care = new LinearLayout(this);
        care.setOrientation(LinearLayout.VERTICAL);
        care.setBackgroundResource(R.drawable.card_bg);
        care.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams careLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        careLp.topMargin = dp(10);
        FrameLayout careWrap = new FrameLayout(this);
        careWrap.setClipChildren(false);
        careWrap.addView(care, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        WhaleBadge careBadge = new WhaleBadge(this);
        FrameLayout.LayoutParams bLp = new FrameLayout.LayoutParams(
                dp(38), dp(32), Gravity.TOP | Gravity.END);
        bLp.topMargin = dp(-6);
        bLp.rightMargin = dp(10);
        careWrap.addView(careBadge, bLp);
        root.addView(careWrap, careLp);

        LinearLayout careRow = new LinearLayout(this);
        careRow.setGravity(Gravity.CENTER_VERTICAL);
        care.addView(careRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout careTexts = new LinearLayout(this);
        careTexts.setOrientation(LinearLayout.VERTICAL);
        careRow.addView(careTexts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        careTexts.addView(text("连续签到 " + prefs.streakDays() + " 天", 15, 0xFF0E3552));
        careTexts.addView(text("累计 " + prefs.totalCheckins() + " 天 · 成就 "
                + Achv.unlockedCount(this) + "/" + Achv.list(this).size(), 10, 0xFF6C8CA6));

        Button checkin = new Button(this);
        boolean done = prefs.checkedToday();
        checkin.setText(done ? "已打卡" : "打卡");
        checkin.setAllCaps(false);
        checkin.setTextSize(12);
        checkin.setTextColor(done ? 0xFF1E88C7 : 0xFF06202F);
        checkin.setBackgroundResource(done ? R.drawable.btn_ghost : R.drawable.btn_primary);
        checkin.setStateListAnimator(null);
        checkin.setOnClickListener(v -> {
            if (prefs.checkedToday()) {
                android.widget.Toast.makeText(this, "今天已经打过卡啦～", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.checkin();
            prefs.bumpMood(6);
            android.widget.Toast.makeText(this, "打卡成功！连续 " + prefs.streakDays() + " 天",
                    android.widget.Toast.LENGTH_SHORT).show();
            recreate();
        });
        careRow.addView(checkin, new LinearLayout.LayoutParams(dp(72), dp(38)));

        TextView achv = text("看看成就 →", 11, 0xFF1E88C7);
        achv.setPadding(0, dp(8), 0, 0);
        achv.setOnClickListener(v -> showAchievements());
        care.addView(achv);

        TextView focus = text("专注 25 分钟（这段时间她不会吵你）→", 11, 0xFF12A97A);
        focus.setPadding(0, dp(6), 0, 0);
        TextView poster = text("生成分享海报 →", 11, 0xFF1E88C7);
        poster.setPadding(0, dp(6), 0, 0);
        poster.setOnClickListener(v -> Poster.share(this));
        care.addView(poster);

        focus.setOnClickListener(v -> {
            if (!PetService.running) {
                android.widget.Toast.makeText(this, "先把她叫出来吧～", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            startService(new Intent(this, PetService.class)
                    .setAction(PetService.ACTION_FOCUS).putExtra("minutes", 25));
            android.widget.Toast.makeText(this, "开始专注，她会在旁边安静陪着", android.widget.Toast.LENGTH_SHORT).show();
        });
        care.addView(focus);
        LinearLayout links = new LinearLayout(this);
        links.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams linksLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        linksLp.topMargin = dp(10);
        root.addView(links, linksLp);

        links.addView(linkTile("聊天", "和她说话", R.drawable.ic_chat, v -> startActivity(
                new Intent(MainActivity.this, ChatActivity.class))), weight(0));
        links.addView(linkTile("参数", "大小 · 速度", R.drawable.ic_sliders, v -> startActivity(
                new Intent(MainActivity.this, SettingsActivity.class))), weight(8));
        links.addView(linkTile("离场", "打游戏时躲开", R.drawable.ic_game, v -> openAway()), weight(8));
        links.addView(linkTile("关于", "鸣谢与出处", R.drawable.ic_info, v -> startActivity(
                new Intent(MainActivity.this, CreditsActivity.class))), weight(8));

        TextView usageLabel = text("", 11, 0xFF7A97AD);
        usageLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams useLp = wrap();
        useLp.topMargin = dp(12);
        root.addView(usageLabel, useLp);

        scroll.addView(root);
        setContentView(screen);
        if (!PetService.running && prefs.wasRunning() && Settings.canDrawOverlays(this)) {
            startPetService();
        }
        refreshFooter(usageLabel);
    }

    private void openAway() {
        if (!hasUsageAccess()) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            return;
        }
        startActivity(new Intent(MainActivity.this, AwayAppsActivity.class));
    }

    private LinearLayout linkTile(String title, String sub, int icon, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundResource(R.drawable.card_bg);
        box.setOnClickListener(click);
        box.setClickable(true);
        box.setFocusable(true);

        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        box.addView(iv, new LinearLayout.LayoutParams(dp(18), dp(18)));

        TextView t = text(title, 13, 0xFF12405F);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.topMargin = dp(3);
        box.addView(t, tLp);

        TextView s = text(sub, 9, 0xFF6C8CA6);
        s.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = dp(1);
        box.addView(s, sLp);
        return box;
    }

    private LinearLayout stat(String value, String label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);

        TextView v = text(value, 15, INK);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        box.addView(v);

        TextView l = text(label, 10, 0xFF6C8CA6);
        l.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lLp = wrap();
        lLp.topMargin = dp(3);
        box.addView(l, lLp);
        return box;
    }

    private LinearLayout.LayoutParams weight(int marginLeft) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.leftMargin = dp(marginLeft);
        return lp;
    }

    private Button ghost(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(0xFF1B4A6B);
        b.setBackgroundResource(R.drawable.btn_ghost);
        b.setStateListAnimator(null);
        return b;
    }

    private TextView text(String s, int sizeSp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** 要权限的时候软一点，别像系统弹窗那么生硬 */
    private void askOverlay() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("呜…还差一个许可")
                .setMessage("主人要是不点「允许」，我就只能被关在小框框里，"
                        + "不能趴在你的桌面上陪你…\n\n"
                        + "就一下下，好不好嘛？（后面随时能在系统设置里收回）")
                .setPositiveButton("给你许可", (d, w) -> startActivity(new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("再等等", null)
                .show();
    }

    private void showAchievements() {
        StringBuilder sb = new StringBuilder();
        for (Achv.Item it : Achv.list(this)) {
            sb.append(it.unlocked ? "🏅 " : "·  ").append(it.name).append("  —  ")
                    .append(it.desc).append("\n");
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("成就 " + Achv.unlockedCount(this) + " / " + Achv.list(this).size())
                .setMessage(sb.toString())
                .setPositiveButton("好", null)
                .show();
    }

    private void startPetService() {
        if (PetService.running) {
            // 服务在跑但她可能是隐身状态（1x1）：重启一次，让 showPet() 把她恢复出来
            restartServiceIfRunning();
            android.widget.Toast.makeText(this,
                    "把她叫回来啦～", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, PetService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        summon.postDelayed(() -> android.widget.Toast.makeText(this,
                "我在桌面等你～按 Home 就能看见", android.widget.Toast.LENGTH_SHORT).show(), 400);
    }

    private void restartServiceIfRunning() {
        if (!PetService.running) return;
        stopService(new Intent(this, PetService.class));
        summon.postDelayed(this::startPetService, 350);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean running = PetService.running;

        if (!overlay) {
            statusText.setText("还没有悬浮窗权限");
            tintDot(0xFFFFC46B);
        } else if (running) {
            statusText.setText("她在桌面上");
            tintDot(0xFF12A97A);
        } else {
            statusText.setText("准备好了，叫她出来");
            tintDot(0xFF1E88C7);
        }
        summon.setAlpha(overlay ? 1f : 0.6f);
    }

    private void refreshFooter(TextView usageLabel) {
        int n = prefs.getAwayPackages().size();
        int mood = prefs.mood();
        String moodText = mood >= 70 ? "心情不错" : mood >= 40 ? "心情一般" : "有点低落";
        if (hasUsageAccess()) {
            usageLabel.setText((n == 0
                    ? "✓ 使用情况已开启 · 还没选要躲开的应用"
                    : "✓ 使用情况已开启 · 离场名单 " + n + " 个") + " · " + moodText);
        } else {
            usageLabel.setText("点「离场」开启使用情况访问 · " + moodText);
        }
    }

    private void tintDot(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        statusDot.setBackground(d);
    }

    private boolean hasUsageAccess() {
        try {
            AppOpsManager aom = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
