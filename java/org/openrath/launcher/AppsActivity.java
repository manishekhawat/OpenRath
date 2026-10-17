package org.openrath.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** All apps, in a grid on black. */
public class AppsActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final List<ResolveInfo> apps = AppPicker.apps(this);
        final PackageManager pm = getPackageManager();
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        final float k = Ui.scale(dm.widthPixels, dm.heightPixels,
                Math.max(50, Math.min(200, new Prefs(this).num("uiScale", 100))) / 100f);
        GridView g = new GridView(this);
        g.setBackgroundColor(0xFF000000);
        g.setNumColumns(GridView.AUTO_FIT);
        g.setColumnWidth(Math.round(120 * k));
        g.setVerticalSpacing(Math.round(18 * k));
        int pad = Math.round(28 * k);
        g.setPadding(pad, pad, pad, pad);
        g.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return apps.size(); }
            @Override public Object getItem(int i) { return apps.get(i); }
            @Override public long getItemId(int i) { return i; }

            @Override
            public View getView(int i, View v, ViewGroup parent) {
                LinearLayout cell = (LinearLayout) v;
                if (cell == null) {
                    cell = new LinearLayout(AppsActivity.this);
                    cell.setOrientation(LinearLayout.VERTICAL);
                    cell.setGravity(Gravity.CENTER_HORIZONTAL);
                    ImageView icon = new ImageView(AppsActivity.this);
                    cell.addView(icon, new LinearLayout.LayoutParams(Math.round(64 * k), Math.round(64 * k)));
                    TextView t = new TextView(AppsActivity.this);
                    t.setTextColor(Ui.SILVER);
                    t.setTextSize(0, 14 * k);
                    t.setSingleLine(true);
                    t.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
                    p.topMargin = Math.round(6 * k);
                    cell.addView(t, p);
                }
                ResolveInfo r = apps.get(i);
                ((ImageView) cell.getChildAt(0)).setImageDrawable(r.loadIcon(pm));
                ((TextView) cell.getChildAt(1)).setText(r.loadLabel(pm));
                return cell;
            }
        });
        g.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> a, View v, int i, long id) {
                ResolveInfo r = apps.get(i);
                if (ChildLock.blocked(new Prefs(AppsActivity.this), r.activityInfo.packageName)) {
                    android.widget.Toast.makeText(AppsActivity.this, Hindi.t(ChildLock.SORRY), android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent in = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(r.activityInfo.packageName, r.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(in);
                finish();
            }
        });
        setContentView(g);
    }
}
