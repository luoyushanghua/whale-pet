package com.whale.pet;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 单流 alpha 渲染：一段视频左半是画面、右半是遮罩（同帧采集，永远同步）。
 * 解码一次、采样两次，避免双 MediaPlayer 漂移造成的"鬼影黑边"。
 */
public class PetRenderer implements GLSurfaceView.Renderer,
        SurfaceTexture.OnFrameAvailableListener, MediaPlayer.OnCompletionListener {

    /** 窗口画幅（视频左半） */
    public static final int VIDEO_W = 960;
    public static final int VIDEO_H = 720;

    private static final String IDLE = "idle";

    public static final String[] TAP_ACTIONS = {
            "tap_happy", "tap_shy", "tap_angry", "tap_laugh", "tap_jump"
    };

    private static final String VS =
            "attribute vec4 aPos;\n" +
                    "attribute vec2 aTex;\n" +
                    "uniform float uSquash;\n" +
                    "varying vec2 vTex;\n" +
                    "void main() {\n" +
                    "  float sx = 1.0 + 0.06 * uSquash;\n" +
                    "  float sy = 1.0 - 0.09 * uSquash;\n" +
                    "  float y = (aPos.y + 1.0) * sy - 1.0;\n" +
                    "  gl_Position = vec4(aPos.x * sx, y, 0.0, 1.0);\n" +
                    "  vTex = aTex;\n" +
                    "}\n";

    /** 待机时会自己做的事（28 段里挑，扭蛋袋保证不重复） */
    private static final String[] AMBIENT_ACTIONS = {
            "stretch", "yawn", "dance", "think", "purr", "tail_tap", "sleepy", "eat_rice",
            "look_around", "fan", "mirror", "float_step", "butterfly", "doze_wake",
            "startled", "sneak_eat", "gift", "note", "watergun", "crab"
    };
    private static final String[] AMBIENT_HAPPY = {
            "dance", "purr", "tail_tap", "eat_rice", "butterfly", "float_step",
            "note", "watergun", "gift", "fan", "look_around"
    };
    private static final String[] AMBIENT_LOW = {
            "sleepy", "yawn", "think", "stretch", "doze_wake", "mirror"
    };
    private volatile int mood = 70;

    private static final String FS =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTex;\n" +
                    "uniform samplerExternalOES uTex;\n" +
                    "uniform mat4 uM;\n" +
                    "uniform float uFlip;\n" +
                    "uniform float uSharp;\n" +
                    "void main() {\n" +
                    "  vec2 uv = (uM * vec4(vTex, 0.0, 1.0)).xy;\n" +
                    "  float fx = uFlip > 0.5 ? (1.0 - vTex.x) : vTex.x;\n" +
                    "  vec2 cUV = vec2(fx * 0.5, uv.y);\n" +
                    "  vec3 c = texture2D(uTex, cUV).rgb;\n" +
                    "  float m = texture2D(uTex, vec2(fx * 0.5 + 0.5, uv.y)).r;\n" +
                    // 锐化：素材是 360p 放大的，加一点 unsharp 让边缘不那么糊
                    "  vec2 px = vec2(0.0016, 0.0028 * 0.75);\n" +
                    "  vec3 blur = (texture2D(uTex, cUV + vec2(px.x, 0.0)).rgb\n" +
                    "             + texture2D(uTex, cUV - vec2(px.x, 0.0)).rgb\n" +
                    "             + texture2D(uTex, cUV + vec2(0.0, px.y)).rgb\n" +
                    "             + texture2D(uTex, cUV - vec2(0.0, px.y)).rgb) * 0.25;\n" +
                    "  c = clamp(c + uSharp * (c - blur), 0.0, 1.0);\n" +
                    "  float a = smoothstep(0.02, 0.18, m);\n" +
                    "  c = max(c - vec3(0.008), vec3(0.0));\n" +
                    "  gl_FragColor = vec4(c * a, a);\n" +
                    "}\n";

    private final Context ctx;
    private final GLSurfaceView view;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private int program;
    private int aPos, aTex, uTex, uM, uSquash, uFlip, uSharpLoc;
    private volatile float flip;
    private volatile float sharp = 0.15f;

    private int tex;
    private SurfaceTexture st;
    private MediaPlayer player;

    private final float[] matrix = new float[16];
    private volatile boolean newFrame;
    private volatile boolean running;
    private volatile float squash;

    private String currentAction = "";
    private int texFrames;
    private int retryCount;
    private int actionToken;

    private volatile long ambientMinMs = 20000L;
    private volatile long ambientMaxMs = 45000L;

    private FloatBuffer quadPos;
    private FloatBuffer quadTex;

    public PetRenderer(Context ctx, GLSurfaceView view) {
        this.ctx = ctx;
        this.view = view;
        for (int i = 0; i < 16; i++) {
            matrix[i] = i % 5 == 0 ? 1f : 0f;
        }
    }

    public void begin() {
        running = true;
        if (st == null) {
            // 还没建好 SurfaceTexture，等 onSurfaceCreated 里再启动
            view.requestRender();
            return;
        }
        view.queueEvent(() -> {
            if (currentAction.isEmpty()) {
                play(IDLE, true);
            } else if (player != null && !player.isPlaying()) {
                player.start();
            }
        });
        view.requestRender();
    }

    /** 隐藏时只暂停播放，不释放：释放后纹理可能失效，回来就再也播不动 */
    public void pausePlayer() {
        view.queueEvent(() -> {
            try {
                if (player != null && player.isPlaying()) player.pause();
            } catch (Exception ignored) {
            }
        });
    }

    /** 恢复播放：优先续播，播放器不在了就重新起待机 */
    public void resumePlayer() {
        view.queueEvent(() -> {
            try {
                if (player != null) {
                    player.start();
                    return;
                }
            } catch (Exception ignored) {
            }
            play(IDLE, true);
        });
    }
    public void end() {
        running = false;
        runningAnim = false;
        view.queueEvent(() -> {
            runningAnim = false;
            releasePlayer();
        });
    }

    /** 测试：直接播原始 webm，看解码器吐不吐 alpha */
    public void playRaw(final String asset) {
        texFrames = 0;
        view.queueEvent(() -> {
            try {
                if (player == null) player = new MediaPlayer(); else player.reset();
                AssetFileDescriptor afd = ctx.getAssets().openFd(asset);
                player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                player.setSurface(new Surface(st));
                player.setLooping(true);
                player.setVolume(0f, 0f);
                player.prepare();
                player.start();
                currentAction = "raw";
            } catch (Exception ignored) {}
        });
    }

    /** 随机播一个点击动作，放完回待机 */
    public void playTap() {
        String action = TAP_ACTIONS[random.nextInt(TAP_ACTIONS.length)];
        view.queueEvent(() -> play(action, false));
    }

    /** 播一次指定动作（外部触发：跑动 / 告别 / 欢迎） */
    public void playOnce(final String action) {
        view.queueEvent(() -> play(action, false));
    }

    /** 跑动循环：已经在跑就不重启动画，免得每次改目标都"顿一下" */
    public void startRunAnim() {
        startRunAnim(1f);
    }

    /** rate = 步频倍率，速度越快跑得越快 */
    public void startRunAnim(final float rate) {
        view.queueEvent(() -> {
            // 不只看标志位：状态可能被 end()/begin() 或动作切换改掉
            if (!runningAnim || !"run".equals(currentAction)) {
                runningAnim = true;
                play("run", true, rate);
            }
        });
    }

    /** 朝向：true = 朝右（素材本身朝左，所以要水平镜像） */
    public void setFacing(boolean faceRight) {
        float f = faceRight ? 1f : 0f;
        if (f != flip) {
            flip = f;
            view.requestRender();
        }
    }

    public void stopRunAnim() {
        view.queueEvent(() -> {
            if (runningAnim) {
                runningAnim = false;
                play(IDLE, true);
            }
        });
    }

    private volatile boolean runningAnim;

    /** 播指定动作；loop=false 时放完自动回待机 */
    public void play(final String action, final boolean loop) {
        play(action, loop, 1f);
    }

    public void play(final String action, final boolean loop, final float rate) {
        // 恢复显示时 GL 线程/纹理可能还没就绪，直接 return 会永远停住
        // （表现为「用了一个动作、回到桌面后卡死」）。这里改成有限次重试。
        if (!running || st == null) {
            if (st == null && retryCount < 20) {
                retryCount++;
                android.util.Log.d("whalepet", "play deferred " + action + " st=null retry=" + retryCount);
                main.postDelayed(() -> view.queueEvent(() -> play(action, loop, rate)), 250);
            }
            return;
        }
        retryCount = 0;
        final int token = ++actionToken;
        try {
            if (player == null) {
                player = new MediaPlayer();
                player.setOnCompletionListener(this);
            } else {
                player.reset();
                player.setOnCompletionListener(this);
            }
            // 英文 id 走映射表（idle → 待机呼吸休闲.mp4）；中文名直接用
            String file = ActionMap.has(action) ? ActionMap.file(action) : action + ".mp4";
            android.util.Log.d("whalepet", "play " + action + " file=" + file);
            AssetFileDescriptor afd = ctx.getAssets().openFd("actions/" + file);
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            player.setSurface(new Surface(st));
            player.setLooping(loop);
            player.setVolume(0f, 0f);
            player.prepare();
            if (rate > 1.01f || rate < 0.99f) {
                try {
                    player.setPlaybackParams(
                            new android.media.PlaybackParams().setSpeed(rate));
                } catch (Exception ignored) {
                }
            }
            player.start();
            currentAction = action;

            main.removeCallbacksAndMessages(null);
            if (!loop) {
                int dur = Math.max(600, player.getDuration() - 90);
                main.postDelayed(() -> {
                    if (token == actionToken) {
                        view.queueEvent(() -> play(IDLE, true));
                    }
                }, dur);
            } else {
                // 动画链（学 dsh-pet）：每段播完立即按权重抽下一个，永不空档。
                // idle 10 / turn 5 / move 5 / 其它动作 80
                int dur = Math.max(1000, player.getDuration() - 60);
                main.postDelayed(() -> {
                    if (token != actionToken) return;
                    int r = random.nextInt(100);
                    if (r < 10) {
                        view.queueEvent(() -> play(IDLE, true));
                    } else if (r < 15) {
                        view.queueEvent(() -> play(nextTurn(), false));
                    } else if (r < 20) {
                        view.queueEvent(() -> play(nextMove(), false));
                    } else {
                        view.queueEvent(() -> play(nextAmbient(), false));
                    }
                }, dur);
            }
        } catch (Exception e) {
            currentAction = "";
            android.util.Log.e("whalepet", "play failed: " + action + " -> " + e);
        }
    }

    private final java.util.List<String> bag = new java.util.ArrayList<>();

    /** 扭蛋袋：一轮里不重复，抽完再洗牌 */
    private String nextTurn() {
        String[] pool = {"tap_shy", "tap_angry", "look_around"};
        return pool[random.nextInt(pool.length)];
    }

    private String nextMove() {
        String[] pool = {"run", "float_step", "crab"};
        return pool[random.nextInt(pool.length)];
    }

    private String[] allActionPool;

    /** 直接读 assets/actions 下的全部中文素材，避免只用到映射表那 28 段 */
    private String[] allActions() {
        if (allActionPool != null) return allActionPool;
        java.util.ArrayList<String> list = new java.util.ArrayList<>();
        try {
            String[] names = ctx.getAssets().list("actions");
            if (names != null) {
                for (String n : names) {
                    if (!n.endsWith(".mp4")) continue;
                    String base = n.substring(0, n.length() - 4);
                    if (base.equals("idle") || base.equals("run")) continue;
                    list.add(base);
                }
            }
        } catch (Exception ignored) {
        }
        if (list.isEmpty()) list.add("look_around");
        allActionPool = list.toArray(new String[0]);
        return allActionPool;
    }

    private String nextAmbient() {
        String[] pool = allActions();
        if (bag.isEmpty()) {
            java.util.Collections.addAll(bag, pool);
            java.util.Collections.shuffle(bag, random);
        }
        return bag.remove(0);
    }

    /** 待机时自己找点事做 */
    private void scheduleAmbient(final int token) {
        long span = Math.max(1000L, ambientMaxMs - ambientMinMs);
        float factor = mood >= 70 ? 0.8f : mood <= 35 ? 1.35f : 1f;
        long delay = Math.max(4000L, (long) ((ambientMinMs + random.nextDouble() * span) * factor));
        main.postDelayed(() -> {
            if (token == actionToken && IDLE.equals(currentAction)) {
                if (random.nextInt(4) == 0) {
                    // 偶尔只是转身看看，不做什么
                    flip = flip > 0.5f ? 0f : 1f;
                    view.requestRender();
                } else {
                    String action = nextAmbient();
                    view.queueEvent(() -> play(action, false));
                }
            }
        }, delay);
    }

    /** 活跃度：待机多久自己动一次（毫秒区间） */
    public void setAmbientRange(long min, long max) {
        ambientMinMs = Math.max(1000L, min);
        ambientMaxMs = Math.max(ambientMinMs + 1000L, max);
    }

    /** 心情决定待机时做什么：低落就发呆打瞌睡，开心就跳舞 */
    public void setSquash(float v) {
        squash = Math.max(0.8f, Math.min(1.2f, v));
        view.requestRender();
    }

    public void setSharpen(float v) {
        sharp = Math.max(0f, Math.min(1.2f, v));
        view.requestRender();
    }

    public void setMood(int m) {
        mood = Math.max(0, Math.min(100, m));
    }

    public void bounce() {
        squash = 1f;
        final long t0 = System.currentTimeMillis();
        view.post(new Runnable() {
            @Override
            public void run() {
                long dt = System.currentTimeMillis() - t0;
                if (dt > 380) {
                    squash = 0f;
                    view.requestRender();
                    return;
                }
                float p = dt / 380f;
                squash = (float) (Math.exp(-6 * p) * Math.cos(p * 18));
                view.requestRender();
                view.postDelayed(this, 16);
            }
        });
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        view.queueEvent(() -> play(IDLE, true));
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        program = buildProgram(VS, FS);
        aPos = GLES20.glGetAttribLocation(program, "aPos");
        aTex = GLES20.glGetAttribLocation(program, "aTex");
        uTex = GLES20.glGetUniformLocation(program, "uTex");
        uM = GLES20.glGetUniformLocation(program, "uM");
        uSquash = GLES20.glGetUniformLocation(program, "uSquash");
        uFlip = GLES20.glGetUniformLocation(program, "uFlip");
        uSharpLoc = GLES20.glGetUniformLocation(program, "uSharp");

        quadPos = floatBuffer(new float[]{-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f});
        quadTex = floatBuffer(new float[]{0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f});

        tex = createExternalTexture();
        st = new SurfaceTexture(tex);
        st.setDefaultBufferSize(VIDEO_W * 2, VIDEO_H);
        st.setOnFrameAvailableListener(this);

        if (running) {
            play(currentAction.isEmpty() ? IDLE : currentAction, true);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        // 自适应：按画面半边比例（4:3）做居中 letterbox，
        // 窗口比例怎么变都不拉伸画面，多余部分留透明。
        float video = VIDEO_W / (float) VIDEO_H;      // 960 / 720 = 1.333
        float view = width / (float) height;
        if (view > video) {
            int w = Math.round(height * video);
            GLES20.glViewport((width - w) / 2, 0, w, height);
        } else {
            int h = Math.round(width / video);
            GLES20.glViewport(0, (height - h) / 2, width, h);
        }
    }

    private int frameCount;
    private long fpsAt;
    private int fpsFrames;

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        fpsFrames++;
        long nowMs = android.os.SystemClock.uptimeMillis();
        if (fpsAt == 0) fpsAt = nowMs;
        if (nowMs - fpsAt >= 2000) { android.util.Log.d("whalepet", "fps " + (fpsFrames * 1000 / (nowMs - fpsAt))); fpsAt = nowMs; fpsFrames = 0; }
        if (program == 0 || st == null) return;

        if (newFrame) {
            st.updateTexImage();
            st.getTransformMatrix(matrix);
            newFrame = false;
        }

        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex);
        GLES20.glUniform1i(uTex, 0);
        GLES20.glUniformMatrix4fv(uM, 1, false, matrix, 0);
        GLES20.glUniform1f(uSquash, squash);
        GLES20.glUniform1f(uFlip, flip);
        GLES20.glUniform1f(uSharpLoc, sharp);

        quadPos.position(0);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quadPos);
        GLES20.glEnableVertexAttribArray(aPos);
        quadTex.position(0);
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, quadTex);
        GLES20.glEnableVertexAttribArray(aTex);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aTex);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        newFrame = true;
        view.requestRender();
    }

    private void releasePlayer() {
        try {
            if (player != null) {
                player.stop();
                player.release();
            }
        } catch (Exception ignored) {
        }
        player = null;
        currentAction = "";
    }

    private int createExternalTexture() {
        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, t[0]);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        return t[0];
    }

    private int buildProgram(String vs, String fs) {
        int v = compile(GLES20.GL_VERTEX_SHADER, vs);
        int f = compile(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        return p;
    }

    private int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        return s;
    }

    private FloatBuffer floatBuffer(float[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(data);
        fb.position(0);
        return fb;
    }
}
