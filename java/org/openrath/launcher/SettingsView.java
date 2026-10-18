package org.openrath.launcher;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings screen, opened from the gear at the bottom of the dock: groups down the left,
 * that group's settings on the right. Each row shows its current value and acts when tapped;
 * the screen redraws after every change, including ones made in a dialog.
 */
final class SettingsView extends FrameLayout {

    static final class Row {
        final String group, title, value;
        final Runnable action;
        /** Shown as an on/off switch when set; value is then the line under the title. */
        Boolean on;
        /** For a feature's switch: the feature's id, which decides the section it sits in. */
        String feature;

        Row(String group, String title, String value, Runnable action) {
            this.group = group;
            this.title = title;
            this.value = value;
            this.action = action;
        }

        Row toggle(boolean on, String feature) {
            this.on = on;
            this.feature = feature;
            return this;
        }
    }

    /** A section on the left: the old groups it gathers, each shown under its own small heading. */
    static final class Section {
        final String name, about;
        final String[] groups, headings;

        Section(String name, String about, String[] groups, String[] headings) {
            this.name = name;
            this.about = about;
            this.groups = groups;
            this.headings = headings;
        }
    }

    static final Section[] SECTIONS = {
            new Section("OpenRath level", "Simple, Full or Custom", new String[]{"Level"}, new String[]{"How much OpenRath shows"}),
            new Section("Home screen", "Boxes, pages, night mode, voice", new String[]{"Dashboard", "Voice"},
                    new String[]{"Boxes and layout", "Voice commands"}),
            new Section("Look", "Layouts, themes, car picture, buttons bar", new String[]{"Layout", "Themes", "Car", "Buttons bar"},
                    new String[]{"Screen layout", "Themes", "Car picture", "Buttons bar"}),
            new Section("Radio and music", "FM, online radio, sound", new String[]{"Radio and music"}, new String[]{"Radio and music"}),
            new Section("Phone and messages", "Favourites, message ticker", new String[]{"Phone", "Notifications"},
                    new String[]{"Phone", "Messages"}),
            new Section("Driving", "Map, alerts, trips, fuel, dashcam", new String[]{"Map and navigation", "Driving", "Monsoon", "Dashcam"},
                    new String[]{"Map and navigation", "Trips, fuel and alerts", "Monsoon", "Dashcam"}),
            new Section("Family", "Valet, child-lock, drivers", new String[]{"Valet", "Family"},
                    new String[]{"Kids or valet mode", "Child-lock and drivers"}),
            new Section("Car", "Service, engine, lights, buttons", new String[]{"Car care", "Engine and tyres", "Car signals", "Steering-wheel buttons"},
                    new String[]{"Service reminders", "Engine and tyres", "Lights, indicators and reverse", "Steering-wheel buttons"}),
            new Section("Cab driver", "Earnings, fare meter, SOS, papers", new String[]{"Cab"}, new String[]{"Cab driver"}),
            new Section("Backup and about", "Backup, privacy, version", new String[]{"Backup", "Help"}, new String[]{"Backup", "About"}),
            new Section("Troubleshooting", "Tests and reports if something fails", new String[]{"Troubleshooting"}, new String[]{"Reports"}),
    };

    /** Help rows that are for finding faults rather than everyday use. */
    private static final java.util.Set<String> TROUBLE = new java.util.HashSet<>(java.util.Arrays.asList(
            "Diagnostics", "How the stock launcher does it", "Unit report", "Clear the crash note", "Crash reports",
            "Headlight setup, deep search", "Reset GPS"));

    /** The section a feature's switch goes in. */
    private static String sectionOf(Features.F f) {
        if ("ticker".equals(f.id) || "readAloud".equals(f.id)) return "Phone and messages";
        if ("Dashboard".equals(f.group)) return "Home screen";
        if ("Sound".equals(f.group)) return "Radio and music";
        if ("Data".equals(f.group)) return "Backup and about";
        if ("Family".equals(f.group)) return "Family";
        if ("Cab".equals(f.group)) return "Cab driver";
        return f.group;   // Driving, Car
    }

