package com.whale.pet;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** 成就：全部由本地数据算出来，不联网、不读别的应用。 */
public class Achv {

    public static class Item {
        public final String name;
        public final String desc;
        public final boolean unlocked;

        Item(String name, String desc, boolean unlocked) {
            this.name = name;
            this.desc = desc;
            this.unlocked = unlocked;
        }
    }

    public static List<Item> list(Context ctx) {
        Prefs p = new Prefs(ctx);
        Memory m = Memory.load(ctx);
        int days = m.daysTogether();
        int inter = p.interactions();
        int streak = p.streakDays();
        int chats = p.chatCount();
        int mood = p.mood();

        List<Item> out = new ArrayList<>();
        out.add(new Item("初次见面", "把她叫到桌面上", days >= 1));
        out.add(new Item("三天之约", "陪她 3 天", days >= 3));
        out.add(new Item("一周目", "陪她 7 天", days >= 7));
        out.add(new Item("签到新手", "连续签到 3 天", streak >= 3));
        out.add(new Item("不迟到的人", "连续签到 7 天", streak >= 7));
        out.add(new Item("手欠", "戳她 50 次", inter >= 50));
        out.add(new Item("重度饲养", "戳她 200 次", inter >= 200));
        out.add(new Item("话痨", "和她聊 10 句", chats >= 10));
        out.add(new Item("聊到深夜", "和她聊 50 句", chats >= 50));
        out.add(new Item("她今天很开心", "把心情养到 90", mood >= 90));
        return out;
    }

    public static int unlockedCount(Context ctx) {
        int n = 0;
        for (Item i : list(ctx)) if (i.unlocked) n++;
        return n;
    }
}
