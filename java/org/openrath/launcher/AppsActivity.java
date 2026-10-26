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
 * All apps, in a grid on black. With the "appDrawer" feature on: a search box, A to Z or most used
 * first, a row of pinned and recently opened apps, an A–Z column to jump with, and a long press for
 * pin, hide, App info or Uninstall (hidden apps show only when searched for). While the car moves
 * (Settings only when stopped) it shows only a short list of the pinned and most used apps, with no
 * search box, until the car stops or the owner says a passenger is using the screen.
 */
public class AppsActivity extends Activity {

    static final int RECENT = 6;
    /** How many apps the list shows while driving: the pinned ones, then the most opened. */
    static final int DRIVING = 8;

    private final List<ResolveInfo> all = new ArrayList<>();
    private final List<ResolveInfo> shown = new ArrayList<>();
    private PackageManager pm;
    private Prefs prefs;
    private float k;
    private boolean extras;
    private GridView grid;
    private EditText search;
    private LinearLayout strip, letters, top, drivingBar;
    private HorizontalScrollView hs;
    private ScrollView ls;
    private BaseAdapter adapter;
    /** While the car moves (Settings only when stopped): no typing and no full list, only the short list. */
    private boolean driving;
    /** The owner said a passenger is using the screen: the full list and search while this list is open. */
    private boolean passenger;
    private final android.os.Handler handler = new android.os.Handler();
    /** Every few seconds: the car started or stopped moving, so the list changes with it. */
    private final Runnable watch = new Runnable() {
        @Override
        public void run() {
            if (locked() != driving) mode();
            handler.postDelayed(this, 3000);
        }
    };

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
        if (Glass.on) root.setBackground(Glass.backdrop());   // the Glass look: the wallpaper, the apps on a glass panel
        else root.setBackgroundColor(0xFF000000);