    /** The old group a row is shown under, after moving fault-finding rows to Troubleshooting. */
    private static String groupOf(Row r) {
        if (TROUBLE.contains(r.title)) return "Troubleshooting";
        return r.group;
    }

    interface Source {
        List<Row> rows();

        /** A whole page shown above the group's rows (the theme gallery), or null. */
        View page(String group);

        void closeSettings();
    }

    private final Source source;
    private final LinearLayout groups = new LinearLayout(getContext()), list = new LinearLayout(getContext());
    private final ScrollView scroll = new ScrollView(getContext());
    private final TextView heading, subheading;
    private final android.widget.EditText search;
    private static String group;   // kept while the screen is rebuilt for a new theme

    SettingsView(Context c, Source s) {
        super(c);
        source = s;
        setBackgroundColor(Ui.BACKGROUND);
        setClickable(true);   // nothing reaches the dashboard underneath

        LinearLayout side = new LinearLayout(c);
        side.setOrientation(LinearLayout.VERTICAL);
        int m = Ui.px(18);
        side.setPadding(m, m, m, m);
        TextView back = Ui.text(c, 17, Ui.SILVER, "sans-serif-medium", Gravity.LEFT | Gravity.CENTER_VERTICAL);
        back.setText(Hindi.t("‹  Back to the dashboard"));
        back.setPadding(Ui.px(14), 0, Ui.px(14), 0);
        back.setBackground(pill(false));
        back.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                source.closeSettings();
            }
        });
        side.addView(back, new LinearLayout.LayoutParams(-1, Ui.px(54)));

        // search every setting by name
        search = new android.widget.EditText(c);
        search.setSingleLine(true);
        search.setHint(Hindi.t("Search settings"));
        search.setHintTextColor(Ui.SILVER_DIM);
        search.setTextColor(Ui.SILVER);
        search.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 16 * Ui.k);
        search.setPadding(Ui.px(14), 0, Ui.px(14), 0);
        search.setBackground(pill(false));
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence t, int a, int b, int n) {
            }

            @Override
            public void onTextChanged(CharSequence t, int a, int b, int n) {
            }

            @Override
            public void afterTextChanged(android.text.Editable e) {
                scroll.scrollTo(0, 0);
                refresh();
            }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, Ui.px(50));
        sp.topMargin = Ui.px(12);
        sp.bottomMargin = Ui.px(12);
        side.addView(search, sp);

        groups.setOrientation(LinearLayout.VERTICAL);
        ScrollView gs = new ScrollView(c);
        gs.addView(groups);
        side.addView(gs, new LinearLayout.LayoutParams(-1, 0, 1f));
        int sideW = Ui.px(300);
        addView(side, new LayoutParams(sideW, -1, Gravity.LEFT));

        View rule = new View(c);
        rule.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.EDGE & 0x00FFFFFF, Ui.mix(Ui.SILVER_DIM, Ui.BACKGROUND, 0.7f), Ui.EDGE & 0x00FFFFFF}));
        addView(rule, new LayoutParams(Math.max(1, Ui.px(1.2f)), -1, Gravity.LEFT));
        ((LayoutParams) rule.getLayoutParams()).leftMargin = sideW;

        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(Ui.px(28), m, Ui.px(28), 0);
        heading = Ui.text(c, 26, Ui.SILVER, "sans-serif-light", Gravity.LEFT);
        heading.setPadding(0, Ui.px(8), 0, Ui.px(4));
        right.addView(heading);
        subheading = Ui.text(c, 14, Ui.SILVER_DIM, "sans-serif", Gravity.LEFT);
        subheading.setPadding(0, 0, 0, Ui.px(10));
        right.addView(subheading);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, m);
        scroll.addView(list);
        right.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        LayoutParams rp = new LayoutParams(-1, -1);
        rp.leftMargin = sideW;
        addView(right, rp);
    }

    private static GradientDrawable pill(boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(12 * Ui.k);
        g.setColor(on ? Ui.PANEL_TOP : Ui.PANEL);
        g.setStroke(Math.max(1, Ui.px(1.2f)), on ? Ui.ACCENT : Ui.mix(Ui.EDGE, Ui.PANEL, 0.6f));
        return g;
    }

    /** The rows each section shows, in order: feature switches first, then each old group under its heading. */
    private static java.util.Map<String, List<Object>> arrange(List<Row> rows) {
        java.util.Map<String, List<Object>> out = new java.util.LinkedHashMap<>();
        for (Section sec : SECTIONS) {
            List<Object> items = new ArrayList<>();
            List<Row> switches = new ArrayList<>();
            java.util.Set<String> switchNames = new java.util.HashSet<>();
            for (Row r : rows) {
                if (r.feature == null) continue;
                Features.F f = Features.get(r.feature);
                if (f != null && sectionOf(f).equals(sec.name)) {
                    switches.add(r);
                    switchNames.add(r.title.toLowerCase(java.util.Locale.US));
                }
            }
            if (!switches.isEmpty()) {
                items.add("Turn on or off");
                items.addAll(switches);
            }
            for (int i = 0; i < sec.groups.length; i++) {
                List<Row> mine = new ArrayList<>();
                for (Row r : rows) {
                    if (r.feature != null || r.action == null || !groupOf(r).equals(sec.groups[i])) continue;
                    if (switchNames.contains(r.title.toLowerCase(java.util.Locale.US))) continue;   // the switch above does this
                    mine.add(r);
                }
                boolean page = "Themes".equals(sec.groups[i]) || "Layout".equals(sec.groups[i]);
                if (mine.isEmpty() && !page) continue;
                items.add(sec.headings[i]);
                if (page) items.add(new Page(sec.groups[i]));   // the group's own page (the theme gallery) goes here
                items.addAll(mine);
            }
            if (!items.isEmpty()) out.put(sec.name, items);
        }
        return out;
    }

    private static Section section(String name) {
        for (Section s : SECTIONS) if (s.name.equals(name)) return s;
        return SECTIONS[0];
    }

    /** Redraw from the current settings, staying on the same section and scroll position. */
    void refresh() {
        List<Row> rows = source.rows();
        java.util.Map<String, List<Object>> all = arrange(rows);
        if (group == null || !all.containsKey(group)) group = all.isEmpty() ? "" : all.keySet().iterator().next();
        String q = search.getText().toString().trim().toLowerCase(java.util.Locale.US);

        groups.removeAllViews();
        for (final String g : all.keySet()) {
            boolean on = q.isEmpty() && g.equals(group);
            LinearLayout item = new LinearLayout(getContext());
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(Ui.px(14), 0, Ui.px(10), 0);
            item.setBackground(pill(on));
            TextView t = Ui.text(getContext(), 17, on ? Ui.SILVER : Ui.mix(Ui.SILVER, Ui.SILVER_DIM, 0.5f),
                    on ? "sans-serif-medium" : "sans-serif", Gravity.LEFT);
            t.setText(Hindi.t(g));
            item.addView(t);
            TextView a = Ui.text(getContext(), 12, Ui.SILVER_DIM, "sans-serif", Gravity.LEFT);
            a.setText(Hindi.t(section(g).about));
            a.setPadding(0, Ui.px(4), 0, 0);
            item.addView(a);
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    group = g;
                    search.setText("");
                    scroll.scrollTo(0, 0);
                    refresh();
                }
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Ui.px(64));
            p.bottomMargin = Ui.px(8);
            groups.addView(item, p);
        }

        final int y = scroll.getScrollY();
        list.removeAllViews();
        if (!q.isEmpty()) {
            heading.setText(Hindi.t("Search"));
            int found = 0;
            for (java.util.Map.Entry<String, List<Object>> e : all.entrySet()) {
                boolean head = false;
                for (Object o : e.getValue()) {
                    if (!(o instanceof Row)) continue;
                    Row r = (Row) o;
                    String hay = (r.title + " " + (r.value == null ? "" : r.value) + " " + e.getKey() + " " + Hindi.t(r.title) + " "
                            + Hindi.t(r.value) + " " + Hindi.t(e.getKey())).toLowerCase(java.util.Locale.US);
                    if (!hay.contains(q)) continue;
                    if (!head) {
                        addHeading(e.getKey());
                        head = true;
                    }
                    addRow(r);
                    found++;
                }
            }
            subheading.setText(found == 0 ? "Nothing matches \"" + q + "\"" : found + (found == 1 ? " setting" : " settings"));
        } else {
            heading.setText(Hindi.t(group));
            subheading.setText(Hindi.t(section(group).about));
            List<Object> items = all.get(group);
            if (items != null) for (Object o : items) {
                if (o instanceof Row) addRow((Row) o);
                else if (o instanceof Page) {
                    View page = source.page(((Page) o).group);
                    if (page != null) list.addView(page, new LinearLayout.LayoutParams(-1, -2));
                } else addHeading((String) o);
            }
        }
        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.scrollTo(0, y);
            }
        });
    }

    /** Where a group's whole page goes among the rows. */
    private static final class Page {
        final String group;

        Page(String group) {
            this.group = group;
        }
    }

    private void addHeading(String text) {
        TextView h = Ui.label(getContext(), Hindi.t(text).toUpperCase(java.util.Locale.US));
        h.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 12 * Ui.k);
        h.setPadding(Ui.px(4), list.getChildCount() == 0 ? Ui.px(6) : Ui.px(18), 0, Ui.px(10));
        list.addView(h);
    }

    private void addRow(final Row r) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.px(18), Ui.px(12), Ui.px(18), Ui.px(12));
        row.setBackground(pill(false));
        row.setMinimumHeight(Ui.px(68));
        LinearLayout text = new LinearLayout(getContext());
        text.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(getContext(), 17, Ui.SILVER, "sans-serif", Gravity.LEFT);
        title.setSingleLine(false);
        title.setText(Hindi.t(r.title));
        text.addView(title);
        if (r.value != null && !r.value.isEmpty()) {
            TextView value = Ui.text(getContext(), 14, r.on == null ? Ui.mix(Ui.ACCENT, Ui.SILVER, 0.5f) : Ui.SILVER_DIM,
                    "sans-serif", Gravity.LEFT);
            value.setSingleLine(false);
            value.setText(Hindi.t(r.value));
            value.setPadding(0, Ui.px(5), 0, 0);
            text.addView(value);
        }
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-2, -2);
        ep.leftMargin = Ui.px(16);
        if (r.on != null) {
            row.addView(new Switch(getContext(), r.on), new LinearLayout.LayoutParams(Ui.px(58), Ui.px(32)));
        } else {
            TextView chevron = Ui.text(getContext(), 22, Ui.SILVER_DIM, "sans-serif-light", Gravity.CENTER);
            chevron.setText("›");
            row.addView(chevron, ep);
        }
        row.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Reports.count(r.title);
                r.action.run();
                refresh();
            }
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = Ui.px(10);
        list.addView(row, p);
    }

    /** An on/off switch, drawn: a rounded track with a knob, lit in the accent colour when on. */
    static final class Switch extends View {
        final boolean on;

        Switch(Context c, boolean on) {
            super(c);
            this.on = on;
        }

        @Override
        protected void onDraw(android.graphics.Canvas cv) {
            float w = getWidth(), h = getHeight(), r = h / 2f;
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            p.setColor(on ? Ui.ACCENT : Ui.mix(Ui.SILVER_DIM, Ui.BACKGROUND, 0.35f));
            cv.drawRoundRect(new android.graphics.RectF(0, 0, w, h), r, r, p);
            p.setColor(on ? 0xFFFFFFFF : Ui.mix(Ui.SILVER, Ui.BACKGROUND, 0.8f));
            float k = r - Ui.px(3);
            cv.drawCircle(on ? w - r : r, r, k, p);
        }
    }

    // a dialog opened from a row just closed: show what it changed
    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) refresh();
    }
}
