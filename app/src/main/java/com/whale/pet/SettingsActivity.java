package com.whale.pet;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** 参数调节：大小、透明度、活跃度、跑速、点击穿透、吸附、自启。 */
public class SettingsActivity extends Activity {

    private Prefs prefs;
    private ImageView heroPreview;

    private TextView section(String title) {
        TextView t = Ui.text(this, title, 11, 0xFF1E88C7);
        t.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams lp = Ui.wrap();
        lp.topMargin = Ui.dp(this, 20);
        lp.bottomMargin = Ui.dp(this, 2);
        t.setLayoutParams(lp);
        return t;
    }

    private void applyPreview() {
        if (heroPreview == null) return;
        float scale = prefs.sizePct() / 40f;
        heroPreview.setScaleX(scale);
        heroPreview.setScaleY(scale);
        heroPreview.setAlpha(prefs.alphaPct() / 100f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundResource(R.drawable.bg_app);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 22);
        root.setPadding(pad, Ui.dp(this, 44), pad, Ui.dp(this, 40));

        root.addView(Ui.header(this, "参数", "改完立刻生效，不用重新召唤"));

        // 实时预览：改大小/透明度，这里立刻变
        LinearLayout preview = Ui.card(this);
        LinearLayout.LayoutParams pvLp = Ui.lp();
        pvLp.topMargin = Ui.dp(this, 14);
        preview.setGravity(android.view.Gravity.CENTER);
        preview.setMinimumHeight(Ui.dp(this, 132));
        root.addView(preview, pvLp);

        heroPreview = new ImageView(this);
        heroPreview.setImageResource(R.drawable.hero_crop);
        preview.addView(heroPreview, new LinearLayout.LayoutParams(
                Ui.dp(this, 86), Ui.dp(this, 108)));
        applyPreview();

        root.addView(section("外观"));

        slider(root, "大小", "占屏幕宽度的比例", 30, 70, prefs.sizePct(), "%", v -> {
            prefs.setSizePct(v);
            applyPreview();
            reload();
        });

        slider(root, "不透明度", "想让她淡一点，就把这个调低", 40, 100, prefs.alphaPct(), "%", v -> {
            prefs.setAlphaPct(v);
            applyPreview();
            reload();
        });

        slider(root, "活跃度", "待机时多久自己动一次", 10, 120, prefs.ambientSec(), " 秒", v -> {
            prefs.setAmbientSec(v);
            reload();
        });

        slider(root, "跑动速度", "点哪跑哪，她按这个速度走", 150, 900, prefs.runSpeed(), " px/s", v -> {
            prefs.setRunSpeed(v);
            reload();
        });

        slider(root, "画面锐度", "素材是 360p，锐化让边缘干净一点；太高会有白边", 0, 100, prefs.sharpen(), "%", v -> {
            prefs.setSharpen(v);
            reload();
        });

        root.addView(section("行为"));

        toggle(root, "点击穿透", "只看着不挡手，但你也点不到她了", prefs.clickThrough(), v -> {
            prefs.setClickThrough(v);
            reload();
        });

        toggle(root, "贴边吸附", "拖到屏幕边缘松手，自动贴住", prefs.edgeSnap(), v -> {
            prefs.setEdgeSnap(v);
            reload();
        });

        toggle(root, "开机自启", "重启手机后自己回到桌面", prefs.autoStart(), v -> {
            prefs.setAutoStart(v);
            reload();
        });

        root.addView(section("通知联动"));
        LinearLayout notifCard = Ui.card(this);
        LinearLayout.LayoutParams nLp = Ui.lp();
        nLp.topMargin = Ui.dp(this, 10);
        root.addView(notifCard, nLp);
        notifCard.addView(Ui.text(this, "让她知道谁在找你", 13, 0xFF12405F));
        notifCard.addView(Ui.text(this,
                "授权「通知使用权」后，有人发消息她会扭头说一句。只读标题和摘要，不上传。",
                10, 0xFF5B7E99));
        TextView goNotif = Ui.text(this, "去授权 →", 12, 0xFF12A97A);
        goNotif.setPadding(0, Ui.dp(this, 8), 0, 0);
        goNotif.setOnClickListener(v -> {
            try {
                startActivity(new Intent(android.provider.Settings
                        .ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception ignored) {
            }
        });
        notifCard.addView(goNotif);

        Button reset = new Button(this);
        reset.setText("恢复默认");
        reset.setAllCaps(false);
        reset.setTextSize(13);
        reset.setTextColor(Ui.INK_2);
        reset.setBackgroundResource(R.drawable.btn_ghost);
        reset.setStateListAnimator(null);
        reset.setOnClickListener(v -> {
            prefs.setSizePct(40);
            prefs.setAlphaPct(100);
            prefs.setAmbientSec(28);
            prefs.setRunSpeed(380);
            prefs.setClickThrough(false);
            prefs.setEdgeSnap(true);
            reload(true);
            finish();
        });
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46));
        rLp.topMargin = Ui.dp(this, 22);
        root.addView(reset, rLp);

        scroll.addView(root);
        setContentView(scroll);
    }

    private interface OnInt {
        void on(int v);
    }

    private interface OnBool {
        void on(boolean v);
    }

    private void slider(LinearLayout parent, String name, String desc,
                        int min, int max, int value, String unit, OnInt cb) {
        LinearLayout box = Ui.card(this);
        LinearLayout.LayoutParams bLp = Ui.lp();
        bLp.topMargin = Ui.dp(this, 12);
        parent.addView(box, bLp);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView t = Ui.text(this, name, 14, Ui.INK_2);
        head.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView val = Ui.text(this, value + unit, 13, Ui.ACCENT);
        head.addView(val);
        box.addView(head);

        TextView d = Ui.text(this, desc, 10, Ui.MUTED);
        LinearLayout.LayoutParams dLp = Ui.wrap();
        dLp.topMargin = Ui.dp(this, 2);
        box.addView(d, dLp);

        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(value - min);
        bar.setPadding(0, Ui.dp(this, 6), 0, 0);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int v = min + progress;
                val.setText(v + unit);
                if (fromUser) cb.on(v);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });
        box.addView(bar);
    }

    private void toggle(LinearLayout parent, String name, String desc,
                        boolean value, OnBool cb) {
        LinearLayout box = Ui.card(this);
        LinearLayout.LayoutParams bLp = Ui.lp();
        bLp.topMargin = Ui.dp(this, 12);
        parent.addView(box, bLp);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView t = Ui.text(this, name, 14, Ui.INK_2);
        head.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(value);
        sw.setOnCheckedChangeListener((b, v) -> cb.on(v));
        head.addView(sw);
        box.addView(head);

        TextView d = Ui.text(this, desc, 10, Ui.MUTED);
        LinearLayout.LayoutParams dLp = Ui.wrap();
        dLp.topMargin = Ui.dp(this, 2);
        box.addView(d, dLp);
    }

    private void reload() {
        reload(false);
    }

    private void reload(boolean resetPosition) {
        if (!PetService.running) return;
        Intent i = new Intent(this, PetService.class);
        i.setAction(PetService.ACTION_RELOAD);
        i.putExtra("reset", resetPosition);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }
}