        // while driving: a line saying why there is no search, and the passenger's way to it
        drivingBar = new LinearLayout(this);
        drivingBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView why = new TextView(this);
        why.setTextColor(Ui.SILVER_DIM);
        why.setTextSize(0, Math.max(Ui.MIN_TEXT, 18) * k);
        why.setText(Hindi.t("Your most used apps. Search and the full list open when the car is stopped."));
        drivingBar.addView(why, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView pass = PhoneCard.pill(this, Hindi.t("I'm a passenger"), 0xFF2A2D32);
        pass.setTextSize(0, Math.max(Ui.MIN_TEXT, 18) * k);
        pass.setPadding(Math.round(22 * k), 0, Math.round(22 * k), 0);
        pass.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                passenger = true;
                mode();
            }
        });
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-2, Math.round(HomeDock.TARGET * k));
        pp.leftMargin = Math.round(16 * k);
        drivingBar.addView(pass, pp);
        drivingBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams dbp = new LinearLayout.LayoutParams(-1, -2);
        dbp.setMargins(pad, Math.round(12 * k), pad, 0);
        root.addView(drivingBar, dbp);

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
            // the order: A to Z, or the apps opened most first
            final TextView order = new TextView(this);
            order.setTextColor(Ui.SILVER);
            order.setTextSize(0, 16 * k);
            order.setGravity(Gravity.CENTER);
            order.setPadding(Math.round(16 * k), 0, Math.round(16 * k), 0);
            order.setText(Hindi.t(mostUsed() ? "Most used" : "A to Z"));
            order.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.put("appOrder", mostUsed() ? "az" : "used");
                    order.setText(Hindi.t(mostUsed() ? "Most used" : "A to Z"));
                    filter();
                }
            });
            if (Glass.on) {
                search.setBackground(Glass.pane(28));
                search.setPadding(Math.round(18 * k), 0, Math.round(18 * k), 0);
                search.setMinHeight(Math.round(56 * k));
                order.setBackground(Glass.pane(28));
            }
            top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(search, new LinearLayout.LayoutParams(0, -2, 1f));
            top.addView(order, new LinearLayout.LayoutParams(-2, Math.round(HomeDock.TARGET * k)));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
            sp.setMargins(pad, Math.round(12 * k), pad, 0);
            root.addView(top, sp);

            hs = new HorizontalScrollView(this);
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
            ls = new ScrollView(this);
            letters = new LinearLayout(this);
            letters.setOrientation(LinearLayout.VERTICAL);
            letters.setPadding(0, pad, Math.round(8 * k), pad);
            ls.addView(letters);
            body.addView(ls, new LinearLayout.LayoutParams(Math.round(44 * k), -1));
        }
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, 0, 1);
        if (Glass.on) {
            body.setBackground(Glass.pane(24));
            int gm = Math.round(18 * k);
            bp.setMargins(gm, gm, gm, gm);
        }
        root.addView(body, bp);
        Edge.fit(root);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // apps may have been added or removed while we were away
        all.clear();
        all.addAll(AppPicker.apps(this));
        if (extras) {
            fillStrip();
            fillLetters();
        }
        mode();
        handler.postDelayed(watch, 3000);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(watch);
        super.onPause();
    }

    private boolean locked() {
        return !passenger && ChildLock.parkedOnly(prefs);
    }

    /** The full list with search, or while driving the short list with no keyboard. */
    private void mode() {
        driving = locked();
        drivingBar.setVisibility(driving ? View.VISIBLE : View.GONE);
        if (extras) {
            if (driving && search.hasFocus()) {
                android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (im != null) im.hideSoftInputFromWindow(search.getWindowToken(), 0);
                search.clearFocus();
            }
            top.setVisibility(driving ? View.GONE : View.VISIBLE);
            ls.setVisibility(driving ? View.GONE : View.VISIBLE);
            hs.setVisibility(driving || strip.getChildCount() == 0 ? View.GONE : View.VISIBLE);
        }
        filter();
    }

    private LinearLayout cell(int iconDp) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView icon = new ImageView(this);
        cell.addView(icon, new LinearLayout.LayoutParams(Math.round(iconDp * k), Math.round(iconDp * k)));
        TextView t = new TextView(this);
        t.setTextColor(Ui.SILVER);
        t.setTextSize(0, Math.max(Ui.MIN_TEXT, 14) * k);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (Glass.on) t.setShadowLayer(2 * k, 0, k, 0x59000000);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = Math.round(6 * k);
        cell.addView(t, p);
        return cell;
    }

    private boolean mostUsed() {
        return extras && "used".equals(prefs.str("appOrder", "az"));
    }

    /**
     * The apps to show: what matches the search; hidden apps only when searched for (so they can be
     * found and shown again); the most opened first when that order is chosen.
     */
    private void filter() {
        List<String> hidden = split(prefs.str("hiddenApps", ""));
        shown.clear();
        if (driving) {
            List<String> pkgs = new ArrayList<>();
            for (ResolveInfo r : all) if (!hidden.contains(r.activityInfo.packageName)) pkgs.add(r.activityInfo.packageName);
            for (String pkg : shortList(pkgs, counts(prefs.str("appUses", "")), split(prefs.str("pinnedApps", "")), DRIVING)) {
                ResolveInfo r = find(pkg);
                if (r != null) shown.add(r);
            }
            adapter.notifyDataSetChanged();
            return;
        }
        String q = search == null ? "" : search.getText().toString();
        for (ResolveInfo r : all) {
            String pkg = r.activityInfo.packageName;
            if (hidden.contains(pkg) && q.trim().isEmpty()) continue;
            if (matches(r.loadLabel(pm).toString(), pkg, q)) shown.add(r);
        }
        if (mostUsed()) {
            final java.util.Map<String, Integer> uses = counts(prefs.str("appUses", ""));
            java.util.Collections.sort(shown, new java.util.Comparator<ResolveInfo>() {
                @Override
                public int compare(ResolveInfo a, ResolveInfo b) {
                    Integer x = uses.get(a.activityInfo.packageName), y = uses.get(b.activityInfo.packageName);
                    return Integer.compare(y == null ? 0 : y, x == null ? 0 : x);   // stable: A to Z among equals
                }
            });
        }
        adapter.notifyDataSetChanged();
    }

    /**
     * The short list while driving: the pinned apps first, then the most opened, then A to Z (the
     * order pkgs come in), at most max. Pure, for the tests.
     */
    static List<String> shortList(List<String> pkgs, final java.util.Map<String, Integer> uses, List<String> pinned, int max) {
        List<String> out = new ArrayList<>();
        for (String p : pinned) if (pkgs.contains(p) && !out.contains(p) && out.size() < max) out.add(p);
        List<String> rest = new ArrayList<>();
        for (String p : pkgs) if (!out.contains(p)) rest.add(p);
        java.util.Collections.sort(rest, new java.util.Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                Integer x = uses.get(a), y = uses.get(b);
                return Integer.compare(y == null ? 0 : y, x == null ? 0 : x);   // stable: A to Z among equals
            }
        });
        for (String p : rest) if (out.size() < max) out.add(p);
        return out;
    }

    /** "pkg=n,pkg=n" to a map. */
    static java.util.Map<String, Integer> counts(String s) {
        java.util.Map<String, Integer> m = new java.util.HashMap<>();
        for (String part : s.split(",")) {
            int eq = part.lastIndexOf('=');
            if (eq <= 0) continue;
            try {
                m.put(part.substring(0, eq), Integer.parseInt(part.substring(eq + 1)));
            } catch (NumberFormatException ignored) {
                // a damaged entry: left out
            }
        }
        return m;
    }

    /** One more open of `pkg`, keeping the 60 most opened apps. */
    static String bump(String s, String pkg) {
        java.util.Map<String, Integer> m = counts(s);
        Integer n = m.get(pkg);
        m.put(pkg, n == null ? 1 : n + 1);
        List<java.util.Map.Entry<String, Integer>> l = new ArrayList<>(m.entrySet());
        java.util.Collections.sort(l, new java.util.Comparator<java.util.Map.Entry<String, Integer>>() {
            @Override
            public int compare(java.util.Map.Entry<String, Integer> a, java.util.Map.Entry<String, Integer> b) {
                return Integer.compare(b.getValue(), a.getValue());
            }
        });
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(60, l.size()); i++) b.append(b.length() == 0 ? "" : ",").append(l.get(i).getKey()).append('=').append(l.get(i).getValue());
        return b.toString();
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
        prefs.put("appUses", bump(prefs.str("appUses", ""), pkg));   // also for the short list while driving
        Intent in = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(pkg, r.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(in);
        finish();
    }

    private void menu(ResolveInfo r) {
        if (driving) return;   // pin, hide and uninstall wait for the car to stop
        final String pkg = r.activityInfo.packageName;
        final List<String> pinned = split(prefs.str("pinnedApps", ""));
        final boolean isPinned = pinned.contains(pkg);
        final List<String> items = new ArrayList<>();
        final List<String> hidden = split(prefs.str("hiddenApps", ""));
        if (extras) items.add(isPinned ? "Unpin" : "Pin to the top");
        if (extras) items.add(hidden.contains(pkg) ? "Show in the list" : "Hide from the list");
        items.add("App info");
        items.add("Uninstall");
        String[] shownItems = new String[items.size()];
        for (int i = 0; i < shownItems.length; i++) shownItems[i] = Hindi.t(items.get(i));
        new Glass.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
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
                        } else if ("Hide from the list".equals(it)) {
                            prefs.put("hiddenApps", join(push(hidden, pkg, 500)));
                            filter();
                            Toast.makeText(AppsActivity.this, Hindi.t("Hidden. Search for it to find it again."), Toast.LENGTH_SHORT).show();
                        } else if ("Show in the list".equals(it)) {
                            prefs.put("hiddenApps", join(remove(hidden, pkg)));
                            filter();
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
