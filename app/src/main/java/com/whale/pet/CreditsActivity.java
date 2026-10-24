package com.whale.pet;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 鸣谢：短短一页，从主页最底下那行小字进来。 */
public class CreditsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundResource(R.drawable.bg_app);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 22);
        root.setPadding(pad, Ui.dp(this, 44), pad, Ui.dp(this, 40));

        root.addView(Ui.header(this, "鸣谢", "这只桌宠不是一个人做出来的"));

        ImageView hero = new ImageView(this);
        hero.setImageResource(R.drawable.hero_crop);
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
                Ui.dp(this, 64), Ui.dp(this, 80));
        heroLp.topMargin = Ui.dp(this, 16);
        root.addView(hero, heroLp);

        entry(root, "形象", "鲸鱼娘 · DeepSeek 同人形象\n原型「溟月」：上善无形（CC BY-NC-SA 4.0）");
        entry(root, "素材", "动作取自 dsh-pet 素材包（社区 AI 生成）");
        entry(root, "语音", "edge-tts 合成（晓伊音色）");
        entry(root, "参考", "DS桌宠：只学了交互形式，未搬代码与美术");
        entry(root, "技术", "FFmpeg · Android SDK · Gradle");
        entry(root, "说明", "自用，不商用。原作者有异议即撤换。");

        TextView version = Ui.text(this, "whale-pet 0.1.0", 10, Ui.FAINT);
        LinearLayout.LayoutParams vLp = Ui.wrap();
        vLp.topMargin = Ui.dp(this, 22);
        root.addView(version, vLp);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void entry(LinearLayout parent, String header, String body) {
        TextView h = Ui.text(this, header, 11, Ui.ACCENT);
        LinearLayout.LayoutParams hLp = Ui.lp();
        hLp.topMargin = Ui.dp(this, 18);
        parent.addView(h, hLp);

        LinearLayout card = Ui.card(this);
        LinearLayout.LayoutParams cLp = Ui.lp();
        cLp.topMargin = Ui.dp(this, 8);
        parent.addView(card, cLp);

        card.addView(Ui.text(this, body, 11, Ui.INK_2));
    }
}
