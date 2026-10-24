package com.whale.pet;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class PetService extends Service implements PetOverlayView.DragListener {

    public static volatile boolean running = false;
    public static final String ACTION_RELOAD = "com.whale.pet.action.RELOAD";
    public static final String ACTION_STOP = "com.whale.pet.action.STOP";
    public static final String ACTION_MUTE = "com.whale.pet.action.MUTE";
    public static final String ACTION_SETTINGS = "com.whale.pet.action.SETTINGS";
    public static final String ACTION_UI_OPEN = "com.whale.pet.action.UI_OPEN";
    public static final String ACTION_SAY = "com.whale.pet.action.SAY";
    public static final String ACTION_NOTIFY = "com.whale.pet.action.NOTIFY";
    public static final String ACTION_FOCUS = "com.whale.pet.action.FOCUS";
    public static final String ACTION_PLAY = "com.whale.pet.action.PLAY";
    public static final String ACTION_FPS = "com.whale.pet.action.FPS";
    public static final String ACTION_TEST = "com.whale.pet.action.TEST";
    public static final String ACTION_UI_CLOSE = "com.whale.pet.action.UI_CLOSE";

    private static final String CHANNEL_ID = "pet_float";

    private WindowManager wm;
    private PetOverlayView petView;
    private WindowManager.LayoutParams lp;

    private View touchLayer;
    private WindowManager.LayoutParams touchLp;

    private TextView bubble;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;

    private int screenW;
    private int screenH;
    private int petW;
    private int petH;
    private boolean hiddenForGame;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        createChannel();
        startAsForeground();
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        running = true;
        prefs.setWasRunning(true);
        showPet();
        // 从 App 里被召唤的：先躲起来，等用户真的退回桌面再出来
        if (PetApp.isUiVisible()) {
            uiHidden = true;
            hidePet();
        }
        registerScreenReceiver();
        handler.postDelayed(gameWatch, 4000);
        handler.postDelayed(chatter, 40000);
        handler.postDelayed(() -> {
            if (!uiHidden) {
                showBubble(prefs.daysTogether() <= 1 ? "初次见面，请多指教～" : "我回来啦～", 2400);
            }
        }, 900);
    }

    private boolean screenOn = true;
    private boolean uiHidden;
    private long focusUntil;
    private long screenOnSince = SystemClock.uptimeMillis();
    private boolean sedentaryWarned;
    private BroadcastReceiver screenReceiver;

    /** 锁屏就停解码和渲染，省电 */
    private void registerScreenReceiver() {
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    screenOn = false;
                    stopVoice();
                    if (petView != null) petView.onPauseRenderer();
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    screenOnSince = SystemClock.uptimeMillis();
                    screenOn = true;
                    if (petView != null) petView.onResumeRenderer();
                } else if (ACTION_STOP.equals(action)) {
                    prefs.setWasRunning(false);
                    PetService.this.stopSelf();
                } else if (ACTION_MUTE.equals(action)) {
                    prefs.setMuted(!prefs.muted());
                    refreshNotification();
                } else if (ACTION_SETTINGS.equals(action)) {
                    startActivity(new Intent(PetService.this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } else if (ACTION_SAY.equals(action)) {
                    String t = intent.getStringExtra("text");
                    if (t != null) showBubble(t, 3200);
                } else if (ACTION_FPS.equals(action)) {
                    applyFps();
                } else if (ACTION_PLAY.equals(action)) {
                    String nm = intent.getStringExtra("name");
                    if (nm != null) {
                        // 在 App 里选的动作，回到桌面才看得到：先把窗口恢复出来
                        if (uiHidden || hiddenForGame) {
                            uiHidden = false;
                            hiddenForGame = false;
                            try {
                                showPet();
                            } catch (Exception ignored) {
                            }
                        }
                        if (petView != null) {
                            try {
                                petView.onResumeRenderer();
                            } catch (Exception ignored) {
                            }
                            // 延后 400ms：等面板关闭触发的 UI_CLOSE / idle 重播走完，
                            // 否则刚选的动作会被待机动画立刻覆盖（表现为"点了没反应"）
                            final String act = nm;
                            handler.postDelayed(() -> {
                                if (petView != null) petView.playOnce(act);
                            }, 400);
                        }
                    }
                } else if (ACTION_NOTIFY.equals(action)) {
                    String t = intent.getStringExtra("text");
                    if (t != null && petView != null && !uiHidden && !hiddenForGame) {
                        showBubble(t, 3200);
                        petView.playOnce("startled");
                        prefs.bumpMood(2);
                        // 可爱提示音：@你 用三连音，普通消息用两连音
                        playChime(t.contains("@你"));
                    }
                } else if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                    // 环境感知：插上电 → 慵懒/得意
                    if (petView != null && !uiHidden && !hiddenForGame) {
                        long now = SystemClock.uptimeMillis();
                        if (now - lastPowerAt > 60000) {
                            lastPowerAt = now;
                            petView.playOnce("stretch");
                            showBubble("插上电啦，我可以慢慢吃白饭了～", 2600);
                            prefs.bumpCounter("react_power");
                        }
                    }
                } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                    if (petView != null && !uiHidden && !hiddenForGame) {
                        long now = SystemClock.uptimeMillis();
                        if (now - lastPowerAt > 60000) {
                            lastPowerAt = now;
                            petView.playOnce("startled");
                            showBubble("拔啦？电量要靠自己省着用了…", 2600);
                            prefs.bumpCounter("react_unplug");
                        }
                    }
                } else if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
                    boolean plugged = intent.getIntExtra("state", 0) == 1;
                    if (petView != null && !uiHidden && !hiddenForGame) {
                        petView.playOnce(plugged ? "tap_happy" : "look_around");
                        showBubble(plugged ? "戴耳机啦？那我不吵你，就陪着" : "摘耳机啦，那我说话咯", 2400);
                        prefs.bumpCounter(plugged ? "react_headset_in" : "react_headset_out");
                    }
                } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                    boolean on = intent.getBooleanExtra("state", false);
                    if (on && petView != null && !uiHidden && !hiddenForGame) {
                        petView.playOnce("look_around");
                        showBubble("要飞走啦？记得回来找我", 2400);
                    }
                } else if (Intent.ACTION_BATTERY_LOW.equals(action)) {
                    if (petView != null && !uiHidden) showBubble("呜…快没电了，充一下嘛", 2600);
                } else if (Intent.ACTION_BATTERY_OKAY.equals(action)) {
                    if (petView != null && !uiHidden) showBubble("电回来啦～", 2000);
                } else if (ACTION_UI_OPEN.equals(action)) {
                    android.util.Log.i("whalepet", "UI_OPEN received");
                    if (!uiHidden) {
                        uiHidden = true;
                        hidePet();
                    }
                } else if (ACTION_UI_CLOSE.equals(action)) {
                    if (uiHidden) {
                        uiHidden = false;
                        showPet();
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(ACTION_STOP);
        filter.addAction(ACTION_MUTE);
        filter.addAction(ACTION_SETTINGS);
        filter.addAction(ACTION_UI_OPEN);
        filter.addAction(ACTION_UI_CLOSE);
        filter.addAction(ACTION_SAY);
        filter.addAction(ACTION_NOTIFY);
        filter.addAction(ACTION_PLAY);
        filter.addAction(ACTION_FPS);
        filter.addAction(ACTION_TEST);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        // Android 13+ 动态注册必须声明导出性，否则 SecurityException 直接崩服务
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }
    }

    /** 由 Mind 决定她现在说不说话：时间、电量、多久没理她、前台是什么应用 */
    private final Runnable chatter = new Runnable() {
        @Override
        public void run() {
            try {
                thinkAndMaybeSpeak();
            } catch (Exception ignored) {
            }
            handler.postDelayed(this, 60000 + random.nextInt(60000));
        }
    };

    private void thinkAndMaybeSpeak() {
        if (petView == null || hiddenForGame || moving || flinging || !screenOn) return;
        if (SystemClock.uptimeMillis() < focusUntil) return;

        String pkg = foregroundPackage();
        if (Mind.quietApp(pkg)) return;   // 你在刷视频/聊天，她不打扰

        String today = Mind.dayKey();
        boolean morning = !today.equals(prefs.flag("mind_morning"));
        boolean night = !today.equals(prefs.flag("mind_night"));
        boolean meal = !today.equals(prefs.flag("mind_meal"));
        int since = prefs.lastInteraction() == 0 ? 999
                : (int) ((System.currentTimeMillis() - prefs.lastInteraction()) / 60000);

        int battery = 100;
        boolean charging = false;
        try {
            android.os.BatteryManager bm =
                    (android.os.BatteryManager) getSystemService(BATTERY_SERVICE);
            if (bm != null) {
                battery = bm.getIntProperty(
                        android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
                charging = bm.isCharging();
            }
        } catch (Exception ignored) {
        }

        int hour = Mind.hourNow();
        Mind.Say say = Mind.think(hour, charging, battery, since,
                morning, night, meal, prefs.mood(), random);
        if (say == null) return;

        showBubble(say.text, 3000);
        if (say.voice != null) speak(say.voice);
        if (say.action != null) petView.playOnce(say.action);

        if (hour >= 6 && hour < 10) prefs.setFlag("mind_morning", today);
        else if (hour >= 23 || hour < 1) prefs.setFlag("mind_night", today);
        else if ((hour >= 11 && hour < 13) || (hour >= 17 && hour < 19)) {
            prefs.setFlag("mind_meal", today);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_FOCUS.equals(intent.getAction())) {
            int min = Math.max(5, Math.min(120, intent.getIntExtra("minutes", 25)));
            focusUntil = SystemClock.uptimeMillis() + min * 60000L;
            showBubble("好，这 " + min + " 分钟我不吵你～", 2400);
            handler.postDelayed(() -> {
                focusUntil = 0;
                showBubble("时间到啦，起来伸个懒腰？", 2800);
                if (petView != null) petView.playOnce("stretch");
            }, min * 60000L);
            return START_STICKY;
        }
        if (intent != null && ACTION_UI_OPEN.equals(intent.getAction())) {
            if (!uiHidden) {
                uiHidden = true;
                hidePet();
            }
            return START_STICKY;
        }
        if (intent != null && ACTION_UI_CLOSE.equals(intent.getAction())) {
            if (uiHidden) {
                uiHidden = false;
                showPet();
            }
            return START_STICKY;
        }
        if (intent != null && ACTION_RELOAD.equals(intent.getAction())) {
            if (intent.getBooleanExtra("reset", false)) {
                resetPosition();
            }
            applySettings();
        }
        return START_STICKY;
    }

    /** 参数一改就调用：大小 / 透明度 / 交互 / 活跃度 */
    private void applySettings() {
        if (petView == null || lp == null) return;
        int w = Math.max(dp(80), Math.round(Math.min(screenW, screenH) * prefs.sizePct() / 100f));
        int h = Math.round(w * 720f / 960f);
        petW = w;
        petH = h;
        android.util.Log.d("whalepet", "window " + w + "x" + h + " screen "
                + screenW + "x" + screenH + " ratio "
                + String.format("%.3f", w / (float) h));
        if (!hiddenForGame) {
            lp.width = w;
            lp.height = h;
            lp.x = Math.min(lp.x, screenW - w);
            lp.y = Math.min(lp.y, screenH - h);
        }
        lp.alpha = prefs.alphaPct() / 100f;
        if (prefs.clickThrough()) {
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        try {
            wm.updateViewLayout(petView, lp);
        } catch (Exception ignored) {
        }
        petView.setAmbientSeconds(prefs.ambientSec());
        if (petView != null) petView.setSharpen(prefs.sharpen() / 100f);
        if (prefs.interactive()) {
            enableTouchLayer();
        } else {
            disableTouchLayer();
        }
    }

    private void resetPosition() {
        if (lp == null) return;
        int w = Math.max(dp(80), Math.round(Math.min(screenW, screenH) * prefs.sizePct() / 100f));
        int h = Math.round(w * 720f / 960f);
        lp.width = w;
        lp.height = h;
        lp.x = screenW - w - dp(6);
        lp.y = Math.max(0, screenH - h - dp(10));
    }

    private void startAsForeground() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    private Notification buildNotification() {        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE : 0;

        PendingIntent stop = PendingIntent.getBroadcast(this, 1,
                new Intent(ACTION_STOP).setPackage(getPackageName()), flags);
        PendingIntent mute = PendingIntent.getBroadcast(this, 2,
                new Intent(ACTION_MUTE).setPackage(getPackageName()), flags);
        PendingIntent settings = PendingIntent.getBroadcast(this, 3,
                new Intent(ACTION_SETTINGS).setPackage(getPackageName()), flags);

        b.setContentTitle("鲸鱼娘在桌面上")
                .setContentText(prefs.muted() ? "已静音 · 她还在陪着你" : "点这里可以叫她回去")
                .setSmallIcon(android.R.drawable.ic_menu_myplaces)
                .setOngoing(true)
                .addAction(0, prefs.muted() ? "取消静音" : "静音", mute)
                .addAction(0, "收回", stop)
                .addAction(0, "设置", settings);
        return b.build();
    }

    private void refreshNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(1, buildNotification());
        } catch (Exception ignored) {
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "桌面陪伴", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    // ---------------- 悬浮窗 ----------------

    private void showPet() {
        if (wm == null) {
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm == null) return;
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            screenW = dm.widthPixels;
            screenH = dm.heightPixels;
        }

        // 已经在（可能是缩成 1px 的隐身状态）→ 还原
        if (petView != null && lp != null) {
            lp.width = petW;
            lp.height = petH;
            lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            if (lp.x < 0 || lp.y < 0) {
                lp.x = screenW - petW - dp(6);
                lp.y = Math.round(screenH * 0.42f);
            }
            try {
                wm.updateViewLayout(petView, lp);
            } catch (Exception ignored) {
            }
            petView.onResumeRenderer();
            if (prefs.interactive()) enableTouchLayer();
            return;
        }

        int w = Math.max(dp(80), Math.round(Math.min(screenW, screenH) * prefs.sizePct() / 100f));
        int h = Math.round(w * 720f / 960f);
        petW = w;
        petH = h;
        android.util.Log.d("whalepet", "window " + w + "x" + h + " screen "
                + screenW + "x" + screenH + " ratio "
                + String.format("%.3f", w / (float) h));

        petView = new PetOverlayView(this);
        petView.setDragListener(this);
        petView.setAmbientSeconds(prefs.ambientSec());
        if (petView != null) petView.setSharpen(prefs.sharpen() / 100f);
        petView.setMood(prefs.mood());
        petView.setSharpen(prefs.sharpen() / 100f);

        lp = new WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.alpha = prefs.alphaPct() / 100f;
        if (prefs.clickThrough()) {
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        lp.x = screenW - w - dp(6);
        // 脚踩桌面底边：底部留一点边距，而不是浮在屏幕中间
        lp.y = Math.max(0, screenH - h - dp(10));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        // 先铺全屏触摸层，再放宠物窗口——后加的在上面，她才能被点到
        if (prefs.interactive()) enableTouchLayer();

        try {
            wm.addView(petView, lp);
        } catch (Exception e) {
            stopSelf();
            return;
        }
        // 落地位置统一走 resetPosition()，避免别处的旧公式覆盖贴底值
        resetPosition();
        posX = lp.x;
        posY = lp.y;
        try {
            wm.updateViewLayout(petView, lp);
        } catch (Exception ignored) {
        }
        petView.onResumeRenderer();
        startIdleFloat();
    }

    // ---------------- 待机微动：静止时轻微上下浮动，让人看着不那么僵 ----------------

    private boolean idleFloating;
    private boolean dragging;

    private final Choreographer.FrameCallback idleFloatFrame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (petView == null || lp == null) return;
            if (moving || flinging || dragging || lp.width <= 1) {
                idleFloating = false;
                lp.y = Math.round(posY);
                try {
                    wm.updateViewLayout(petView, lp);
                } catch (Exception ignored) {
                }
                Choreographer.getInstance().postFrameCallback(this);
                return;
            }
            long t = SystemClock.uptimeMillis();
            float phase = (float) (t / 477.0);          // 周期约 3.5 秒
            float dy = (float) Math.sin(phase) * dp(5);
            float sq = 1f + (float) Math.sin(phase) * 0.025f;
            lp.y = Math.round(posY + dy);
            try {
                wm.updateViewLayout(petView, lp);
                petView.setSquash(sq);
            } catch (Exception ignored) {
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private void startIdleFloat() {
        if (idleFloating) return;
        idleFloating = true;
        Choreographer.getInstance().postFrameCallback(idleFloatFrame);
    }

    /**
     * 隐身：不销毁窗口，只缩成 1px 并停掉 GL/解码。
     * 留着窗口是为了不被系统当成"缓存进程"冻住——冻住了就再也醒不过来。
     */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        try {
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            screenW = dm.widthPixels;
            screenH = dm.heightPixels;
            if (petView != null && lp != null) {
                int w = Math.max(dp(80), Math.round(Math.min(screenW, screenH) * prefs.sizePct() / 100f));
                int h = Math.round(w * 720f / 960f);
                lp.width = w;
                lp.height = h;
                lp.x = clamp(lp.x, 0, Math.max(0, screenW - w));
                lp.y = clamp(lp.y, 0, Math.max(0, screenH - h));
                posX = lp.x;
                posY = lp.y;
                wm.updateViewLayout(petView, lp);
            }
            if (panel != null) panel.relayout();
        } catch (Exception ignored) {
        }
    }
    private void hidePet() {
        if (petView == null || lp == null) return;
        petView.onPauseRenderer();
        disableTouchLayer();
        lp.width = 1;
        lp.height = 1;
        lp.x = -2;
        lp.y = -2;
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try {
            wm.updateViewLayout(petView, lp);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDragBy(float dx, float dy) {
        if (lp == null || petView == null) return;
        dragging = true;
        stopMoving();
        lp.x = clamp(lp.x + Math.round(dx), 0, screenW - lp.width);
        lp.y = clamp(lp.y + Math.round(dy), 0, screenH - lp.height);
        // 同步基准，否则松手后待机浮动会把她拉回旧位置
        posX = lp.x;
        posY = lp.y;
        try {
            wm.updateViewLayout(petView, lp);
        } catch (Exception ignored) {
        }
    }

    private static final String[] TAP_LINES = {
            "干嘛呀～", "哦鲸鲸……", "先说要干嘛", "戳我干嘛，我又不会跑", "Ciallo～"
    };
    private static final String[] FEED_LINES = {
            "又是白饭？……也行", "嗯，勉强好吃", "还、还要一碗！", "token 也给我来点"
    };
    private static final String[] PET_LINES = {
            "呜……别摸头啦", "就、就一下哦", "呼噜……", "再摸要生气了（才没有）"
    };
    private static final String[] CHATTER = {
            "今天也一起加油吧", "白饭……想吃白饭", "你忙你的，我看着", "记得喝水哦",
            "有点困了……", "别一直盯着屏幕啦", "我一直都在的"
    };

    private String pick(String[] pool) {
        return pool[random.nextInt(pool.length)];
    }

    /** 返回 {台词, 语音文件 id}，台词池和语音文件按顺序一一对应 */
    private String[] pickLine(String[] pool, String prefix) {
        int i = random.nextInt(pool.length);
        return new String[]{pool[i], prefix + (i + 1)};
    }

    /** 只从某一段里挑（用来做"心情低落时说的话"） */
    private String[] pickLineRange(String[] pool, String prefix, int from, int to) {
        int i = from + random.nextInt(Math.max(1, to - from));
        return new String[]{pool[i], prefix + (i + 1)};
    }

    private MediaPlayer voicePlayer;

    private void speak(String id) {
        try {
            if (voicePlayer == null) {
                voicePlayer = new MediaPlayer();
            } else {
                voicePlayer.reset();
            }
            AssetFileDescriptor afd = getAssets().openFd("voice/" + id + ".mp3");
            voicePlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            voicePlayer.prepare();
            voicePlayer.start();
        } catch (Exception ignored) {
        }
    }

    private void stopVoice() {
        try {
            if (voicePlayer != null && voicePlayer.isPlaying()) voicePlayer.stop();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onTap() {
        // 小情绪：2 秒内连戳 5 次 → 哼气（冷却 60 秒）
        long now = SystemClock.uptimeMillis();
        if (now - lastTapAt > 5000) rapidTaps = 0;
        lastTapAt = now;
        rapidTaps++;
        if (rapidTaps >= 5 && now - lastAngryAt > 60000) {
            lastAngryAt = now;
            rapidTaps = 0;
            if (petView != null) petView.playOnce("tap_angry");
            showBubble("哼，不理你了", 2200);
            prefs.bumpMood(-6);
            return;
        }
        if (petView != null) petView.tapAction();
        prefs.bumpInteraction();
        prefs.touchInteraction();
        prefs.bumpMood(5);
        if (petView != null) petView.setMood(prefs.mood());
        String[] line = pickLine(TAP_LINES, "tap");
        showBubble(line[0], 2000);
        speak(line[1]);
    }

    private PetPanel panel;
    private long lastPowerAt;
    private long lastHappyAt;
    private long lastTapAt;
    private long lastAngryAt;
    private int rapidTaps;

    @Override
    public void onDoubleTap() {
        // 双击：投喂
        if (petView == null) return;
        petView.playOnce("eat_rice");
        prefs.bumpInteraction();
        prefs.bumpMood(7);
        if (petView != null) petView.setMood(prefs.mood());
        String[] line = pickLine(FEED_LINES, "feed");
        showBubble(line[0], 2200);
        speak(line[1]);
    }

    @Override
    public void onLongPress() {
        // 长按：打开悬浮动作面板（摸头/投喂在面板底部）
        if (petView == null) return;
        togglePanel();
    }

    private void onLongPressPetLegacy() {
        long now = SystemClock.uptimeMillis();
        // 小情绪：心情很好且没刚触发过 → 开心蹦一下（冷却 90 秒）
        if (prefs.mood() >= 80 && now - lastHappyAt > 90000) {
            lastHappyAt = now;
            petView.playOnce("tap_jump");
            showBubble("嘿嘿，今天心情好", 2200);
            prefs.bumpInteraction();
            prefs.touchInteraction();
            prefs.bumpMood(6);
            petView.setMood(prefs.mood());
            return;
        }
        petView.playOnce("purr");
        try {
            android.os.Vibrator vib = (android.os.Vibrator)
                    getSystemService(Context.VIBRATOR_SERVICE);
            if (vib != null) vib.vibrate(18);
        } catch (Exception ignored) {
        }
        String[] line = pickLine(PET_LINES, "pet");
        showBubble(line[0], 2200);
        prefs.bumpInteraction();
        prefs.touchInteraction();
        prefs.bumpMood(9);
        if (petView != null) petView.setMood(prefs.mood());
        speak(line[1]);
    }

    /** 松手：按甩出去的速度做抛物线，落地弹两下再贴边 */
    @Override
    public void onDragEnd(float vx, float vy) {
        if (petView == null || lp == null || hiddenForGame) return;
        dragging = false;
        stopMoving();
        posX = lp.x;
        posY = lp.y;
        velX = Math.max(-2400f, Math.min(2400f, vx));
        velY = Math.max(-2400f, Math.min(2400f, vy));
        if (Math.hypot(velX, velY) < 260f) {
            // 慢放：就放在松手的位置，不做吸附
            startIdleFloat();
            return;
        }
        gravity = 2600f;
        flinging = true;
        lastFlingTick = SystemClock.uptimeMillis();
        handler.post(flingStep);
    }

    private final Runnable flingStep = new Runnable() {
        @Override
        public void run() {
            if (!flinging || petView == null) return;
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(0.032f, (now - lastFlingTick) / 1000f);
            lastFlingTick = now;

            velY += gravity * dt;
            posX += velX * dt;
            posY += velY * dt;

            int maxX = screenW - lp.width;
            int maxY = screenH - lp.height - dp(56);
            boolean bounced = false;

            if (posX < 0) {
                posX = 0;
                velX = -velX * 0.5f;
                bounced = true;
            } else if (posX > maxX) {
                posX = maxX;
                velX = -velX * 0.5f;
                bounced = true;
            }
            if (posY < dp(24)) {
                posY = dp(24);
                velY = -velY * 0.42f;
                bounced = true;
            } else if (posY > maxY) {
                posY = maxY;
                velY = -velY * 0.42f;
                velX *= 0.82f;
                bounced = true;
            }

            lp.x = Math.round(posX);
            lp.y = Math.round(posY);
            try {
                wm.updateViewLayout(petView, lp);
            } catch (Exception ignored) {
            }

            float speed = (float) Math.hypot(velX, velY);
            if (bounced && speed < 90f) {
                flinging = false;
                snapToEdge();
                return;
            }
            handler.postDelayed(this, 16);
        }
    };

    private void snapToEdge() {
        if (!prefs.edgeSnap() || lp == null || petView == null) return;
        int mid = lp.x + lp.width / 2;
        final int targetX = mid < screenW / 2 ? dp(2) : screenW - lp.width - dp(2);
        if (Math.abs(targetX - lp.x) < dp(6)) return;
        final int startX = lp.x;
        final long t0 = SystemClock.uptimeMillis();
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (petView == null) return;
                float p = Math.min(1f, (SystemClock.uptimeMillis() - t0) / 180f);
                lp.x = Math.round(startX + (targetX - startX) * p);
                try {
                    wm.updateViewLayout(petView, lp);
                } catch (Exception ignored) {
                }
                if (p < 1f) handler.postDelayed(this, 16);
            }
        });
    }

    private boolean flinging;
    private int flingN;
    private long flingSum;
    private int flingMax;
    private float velX, velY, gravity;
    private long lastFlingTick;

    /** 帧率：把窗口的首选刷新率设成 60 或 120，连续渲染模式会自动跟上 */
    private void applyFps() {
        if (lp == null) return;
        int fps = prefs.fps();
        lp.preferredRefreshRate = fps >= 120 ? 120f : 60f;
        try {
            if (petView != null) wm.updateViewLayout(petView, lp);
        } catch (Exception ignored) {
        }
    }
    // 悬浮动作面板：不启动 Activity，像它自己的气泡一样贴在底部
    private void togglePanel() {
        if (panel == null) {
            panel = new PetPanel(this, new PetPanel.Listener() {
                @Override
                public void onPlay(String action) {
                    sendBroadcast(new Intent(ACTION_PLAY)
                            .setPackage(getPackageName())
                            .putExtra("name", action));
                }

                @Override
                public void onClose() {
                }
            });
        }
        if (panel.isShowing()) panel.hide();
        else panel.show();
    }
    // ---------------- 互动模式：点哪跑哪 ----------------

    private void enableTouchLayer() {
        // 已废弃：全屏触摸层会挡键盘和输入框，用户明确不要。
        // 现在只保留「直接点她本人」的交互，触摸层一律不创建。
        if (touchLayer != null) disableTouchLayer();
        return;
    }

    private void enableTouchLayerDisabled() {
        if (touchLayer != null || wm == null) return;
        touchLayer = new View(this);
        touchLayer.setBackgroundColor(0x01000000);
        touchLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        touchLp.gravity = Gravity.TOP | Gravity.START;
        touchLayer.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                onScreenTap(e.getRawX(), e.getRawY());
            }
            return true;
        });
        try {
            wm.addView(touchLayer, touchLp);
        } catch (Exception e) {
            touchLayer = null;
        }
        // 触摸层是全屏的，加完要把它压到宠物窗口下面，否则点不到她
        if (touchLayer != null && petView != null && lp != null) {
            try {
                wm.removeView(petView);
                wm.addView(petView, lp);
                petView.onResumeRenderer();
            } catch (Exception ignored) {
            }
        }
    }

    private void disableTouchLayer() {
        if (touchLayer == null) return;
        try {
            wm.removeView(touchLayer);
        } catch (Exception ignored) {
        }
        touchLayer = null;
    }

    private void setTouchable(boolean touchable) {
        if (touchLayer == null || touchLp == null) return;
        if (touchable) {
            touchLp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            touchLp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        try {
            wm.updateViewLayout(touchLayer, touchLp);
        } catch (Exception ignored) {
        }
    }

    private void onScreenTap(float x, float y) {
        if (petView == null) return;
        // 不踩在手指正上方：随机偏一点，像"凑过去"而不是"贴上去"
        int jitterX = dp(24) + random.nextInt(Math.max(1, dp(40)));
        int jitterY = random.nextInt(Math.max(1, dp(28))) - dp(14);
        if (random.nextBoolean()) jitterX = -jitterX;
        int tx = (int) (x - lp.width / 2f) + jitterX;
        int ty = (int) (y - lp.height / 2f) + jitterY;
        runTo(tx, ty);
    }

    /**
     * 匀速跑向目标点。中途再点一次不会重新起步——
     * 只改目标，她直接从"现在站的地方"拐过去。
     */
    private void runTo(int targetX, int targetY) {
        moveTargetX = clamp(targetX, dp(4), screenW - lp.width - dp(4));
        moveTargetY = clamp(targetY, dp(24), screenH - lp.height - dp(4));

        boolean faceRight = moveTargetX > posX;
        if (moving) {
            // 中途改道：只转向，动画接着跑
            if (petView != null) petView.setFacing(faceRight);
            return;
        }

        moving = true;
        posX = lp.x;
        posY = lp.y;
        lastMoveTick = SystemClock.uptimeMillis();
        float rate = Math.max(0.85f, Math.min(1.8f, prefs.runSpeed() / 380f));
        if (petView != null) petView.startRun(faceRight, rate);
        Choreographer.getInstance().postFrameCallback(moveFrame);
    }

    /** 用 Choreographer 跟着屏幕刷新率走：60Hz 就 60 帧，120/144/165Hz 也能吃满 */
    private final Choreographer.FrameCallback moveFrame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!moving || petView == null) return;
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(0.05f, (now - lastMoveTick) / 1000f);
            lastMoveTick = now;

            float dx = moveTargetX - posX;
            float dy = moveTargetY - posY;
            float dist = (float) Math.hypot(dx, dy);
            float step = Math.max(120f, prefs.runSpeed()) * dt;

            if (dist <= step || dist < 1f) {
                posX = moveTargetX;
                posY = moveTargetY;
                applyMove();
                stopMoving();
                return;
            }
            posX += dx / dist * step;
            posY += dy / dist * step;
            applyMove();
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private void applyMove() {
        if (lp == null || petView == null) return;
        lp.x = Math.round(posX);
        // 跑动时轻微起伏，像脚步落地
        int bob = moving
                ? Math.round((float) Math.sin(SystemClock.uptimeMillis() / 85.0) * dp(3))
                : 0;
        lp.y = Math.round(posY) + bob;
        try {
            wm.updateViewLayout(petView, lp);
        } catch (Exception ignored) {
        }
    }

    private void stopMoving() {
        if (!moving) return;
        moving = false;
        try { Choreographer.getInstance().removeFrameCallback(moveFrame); } catch (Exception ignored) {}
        if (petView != null) petView.stopRun();
        // 她跑完了，先把屏幕还给你 1.2 秒，避免每次点完都被她抢走
        setTouchable(false);
        handler.postDelayed(() -> {
            if (!moving) setTouchable(true);
        }, 1200);
    }

    private boolean moving;
    private float posX, posY;
    private int moveTargetX, moveTargetY;
    private long lastMoveTick;
    private final java.util.Random random = new java.util.Random();

    // ---------------- 气泡 ----------------

    /** 可爱提示音：不走 TTS，避免机械感 */
    private android.media.MediaPlayer chime;

    private void playChime(boolean mention) {
        if (prefs.muted()) return;
        try {
            if (chime != null) { chime.release(); chime = null; }
            chime = new android.media.MediaPlayer();
            android.content.res.AssetFileDescriptor afd = getAssets().openFd(
                    mention ? "sfx/chime_mention.mp3" : "sfx/chime_notify.mp3");
            chime.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            chime.setVolume(0.55f, 0.55f);
            chime.setOnCompletionListener(mp -> { mp.release(); if (chime == mp) chime = null; });
            chime.prepare();
            chime.start();
        } catch (Exception ignored) {
        }
    }
    private void showBubble(String text, long duration) {
        removeBubble();
        if (wm == null || lp == null) return;
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(0xFF0E2C42);
        tv.setLineSpacing(dp(2), 1f);
        GradientDrawable bg = new GradientDrawable();
        boolean low = prefs.mood() < 35;
        bg.setColor(low ? 0xFFE6EEF5 : 0xFFFFFFFF);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), low ? 0xFF9FB8CB : 0xFF9CC8E6);
        tv.setBackground(bg);
        tv.setPadding(dp(16), dp(10), dp(16), dp(10));
        tv.setMaxWidth(dp(210));

        int boxW = dp(210);
        tv.measure(android.view.View.MeasureSpec.makeMeasureSpec(
                        boxW, android.view.View.MeasureSpec.AT_MOST),
                android.view.View.MeasureSpec.makeMeasureSpec(
                        dp(400), android.view.View.MeasureSpec.AT_MOST));
        int bw = Math.max(dp(60), tv.getMeasuredWidth());
        WindowManager.LayoutParams blp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        blp.gravity = Gravity.TOP | Gravity.START;
        blp.x = clamp(lp.x + lp.width / 2 - bw / 2, dp(8), screenW - bw - dp(8));
        blp.y = Math.max(dp(36), lp.y - dp(46));
        try {
            wm.addView(tv, blp);
            bubble = tv;
        } catch (Exception ignored) {
            return;
        }
        handler.postDelayed(this::removeBubble, duration);
    }

    private void removeBubble() {
        if (bubble == null) return;
        try {
            wm.removeView(bubble);
        } catch (Exception ignored) {
        }
        bubble = null;
    }

    // ---------------- 游戏检测 ----------------

    private final Runnable gameWatch = new Runnable() {
        @Override
        public void run() {
            try {
                tickWatch();
            } catch (Exception ignored) {
            }
            handler.postDelayed(this, 4000);
        }
    };

    // 无聊 / 被冷落：按"多久没互动"触发，各带冷却
    private long lastBoredAt;
    private long lastNeglectAt;

    private void upkeepLoneliness() {
        if (petView == null || uiHidden || hiddenForGame) return;
        long idleMs = System.currentTimeMillis() - prefs.lastInteraction();
        long now = SystemClock.uptimeMillis();
        if (idleMs > 30 * 60000L && now - lastNeglectAt > 30 * 60000L) {
            lastNeglectAt = now;
            petView.playOnce("sleepy");
            showBubble("……你忙吧，我先睡会儿", 3000);
            return;
        }
        if (idleMs > 5 * 60000L && now - lastBoredAt > 5 * 60000L) {
            lastBoredAt = now;
            petView.playOnce(random.nextBoolean() ? "look_around" : "yawn");
            showBubble("有点无聊…你在干嘛呀", 2600);
        }
    }
    private void upkeepSedentary() {
        upkeepLoneliness();
        if (!screenOn || screenOnSince == 0 || sedentaryWarned) return;
        if (SystemClock.uptimeMillis() - screenOnSince > 50 * 60000L) {
            sedentaryWarned = true;
            if (petView != null && !uiHidden && !hiddenForGame) {
                showBubble("已经坐了快一小时了，起来动一动嘛～", 3200);
                if (petView != null) petView.playOnce("stretch");
            }
        }
    }

    // ---------------- 使用情况感知：同一个 App 待太久就说一句 ----------------
    private String lastPkg;
    private long lastPkgSince;
    private long lastUsageWarnAt;

    private String categoryOf(String pkg) {
        String p = pkg == null ? "" : pkg.toLowerCase();
        if (p.contains("tmgp") || p.contains("mihoyo") || p.contains("netease")
                || p.contains("supercell") || p.contains("game")) return "game";
        if (p.contains("tencent.mm") || p.contains("mobileqq") || p.contains("weibo")
                || p.contains("telegram") || p.contains("whatsapp")) return "social";
        if (p.contains("bilibili") || p.contains("douyin") || p.contains("youku")
                || p.contains("iqiyi") || p.contains("youtube")) return "video";
        if (p.contains("taobao") || p.contains("jingdong") || p.contains("pinduoduo")) return "shop";
        if (p.contains("zhihu") || p.contains("xhs") || p.contains("reader")) return "read";
        return "other";
    }

    private void trackUsage(String pkg) {
        long now = SystemClock.uptimeMillis();
        if (!pkg.equals(lastPkg)) {
            lastPkg = pkg;
            lastPkgSince = now;
            return;
        }
        long mins = (now - lastPkgSince) / 60000L;
        if (mins < 30 || now - lastUsageWarnAt < 30 * 60000L) return;
        lastUsageWarnAt = now;
        prefs.bumpUsageWarn();
        String cat = categoryOf(pkg);
        String line;
        switch (cat) {
            case "game": line = "游戏打半小时啦，眼睛要不要休息一下"; break;
            case "social": line = "聊了好久哦…我在这儿等你瞄我一眼"; break;
            case "video": line = "刷了半小时视频了，起来动动嘛"; break;
            case "shop": line = "逛这么久，购物车要满啦"; break;
            case "read": line = "看了半小时字了，歇会儿眼睛吧"; break;
            default: line = "这个 App 你用了挺久了，记得喝水";
        }
        if (petView != null && !uiHidden && !hiddenForGame) {
            showBubble(line, 3200);
            petView.playOnce("look_around");
        }
    }
    private void tickWatch() {
        upkeepSedentary();
        if (!prefs.gameAway() || !hasUsageAccess()) return;
        String pkg = foregroundPackage();
        if (pkg == null) return;

        // 自己的界面在前台：让她先躲开（广播丢了也能兜底）
        if (pkg.equals(getPackageName())) {
            if (!uiHidden) {
                uiHidden = true;
                hidePet();
            }
            return;
        }
        if (uiHidden) {
            uiHidden = false;
            showPet();
        }
        trackUsage(pkg);
        if (hiddenForGame) {
            // 还在离场状态，等游戏退出去再回来
            if (!isGame(pkg)) comeBack();
            return;
        }
        if (isGame(pkg) && petView != null) {
            leaveForGame();
        }
    }

    private void leaveForGame() {
        hiddenForGame = true;
        showBubble("你去玩吧，我一会儿再来～", 2600);
        if (petView != null) petView.playOnce("bye");
        handler.postDelayed(this::hidePet, 1100);
    }

    private void comeBack() {
        hiddenForGame = false;
        showPet();
        handler.postDelayed(() -> {
            if (petView != null) petView.playOnce("tap_happy");
            showBubble("你回来啦～", 2200);
        }, 700);
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

    private String foregroundPackage() {
        UsageStatsManager usm =
                (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return null;
        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(now - 15000, now);
        if (events == null) return null;
        UsageEvents.Event e = new UsageEvents.Event();
        String last = null;
        long lastTs = 0;
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                if (e.getTimeStamp() >= lastTs) {
                    lastTs = e.getTimeStamp();
                    last = e.getPackageName();
                }
            }
        }
        return last;
    }

    private boolean isGame(String pkg) {
        if (prefs.getAwayPackages().contains(pkg)) return true;
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
            return ai.category == ApplicationInfo.CATEGORY_GAME;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    // ---------------- 杂项 ----------------

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        try {
            if (screenReceiver != null) unregisterReceiver(screenReceiver);
        } catch (Exception ignored) {
        }
        removeBubble();
        disableTouchLayer();
        if (petView != null) {
            petView.onPauseRenderer();
            try {
                wm.removeView(petView);
            } catch (Exception ignored) {
            }
            petView = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
