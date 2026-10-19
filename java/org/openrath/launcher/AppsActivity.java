package org.openrath.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * All apps, in a grid on black. With the "appDrawer" feature on: a search box, a row of pinned and
 * recently opened apps, an A–Z column to jump with, and a long press for pin, App info or Uninstall.
 */
public class AppsActivity extends Activity {

    static final int RECENT = 6;

    private final List<ResolveInfo> all = new ArrayList<>();
    private final List<ResolveInfo> shown = new ArrayList<>();
    private PackageManager pm;
    private Prefs prefs;
    private float k;
    private boolean extras;
    private GridView grid;
    private EditText search;
    private LinearLayout strip, letters;
    private BaseAdapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pm = getPackageManager();
        prefs = new Prefs(this);
        extras = Features.on(prefs, "appDrawer");
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        k = Ui.scale(dm.widthPixels, dm.heightPixels,
                Math.max(50, Math.min(200, prefs.num("uiScale", 100))) / 100f);
        int pad = Math.round(28 * k);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF000000);

        if (extras) {
            search = new EditText(this);
            search.setSingleLine(true);
            search.setHint(Hindi.t("Search apps"));
            search.setTextColor(Ui.SILVER);
            search.setHintTextColor(Ui.SILVER_DIM);
            search.setTextSize(0, 18 * k);
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
                @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
                @Override public void afterTextChanged(Editable s) { filter(); }
            });
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
            sp.setMargins(pad, Math.round(12 * k), pad, 0);
            root.addView(search, sp);

            HorizontalScrollView hs = new HorizontalScrollView(this);
            strip = new LinearLayout(this);
            strip.setOrientation(LinearLayout.HORIZONTAL);
            strip.setPadding(pad, Math.round(8 * k), pad, 0);
            hs.addView(strip);
            root.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        }

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(Math.round(120 * k));
        grid.setVerticalSpacing(Math.round(18 * k));
        grid.setPadding(pad, pad, pad, pad);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }

            @Override
            public View getView(int i, View v, ViewGroup parent) {
                LinearLayout cell = (LinearLayout) v;
                if (cell == null) cell = cell(64);
                ResolveInfo r = shown.get(i);
                ((ImageView) cell.getChildAt(0)).setImageDrawable(r.loadIcon(pm));
                ((TextView) cell.getChildAt(1)).setText(r.loadLabel(pm));
                return cell;
            }
        };
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> a, View v, int i, long id) {
                open(shown.get(i));
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> a, View v, int i, long id) {
                menu(shown.get(i));
                return true;
            }
        });
        body.addView(grid, new LinearLayout.LayoutParams(0, -1, 1));
        if (extras) {
            ScrollView ls = new ScrollView(this);
            letters = new LinearLayout(this);
            letters.setOrientation(LinearLayout.VERTICAL);
            letters.setPadding(0, pad, Math.round(8 * k), pad);
            ls.addView(letters);
            body.addView(ls, new LinearLayout.LayoutParams(Math.round(44 * k), -1));
        }
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        Edge.fit(root);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // apps may have been added or removed while we were away
        all.clear();
        all.addAll(AppPicker.apps(this));
        filter();
        if (extras) {
            fillStrip();
            fillLetters();
        }
    }

    private LinearLayout cell(int iconDp) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView icon = new ImageView(this);
        cell.addView(icon, new LinearLayout.LayoutParams(Math.round(iconDp * k), Math.round(iconDp * k)));
        TextView t = new TextView(this);
        t.setTextColor(Ui.SILVER);
        t.setTextSize(0, 14 * k);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = Math.round(6 * k);
        cell.addView(t, p);
        return cell;
    }

    private void filter() {
        String q = search == null ? "" : search.getText().toString();
        shown.clear();
        for (ResolveInfo r : all) {
            if (matches(r.loadLabel(pm).toString(), r.activityInfo.packageName, q)) shown.add(r);
        }
        adapter.notifyDataSetChanged();
    }

    private ResolveInfo find(String pkg) {
        for (ResolveInfo r : all) if (r.activityInfo.packageName.equals(pkg)) return r;
        return null;
    }

    /** Pinned apps first, then the ones opened lately. */
    private void fillStrip() {
        strip.removeAllViews();
        List<String> pkgs = new ArrayList<>(split(prefs.str("pinnedApps", "")));
        for (String p : split(prefs.str("recentApps", ""))) if (!pkgs.contains(p)) pkgs.add(p);
        for (String p : pkgs) {
            final ResolveInfo r = find(p);
            if (r == null) continue;
            LinearLayout c = cell(48);
            ((ImageView) c.getChildAt(0)).setImageDrawable(r.loadIcon(pm));
            ((TextView) c.getChildAt(1)).setText(r.loadLabel(pm));
            c.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { open(r); }
            });
            c.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { menu(r); return true; }
            });
            strip.addView(c, new LinearLayout.LayoutParams(Math.round(96 * k), -2));
        }
        ((View) strip.getParent()).setVisibility(strip.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    /** One letter for each first letter the apps have; a tap jumps there. */
    private void fillLetters() {
        letters.removeAllViews();
        String last = "";
        for (int i = 0; i < all.size(); i++) {
            final String l = letter(all.get(i).loadLabel(pm).toString());
            if (l.equals(last)) continue;
            last = l;
            TextView t = new TextView(this);
            t.setText(l);
            t.setTextColor(Ui.SILVER_DIM);
            t.setTextSize(0, 16 * k);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Math.round(4 * k), 0, Math.round(4 * k));
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (search != null && search.length() > 0) search.setText("");
                    for (int j = 0; j < shown.size(); j++) {
                        if (letter(shown.get(j).loadLabel(pm).toString()).equals(l)) {
                            grid.setSelection(j);
                            return;
                        }
                    }
                }
            });
            letters.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void open(ResolveInfo r) {
        String pkg = r.activityInfo.packageName;
        if (ChildLock.blocked(prefs, pkg)) {
            Toast.makeText(this, Hindi.t(ChildLock.SORRY), Toast.LENGTH_SHORT).show();
            return;
        }
        if (extras) prefs.put("recentApps", join(push(split(prefs.str("recentApps", "")), pkg, RECENT)));
        Intent in = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(pkg, r.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(in);
        finish();
    }

    private void menu(ResolveInfo r) {
        final String pkg = r.activityInfo.packageName;
        final List<String> pinned = split(prefs.str("pinnedApps", ""));
        final boolean isPinned = pinned.contains(pkg);
        final List<String> items = new ArrayList<>();
        if (extras) items.add(isPinned ? "Unpin" : "Pin to the top");
        items.add("App info");
        items.add("Uninstall");
        String[] shownItems = new String[items.size()];
        for (int i = 0; i < shownItems.length; i++) shownItems[i] = Hindi.t(items.get(i));
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(r.loadLabel(pm))
                .setItems(shownItems, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        String it = items.get(which);
                        if ("Unpin".equals(it)) {
                            prefs.put("pinnedApps", join(remove(pinned, pkg)));
                            fillStrip();
                        } else if ("Pin to the top".equals(it)) {
                            prefs.put("pinnedApps", join(push(remove(pinned, pkg), pkg, 12)));
                            fillStrip();
                        } else if ("App info".equals(it)) {
                            start(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + pkg)));
                        } else {
                            start(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
                        }
                    }
                }).show();
    }

    private void start(Intent in) {
        try {
            startActivity(in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, Hindi.t("This unit doesn't allow that"), Toast.LENGTH_SHORT).show();
        }
    }

    // ---- small helpers, kept free of Android so the tests can check them

    /** Whether an app matches what was typed: every word appears in its name or package. */
    static boolean matches(String label, String pkg, String q) {
        if (q == null) return true;
        String hay = (label + " " + pkg).toLowerCase(java.util.Locale.ROOT);
        for (String w : q.trim().toLowerCase(java.util.Locale.ROOT).split("\\s+")) {
            if (!w.isEmpty() && !hay.contains(w)) return false;
        }
        return true;
    }

    /** The A–Z letter an app files under; "#" for digits and anything else. */
    static String letter(String label) {
        String s = label == null ? "" : label.trim();
        if (s.isEmpty()) return "#";
        char c = Character.toUpperCase(s.charAt(0));
        return c >= 'A' && c <= 'Z' ? String.valueOf(c) : "#";
    }

    /** `item` moved to the front, at most `max` kept. */
    static List<String> push(List<String> list, String item, int max) {
        List<String> out = new ArrayList<>();
        out.add(item);
        for (String s : list) if (!s.equals(item) && out.size() < max) out.add(s);
        return out;
    }

    static List<String> remove(List<String> list, String item) {
        List<String> out = new ArrayList<>(list);
        out.remove(item);
        return out;
    }

    static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String p : s.split(",")) if (!p.trim().isEmpty()) out.add(p.trim());
        return out;
    }

    static String join(List<String> list) {
        StringBuilder b = new StringBuilder();
        for (String s : list) {
            if (b.length() > 0) b.append(',');
            b.append(s);
        }
        return b.toString();
    }
}
