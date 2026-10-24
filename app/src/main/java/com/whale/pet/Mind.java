package com.whale.pet;

import java.util.Calendar;

/**
 * 她的小脑袋：根据时间、电量、你多久没理她、前台是什么应用，决定"说不说话、说什么、做什么动作"。
 * 全是本地规则，不发网络请求——快、稳、可解释。
 */
public class Mind {

    public static class Say {
        public final String text;
        public final String voice;   // 语音 id，没有就 null
        public final String action;  // 动作 id，没有就 null

        Say(String text, String voice, String action) {
            this.text = text;
            this.voice = voice;
            this.action = action;
        }
    }

    /** 前台是这些应用时她闭嘴：刷视频/聊天的时候别打扰 */
    private static final String[] QUIET_PKGS = {
            "com.ss.android.ugc.aweme", "tv.danmaku.bili", "com.tencent.mm",
            "com.tencent.mobileqq", "com.xingin.xhs", "com.zhihu.android"
    };

    public static boolean quietApp(String pkg) {
        if (pkg == null) return false;
        for (String q : QUIET_PKGS) if (pkg.startsWith(q)) return true;
        return false;
    }

    /**
     * @param hour          现在几点（0-23）
     * @param charging      是否在充电
     * @param battery       电量 0-100
     * @param sinceInteract 距离上次互动多少分钟
     * @param morningDone   今天早上问过早安没
     * @param nightDone     今天晚上催过睡没
     * @param mealDone      今天这顿饭点提醒过没
     * @param mood          心情 0-100
     * @return null 表示"现在不想说话"
     */
    public static Say think(int hour, boolean charging, int battery, int sinceInteract,
                            boolean morningDone, boolean nightDone, boolean mealDone,
                            int mood, java.util.Random r) {

        // 1) 夜里：催睡，优先级最高
        if ((hour >= 23 || hour < 1) && !nightDone) {
            return new Say("这么晚了还不睡…明天会变笨的哦", "chat5", "yawn");
        }
        // 2) 早上第一次见
        if (hour >= 6 && hour < 10 && !morningDone) {
            return new Say("早呀～今天也一起加油", "chat1", "tap_happy");
        }
        // 3) 饭点
        if (!mealDone && ((hour >= 11 && hour < 13) || (hour >= 17 && hour < 19))) {
            return new Say("到饭点了，去吃饭嘛（我也想吃白饭）", "chat2", "eat_rice");
        }
        // 4) 低电 & 没充电
        if (battery <= 20 && !charging) {
            return new Say("呜…快没电了，先充一下好不好", "chat4", "startled");
        }
        // 5) 很久没理她
        if (sinceInteract >= 180) {
            return new Say("你忙完啦？我一直都在的", "chat7", "look_around");
        }
        // 6) 心情低落：少说话
        if (mood < 35) {
            if (r.nextInt(3) != 0) return null;
            return new Say("……", null, "sleepy");
        }
        // 7) 平时：随口一句
        if (r.nextInt(3) != 0) return null;
        String[] small = {"记得喝水哦", "别一直盯着屏幕啦", "有点困了……", "今天也一起加油吧"};
        String[] voices = {"chat4", "chat6", "chat5", "chat1"};
        int i = r.nextInt(small.length);
        return new Say(small[i], voices[i], null);
    }

    public static int hourNow() {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
    }

    public static String dayKey() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) + "-" + (c.get(Calendar.MONTH) + 1) + "-"
                + c.get(Calendar.DAY_OF_MONTH);
    }
}
