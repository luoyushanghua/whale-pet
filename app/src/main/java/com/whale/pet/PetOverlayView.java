package com.whale.pet;

import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;

public class PetOverlayView extends GLSurfaceView {

    public interface DragListener {
        void onDragBy(float dx, float dy);

        void onDragEnd(float vx, float vy);

        void onTap();

        void onDoubleTap();

        void onLongPress();
    }

    private final PetRenderer renderer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private DragListener dragListener;

    private float lastX, lastY, downX, downY;
    private long lastMoveAt;
    private float vx, vy;
    private boolean dragging;
    private boolean longPressFired;

    private long lastTapAt;
    private static final long DOUBLE_MS = 280;
    private static final long LONG_MS = 480;

    private final Runnable longPressTask = () -> {
        if (!dragging && dragListener != null) {
            longPressFired = true;
            dragListener.onLongPress();
        }
    };

    public PetOverlayView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        // 悬浮窗里 SurfaceView 不要置顶：Android 12+ 部分机型（含 vivo/Android 16）
        // 置顶合成会失败，表现为窗口在、画面空。默认让它走窗口挖洞这条标准路径。
        setZOrderOnTop(false);
        setZOrderMediaOverlay(false);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        renderer = new PetRenderer(context.getApplicationContext(), this);
        setRenderer(renderer);
        // 连续渲染：之前 WHEN_DIRTY 只在视频来帧时画（24fps），
        // 压扁/回弹这类变换看着一顿一顿。窗口只有一块 quad，跑满帧很便宜。
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        setPreserveEGLContextOnPause(true);
    }

    /** 扔出去的时候窗口每帧都在挪，暂停连续重绘能省不少主线程 */
    public void pauseRenderModeForMove() {
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    public void resumeRenderMode() {
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    public void setDragListener(DragListener l) {
        this.dragListener = l;
    }

    public void onResumeRenderer() {
        onResume();
        renderer.begin();
    }

    /** 隐藏（离场/切走）时：停重绘 + 暂停播放，但保留播放器与纹理 */
    public void hidePause() {
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        renderer.pausePlayer();
    }

    /** 回来时：恢复重绘 + 续播 */
    public void hideResume() {
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        renderer.resumePlayer();
    }
    public void onPauseRenderer() {
        renderer.end();
        onPause();
    }

    public void tapAction() {
        renderer.bounce();
        renderer.playTap();
    }

    public void playRaw(String asset) {
        renderer.playRaw(asset);
    }

    public void playOnce(String action) {
        renderer.playOnce(action);
    }

    public void startRun(boolean faceRight, float rate) {
        renderer.setFacing(faceRight);
        renderer.startRunAnim(rate);
    }

    public void setFacing(boolean faceRight) {
        renderer.setFacing(faceRight);
    }

    public void stopRun() {
        renderer.stopRunAnim();
    }

    public void setAmbientSeconds(int sec) {
        renderer.setAmbientRange(sec * 1000L, sec * 1400L);
    }

    public void setSharpen(float value) {
        renderer.setSharpen(value);
    }

    public void setSquash(float v) {
        renderer.setSquash(v);
    }

    public void setMood(int mood) {
        renderer.setMood(mood);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getRawX();
                downY = lastY = e.getRawY();
                dragging = false;
                longPressFired = false;
                vx = vy = 0f;
                lastMoveAt = System.currentTimeMillis();
                handler.postDelayed(longPressTask, LONG_MS);
                return true;

            case MotionEvent.ACTION_MOVE: {
                float x = e.getRawX();
                float y = e.getRawY();
                float dx = x - lastX;
                float dy = y - lastY;
                long now = System.currentTimeMillis();
                float dt = Math.max(1, now - lastMoveAt) / 1000f;
                lastMoveAt = now;
                lastX = x;
                lastY = y;
                if (!dragging && Math.hypot(x - downX, y - downY) > dp(7)) {
                    dragging = true;
                    handler.removeCallbacks(longPressTask);
                }
                if (dragging) {
                    // 平滑一下瞬时速度，免得甩出去乱飞
                    vx = vx * 0.55f + (dx / dt) * 0.45f;
                    vy = vy * 0.55f + (dy / dt) * 0.45f;
                    if (dragListener != null) dragListener.onDragBy(dx, dy);
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                handler.removeCallbacks(longPressTask);
                if (dragging) {
                    dragging = false;
                    if (dragListener != null) dragListener.onDragEnd(vx, vy);
                } else if (!longPressFired) {
                    long now = System.currentTimeMillis();
                    if (now - lastTapAt < DOUBLE_MS) {
                        lastTapAt = 0;
                        if (dragListener != null) dragListener.onDoubleTap();
                    } else {
                        lastTapAt = now;
                        if (dragListener != null) dragListener.onTap();
                    }
                }
                return true;
            }

            default:
                return super.onTouchEvent(e);
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
