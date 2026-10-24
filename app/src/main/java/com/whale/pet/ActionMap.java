package com.whale.pet;

import java.util.HashMap;

/** 英文动作 id → 素材中文文件名（不含扩展名）。 */
public class ActionMap {

    private static final HashMap<String, String> MAP = new HashMap<>();

    static {
        map("idle", "待机呼吸休闲");
        map("tap_happy", "点击回应-元气挥手");
        map("tap_shy", "点击回应-害羞惊讶");
        map("tap_angry", "点击回应-傲娇生气");
        map("tap_laugh", "点击回应-挠痒咯咯笑");
        map("tap_jump", "点击回应-开心跃动");
        map("eat_rice", "吃白饭");
        map("stretch", "超大伸懒腰");
        map("yawn", "哈欠连天");
        map("purr", "撸猫");
        map("dance", "轻快摇摆舞");
        map("run", "原地左转奔跑");
        map("bye", "女仆屈膝礼仪");
        map("tail_tap", "用鲸鱼尾巴拍打地面");
        map("think", "深度思考碎碎念");
        map("sleepy", "原地小憩沉眠");
        map("look_around", "东张西望");
        map("fan", "摇扇纳凉");
        map("mirror", "照镜子");
        map("float_step", "原地漂浮踏步");
        map("butterfly", "蝴蝶蜜蜂环绕头顶开花");
        map("doze_wake", "打瞌睡被惊醒");
        map("startled", "被吓一跳");
        map("sneak_eat", "偷吃零食被抓住");
        map("gift", "拆礼物");
        map("note", "轻快记录");
        map("watergun", "玩水枪");
        map("crab", "螃蟹走路");
    }

    private static void map(String id, String file) {
        MAP.put(id, file);
    }

    /** 返回 assets/actions 下的文件名（含 .mp4）。没映射就原样返回，兼容旧文件。 */
    public static String file(String action) {
        String v = MAP.get(action);
        return (v == null ? action : v) + ".mp4";
    }

    public static boolean has(String action) {
        return MAP.containsKey(action);
    }

    public static String[] allIds() {
        return MAP.keySet().toArray(new String[0]);
    }
}
