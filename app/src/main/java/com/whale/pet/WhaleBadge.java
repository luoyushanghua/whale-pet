package com.whale.pet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** 卡片右上角的小鲸鱼，轻轻摆动（±8°），尾巴错相位甩。 */
public class WhaleBadge extends View {

    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bodyPath = new Path();
    private final Path tailPath = new Path();
    private final long t0 = System.currentTimeMillis();

    public WhaleBadge(Context c) {
        super(c);
        body.setColor(0xFF4FA8DC);
        body.setStyle(Paint.Style.FILL);
        tail.setColor(0xFF3E92C4);
        tail.setStyle(Paint.Style.FILL);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float t = (System.currentTimeMillis() - t0) / 1000f;

        float angle = (float) Math.sin(t * 1.4f) * 8f;
        canvas.save();
        canvas.rotate(angle, w / 2f, h * 0.55f);

        // 身体：椭圆 + 尾巴
        bodyPath.reset();
        float bw = w * 0.72f, bh = h * 0.42f;
        float cx = w * 0.46f, cy = h * 0.55f;
        bodyPath.addOval(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f, Path.Direction.CW);
        canvas.drawPath(bodyPath, body);

        float ta = (float) Math.sin(t * 1.4f + 1.1f) * 14f;
        tailPath.reset();
        tailPath.moveTo(cx + bw * 0.42f, cy);
        tailPath.lineTo(cx + bw * 0.42f + bh * 0.55f, cy - bh * 0.55f + ta * 0.3f);
        tailPath.lineTo(cx + bw * 0.42f + bh * 0.55f, cy + bh * 0.55f + ta * 0.3f);
        tailPath.close();
        canvas.drawPath(tailPath, tail);

        // 眼睛
        Paint eye = new Paint(Paint.ANTI_ALIAS_FLAG);
        eye.setColor(Color.WHITE);
        canvas.drawCircle(cx - bw * 0.18f, cy - bh * 0.12f, Math.max(1.6f, w * 0.035f), eye);

        canvas.restore();
        postInvalidateOnAnimation();
    }
}
