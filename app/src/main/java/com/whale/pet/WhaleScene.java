package com.whale.pet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.Random;

/** 背景：两层反向流动的浪 + 几只慢慢游过的小鲸鱼。全部 Canvas 绘制，零资源。 */
public class WhaleScene extends View {

    private final Paint wave = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whale = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Random r = new Random();
    private final float[] wx = new float[4];
    private final float[] wy = new float[4];
    private final float[] wspeed = new float[4];
    private final float[] wsize = new float[4];
    private long t0 = System.currentTimeMillis();

    public WhaleScene(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        wave.setStyle(Paint.Style.FILL);
        whale.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 4; i++) {
            wx[i] = r.nextFloat();
            wy[i] = 0.1f + r.nextFloat() * 0.55f;
            wspeed[i] = 0.012f + r.nextFloat() * 0.02f;
            wsize[i] = 0.05f + r.nextFloat() * 0.05f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float t = (System.currentTimeMillis() - t0) / 1000f;

        for (int i = 0; i < 4; i++) {
            float x = wx[i] * w;
            float y = (wy[i] + (float) Math.sin(t * 0.6f + i) * 0.01f) * h;
            drawWhale(canvas, x, y, wsize[i] * w, 0.10f);
            wx[i] += wspeed[i] * 0.016f;
            if (wx[i] > 1.15f) wx[i] = -0.15f;
        }

        drawWave(canvas, h * 0.86f, 0.020f, t * 0.7f, 0x33, w);
        drawWave(canvas, h * 0.92f, 0.026f, -t * 0.5f, 0x55, w);

        postInvalidateOnAnimation();
    }

    private void drawWave(Canvas c, float baseY, float amp, float phase, int alpha, float w) {
        wave.setColor(Color.argb(alpha, 120, 200, 255));
        path.reset();
        path.moveTo(0, baseY);
        for (float x = 0; x <= w; x += 12) {
            float y = baseY + (float) Math.sin(x / w * 6.28f + phase) * amp * getHeight()
                    + (float) Math.sin(x / w * 12.5f + phase * 1.7f) * amp * getHeight() * 0.4f;
            path.lineTo(x, y);
        }
        path.lineTo(w, getHeight());
        path.lineTo(0, getHeight());
        path.close();
        c.drawPath(path, wave);
    }

    private void drawWhale(Canvas c, float x, float y, float size, float alpha) {
        whale.setColor(Color.argb((int) (alpha * 255), 40, 120, 180));
        float bodyW = size;
        float bodyH = size * 0.42f;
        c.drawOval(x - bodyW / 2, y - bodyH / 2, x + bodyW / 2, y + bodyH / 2, whale);
        // 尾巴
        Path tail = new Path();
        tail.moveTo(x + bodyW / 2 - 2, y);
        tail.lineTo(x + bodyW / 2 + bodyH * 0.55f, y - bodyH * 0.55f);
        tail.lineTo(x + bodyW / 2 + bodyH * 0.55f, y + bodyH * 0.55f);
        tail.close();
        c.drawPath(tail, whale);
        // 眼睛
        whale.setColor(Color.argb((int) (alpha * 255), 255, 255, 255));
        c.drawCircle(x + bodyW * 0.18f, y - bodyH * 0.12f, Math.max(1.5f, size * 0.02f), whale);
        whale.setColor(Color.argb((int) (alpha * 255), 40, 120, 180));
    }
}
