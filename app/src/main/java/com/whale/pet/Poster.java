package com.whale.pet;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;

import java.io.File;

/** 分享海报：Canvas 画一张卡，导出 PNG 走分享。 */
public class Poster {

    public static Uri make(Context ctx) {
        Memory m = Memory.load(ctx);
        Prefs p = new Prefs(ctx);
        int W = 1080, H = 1440;
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        paint.setShader(new LinearGradient(0, 0, 0, H,
                new int[]{0xFFEAF7FF, 0xFFD9EEFF, 0xFFC6E6FF},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, paint);
        paint.setShader(null);

        // 卡片
        paint.setColor(Color.WHITE);
        RectF card = new RectF(90, 240, W - 90, H - 240);
        c.drawRoundRect(card, 48, 48, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        paint.setColor(0xFFBFE2F7);
        c.drawRoundRect(card, 48, 48, paint);
        paint.setStyle(Paint.Style.FILL);

        // 立绘
        try {
            android.graphics.Bitmap hero = android.graphics.BitmapFactory.decodeResource(
                    ctx.getResources(), R.drawable.hero_crop);
            if (hero != null) {
                int hh = 520;
                int hw = Math.round(hero.getWidth() * hh / (float) hero.getHeight());
                c.drawBitmap(hero, null,
                        new RectF(W / 2f - hw / 2f, 300, W / 2f + hw / 2f, 820), paint);
            }
        } catch (Exception ignored) {
        }

        // 文字
        paint.setColor(0xFF0E3552);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(64);
        c.drawText("鲸鱼娘", W / 2f, 920, paint);

        paint.setColor(0xFF5B7E99);
        paint.setTextSize(40);
        c.drawText("第 " + m.daysTogether() + " 天 · 连续签到 " + p.streakDays() + " 天",
                W / 2f, 990, paint);

        paint.setColor(0xFF1E88C7);
        paint.setTextSize(46);
        c.drawText("「今天也想和你一起」", W / 2f, 1090, paint);

        paint.setColor(0xFF9CC8E6);
        paint.setTextSize(28);
        c.drawText("whale-pet · 自己用的小桌宠", W / 2f, H - 300, paint);

        try {
            File dir = new File(ctx.getFilesDir(), "export");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "poster-" + System.currentTimeMillis() + ".png");
            java.io.FileOutputStream out = new java.io.FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();
            return new Uri.Builder().scheme("content")
                    .authority(ExportProvider.AUTHORITY).appendPath(f.getName()).build();
        } catch (Exception e) {
            return null;
        }
    }

    public static void share(Context ctx) {
        Uri uri = make(ctx);
        if (uri == null) return;
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("image/png");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(i, "把海报发给谁？");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(chooser);
    }
}
