package com.whale.pet;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** 选哪些应用里她要自动离场（游戏、全屏视频、开会软件都行）。 */
public class AwayAppsActivity extends Activity {

    private final List<String> labels = new ArrayList<>();
    private final List<String> packages = new ArrayList<>();
    private final List<Boolean> checked = new ArrayList<>();
    private Prefs prefs;
    private ArrayAdapter<String> adapter;
    private TextView countLabel;

    private void refreshCount() {
        int n = 0;
        for (boolean b : checked) if (b) n++;
        countLabel.setText(n == 0 ? "还没选（她会在所有应用上都待着）" : "已选 " + n + " 个应用");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_app);
        int pad = Ui.dp(this, 22);
        root.setPadding(pad, Ui.dp(this, 44), pad, Ui.dp(this, 24));

        root.addView(Ui.header(this, "离场名单", "这些应用一打开，她就说一声然后躲起来"));

        countLabel = Ui.text(this, "", 11, Ui.ACCENT);
        LinearLayout.LayoutParams clp = Ui.wrap();
        clp.topMargin = Ui.dp(this, 8);
        root.addView(countLabel, clp);

        ListView list = new ListView(this);
        // 不用 CHOICE_MODE_MULTIPLE：ListView 自己也会改选中态，和我们的 checked 列表打架，
        // 表现就是「勾了保存后回来看着像没勾，要点两次才能取消」
        list.setChoiceMode(ListView.CHOICE_MODE_NONE);
        list.setBackgroundColor(0x00000000);
        list.setDivider(null);
        list.setDividerHeight(Ui.dp(this, 8));
        list.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        list.setClipToPadding(false);

        adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_multiple_choice, labels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                CheckedTextView v = (CheckedTextView) super.getView(position, convertView, parent);
                v.setTextColor(Ui.INK_2);
                v.setTextSize(14);
                v.setBackgroundResource(R.drawable.card_bg);
                int p = Ui.dp(getContext(), 16);
                v.setPadding(p, Ui.dp(getContext(), 13), p, Ui.dp(getContext(), 13));
                v.setGravity(Gravity.CENTER_VERTICAL);
                v.setChecked(checked.get(position));
                return v;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            checked.set(position, !checked.get(position));
            Set<String> set = prefs.getAwayPackages();
            if (checked.get(position)) {
                set.add(packages.get(position));
            } else {
                set.remove(packages.get(position));
            }
            prefs.setAwayPackages(set);
            adapter.notifyDataSetChanged();
            refreshCount();
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button done = new Button(this);
        done.setText("好了");
        done.setAllCaps(false);
        done.setTextSize(15);
        done.setTextColor(0xFF06202F);
        done.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        done.setBackgroundResource(R.drawable.btn_primary);
        done.setStateListAnimator(null);
        done.setOnClickListener(v -> finish());
        root.addView(done, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));

        setContentView(root);
        loadApps();
    }

    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Set<String> saved = prefs.getAwayPackages();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            List<Object[]> rows = new ArrayList<>();
            for (ApplicationInfo ai : apps) {
                if (ai.packageName.equals(getPackageName())) continue;
                if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
                String label = String.valueOf(pm.getApplicationLabel(ai));
                rows.add(new Object[]{label, ai.packageName, saved.contains(ai.packageName)});
            }
            rows.sort(Comparator.comparing(o -> ((String) o[0])));
            runOnUiThread(() -> {
                labels.clear();
                packages.clear();
                checked.clear();
                for (Object[] r : rows) {
                    labels.add((String) r[0]);
                    packages.add((String) r[1]);
                    checked.add((Boolean) r[2]);
                }
                adapter.notifyDataSetChanged();
                refreshCount();
            });
        }).start();
    }
}
