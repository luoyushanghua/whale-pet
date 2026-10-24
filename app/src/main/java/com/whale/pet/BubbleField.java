package com.whale.pet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 背景里慢慢上升的小气泡，给静态页面一点"活"的感觉。 */
public class BubbleField extends View {

    private static class Bubble {
        float x, y, r, speed, alpha;
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Bubble> bubbles = new ArrayList<>();
    private final Random random = new Random();
    private long lastTick;

    public BubbleField(Context context) {
        super(context);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public BubbleField(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (!bubbles.isEmpty()) return;
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < 16; i++) {
            Bubble b = new Bubble();
            b.r = (5 + random.nextFloat() * 16) * d;
            b.x = random.nextFloat() * w;
            b.y = h * (0.35f + random.nextFloat() * 0.65f);
            b.speed = (6 + random.nextFloat() * 14) * d;
            b.alpha = 0.03f + random.nextFloat() * 0.05f;
            bubbles.add(b);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.currentTimeMillis();
        if (lastTick == 0) lastTick = now;
        float dt = Math.min(0.05f, (now - lastTick) / 1000f);
        lastTick = now;

        int h = getHeight();
        for (Bubble b : bubbles) {
            b.y -= b.speed * dt;
            if (b.y < -b.r * 2) {
                b.y = h + b.r;
                b.x = random.nextFloat() * getWidth();
            }
            paint.setColor(Color.argb((int) (b.alpha * 255), 190, 230, 255));
            canvas.drawCircle(b.x, b.y, b.r, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(Color.argb((int) (b.alpha * 180), 255, 255, 255));
            canvas.drawCircle(b.x, b.y, b.r, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        postInvalidateOnAnimation();
    }
}
