package com.whale.pet;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 统一的小工具：颜色、字号、卡片、字体，省得每个页面各写一套。 */
public class Ui {

    public static final int INK = 0xFF0E3552;
    public static final int INK_2 = 0xFF1B4A6B;
    public static final int MUTED = 0xFF5B7E99;
    public static final int FAINT = 0xFF7A97AD;
    public static final int ACCENT = 0xFF1E88C7;
    public static final int OK = 0xFF12A97A;

    public static int dp(Context c, float v) {
        return Math.round(c.getResources().getDisplayMetrics().density * v);
    }

    public static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(dp(c, 3), 1f);
        return t;
    }

    public static TextView heading(Context c, String s) {
        TextView t = text(c, s, 22, INK);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.02f);
        return t;
    }

    public static LinearLayout card(Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.card_bg);
        int p = dp(c, 16);
        box.setPadding(p, dp(c, 14), p, dp(c, 14));
        return box;
    }

    public static LinearLayout.LayoutParams lp() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout header(Context c, String title, String sub) {
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.START);
        head.addView(heading(c, title));
        TextView s = text(c, sub, 11, MUTED);
        LinearLayout.LayoutParams slp = wrap();
        slp.topMargin = dp(c, 4);
        head.addView(s, slp);
        return head;
    }

    public static void divider(Context c, LinearLayout parent) {
        android.view.View v = new android.view.View(c);
        v.setBackgroundColor(0xFF123049);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.topMargin = dp(c, 12);
        lp.bottomMargin = dp(c, 12);
        parent.addView(v, lp);
    }

    public static Typeface cute(Context c) {
        return c.getResources().getFont(R.font.ruantang);
    }
}
