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

    /** Valet mode, the child-lock, passenger games and driver profiles: related jobs, side by side, each saying how it differs. */
    static final String FAMILY = "Valet and children";

    static final Section[] SECTIONS = {
            new Section("General", "Level, language, units, country", new String[]{"Level", "General"},
                    new String[]{"How much OpenRath shows", "General"}),
            new Section("Home screen", "Boxes, pages, weather, news", new String[]{"Dashboard"}, new String[]{"Boxes and layout"}),
            new Section("Look", "Layouts, themes, car picture, buttons bar", new String[]{"Layout", "Themes", "Car", "Buttons bar"},
                    new String[]{"Screen layout", "Themes", "Car picture", "Buttons bar"}),
            new Section("Voice", "Mic, language, what you can say", new String[]{"Voice"}, new String[]{"Voice commands"}),
            new Section("Radio and music", "FM, online radio, sound", new String[]{"Radio and music"}, new String[]{"Radio and music"}),
            new Section("Phone and messages", "Favourites, message ticker", new String[]{"Phone", "Notifications"},
                    new String[]{"Phone", "Messages"}),
            new Section("Navigation", "Map, navigation apps, next turn", new String[]{"Map and navigation"}, new String[]{"Apps and voice"}),
            new Section("Driving alerts", "Speed, fog, breaks, road learning", new String[]{"Driving", "Monsoon"}, new String[]{"Speed", "Monsoon"}),
            new Section("Fuel and parking", "Petrol, tolls, parking, sharing costs", new String[0], new String[0]),
            new Section("Dashcam", "Recording with a USB camera", new String[]{"Dashcam"}, new String[]{"Dashcam"}),
            new Section("Your driving", "Trips, badges, routes, learnt roads", new String[]{"Your driving"}, new String[]{"Records"}),
            new Section(FAMILY, "Valet mode, app lock, passenger games, drivers", new String[]{"Valet", "Family"},
                    new String[]{"Valet mode", "Children and drivers"}),
            new Section("Car", "Service, engine, lights, buttons", new String[]{"Car care", "Engine and tyres", "Car signals", "Steering-wheel buttons"},
                    new String[]{"Service reminders", "Engine and tyres", "Lights, indicators and reverse", "Steering-wheel buttons"}),
            new Section("Cab driver", "Earnings, fare meter, SOS, papers", new String[]{"Cab"}, new String[]{"Cab driver"}),
            new Section("Backup and about", "Backup, privacy, version", new String[]{"Backup", "Help"}, new String[]{"Backup", "About"}),
            new Section("Troubleshooting", "Tests and reports if something fails", new String[]{"Troubleshooting"}, new String[]{"Reports"}),
    };

    /** Help rows that are for finding faults rather than everyday use. */
    private static final java.util.Set<String> TROUBLE = new java.util.HashSet<>(java.util.Arrays.asList(
            "Diagnostics", "How the stock launcher does it", "Unit report", "Clear the crash note",
            "Headlight setup, deep search", "Reset GPS"));

    /** The section a switch goes in when it isn't its group's: by feature id. */
    private static final String[][] SECTION_OF = {
            {"voice", "Voice"}, {"ticker", "Phone and messages"}, {"readAloud", "Phone and messages"},
            {"callScreen", "Phone and messages"}, {"hotspot", "Phone and messages"},
            {"autoTheme", "Look"}, {"bootArt", "Look"}, {"intro", "Look"}, {"appDrawer", "Look"}, {"dockStatus", "Look"},
            {"tapFeedback", "Look"}, {"road", "Look"}, {"arrows", "Look"}, {"artColour", "Look"},
            {"navTurns", "Navigation"}, {"overStrip", "Navigation"}, {"places", "Navigation"}, {"tripShare", "Navigation"},
            {"offlineMap", "Navigation"},
            {"fuelPrice", "Fuel and parking"}, {"tolls", "Fuel and parking"}, {"parking", "Fuel and parking"},
            {"parkedMusic", "Fuel and parking"}, {"split", "Fuel and parking"},
            {"dashcam", "Dashcam"},
            {"score", "Your driving"}, {"achievements", "Your driving"}, {"accel", "Your driving"}, {"gpx", "Your driving"}};

    /**
     * Rows that set up a feature, by title: each shows just under the feature's switch, and only while the
     * feature is on. "valet" and "obdReady" (OBD on with an adapter chosen) aren't switches but work the same.
     */
    static final String[][] UNDER = {
            {"Number of pages", "pages"}, {"Clock screensaver after", "saver"}, {"News box shows", "news"},
            {"Preview night-drive mode", "nightDrive"}, {"Holidays ahead", "calendar"},
            {"Mark a waterlogging spot here", "rain"}, {"Marked spots", "rain"}, {"Air quality now", "rain"},
            {"Start-up summary", "startSummary"}, {"This week's report", "startSummary"},
            {"Night and phone themes", "autoTheme"}, {"Make the boot logo", "bootArt"},
            {"Listen mainly for", "voice"}, {"Try a voice command", "voice"}, {"Mic button", "voice"}, {"What you can say", "voice"},
            {"City sound", "soundProfiles"}, {"Highway sound", "soundProfiles"}, {"Night sound", "soundProfiles"},
            {"How much louder", "speedVolume"}, {"Quiet hours start at", "quiet"}, {"Quiet hours end at", "quiet"},
            {"Quiet-hours volume", "quiet"}, {"Equalizer settings", "eq"}, {"Phone projection app", "projection"},
            {"Ticker shows", "ticker"}, {"Apps to listen to", "ticker"}, {"Try the ticker", "ticker"}, {"Read aloud says", "readAloud"},
            {"Guest Wi-Fi", "hotspot"}, {"Change the hotspot name and password", "hotspot"},
            {"Map kept for no internet", "offlineMap"}, {"Voice without internet", "offlineMap"},
            {"Your own speed limit", "speedAlerts"}, {"Break reminder after", "restStop"}, {"Find a rest stop now", "restStop"},
            {"Fog mode now", "fog"}, {"Speed in fog", "fog"},
            {"Petrol price", "fuelPrice"}, {"Petrol left", "fuelPrice"}, {"Tank size", "fuelPrice"},
            {"Warn when the petrol is below", "fuelPrice"}, {"Toll estimate per plaza", "tolls"},
            {"Send the parking spot to your phone", "parking"}, {"Paid parking reminder", "parking"},
            {"Share the trip", "tripShare"}, {"Split the cost", "split"}, {"Your UPI id", "split"},
            {"Pause parked music after", "parkedMusic"},
            {"Camera", "dashcam"}, {"Space for recordings", "dashcam"}, {"Save the clip now", "dashcam"},
            {"Play a game now", "kids"}, {"Apps locked while moving", "childLock"}, {"Who's driving", "profiles"},
            {"Volume limit", "valet"}, {"Speed limit in valet mode", "valet"}, {"Change the PIN", "valet"}, {"Choose the PIN", "valet"},
            {"Car documents", "wallet"}, {"Service reminders", "care"}, {"Your service centre", "care"},
            {"Camera picture in reverse", "reverse"}, {"OBD-II adapter", "obd"}, {"Read fault codes", "obdReady"}, {"Live engine readings", "obdReady"}, {"Your own engine readings", "obd"},
            {"Clear fault codes", "obdReady"}, {"Warn when the coolant is above", "obdReady"},
            {"Tyre sensors", "tpms"}, {"Tyre pressure units", "tpms"}, {"Warn when a tyre is below", "tpms"},
            {"LED light strip", "ledStrip"}, {"Learn a button", "keys"}, {"Buttons work in other apps too", "keys"},
            {"Check for updates now", "updates"}, {"Usage report", "reports"}, {"Crash reports", "reports"}};

    /** Figures and records rather than settings, by title: gathered in Your driving, each while its feature is on. */
    static final String[][] FIGURES = {
            {"Trips, fuel and tolls", ""}, {"Badges", "achievements"}, {"0–100 km/h", "accel"}, {"Mileage coach", "fuelPrice"},
            {"Export routes as GPX", "gpx"}, {"Delete recorded routes", "gpx"}, {"Last parking photo", "parking"},
            {"Speed breakers learnt", "bumps"}, {"Stops learnt", "bumps"}};

    /** Switches that mean something only while another is on: they show once it is. */
    static final String[][] NEEDS = {
            {"cabEarnings", "cab"}, {"cabMeter", "cab"}, {"cabApps", "cab"}, {"cabPapers", "cab"}, {"cabCng", "cab"},
            {"cabSpots", "cab"}, {"cabTarget", "cab"}, {"cabSos", "cab"}, {"cabQuiet", "cab"}, {"cabNotes", "cab"},
            {"cabPhrases", "cab"}, {"projectionAuto", "projection"}, {"longKeys", "keys"}, {"cam360Turn", "view360"},
            {"faultWords", "obd"}, {"askCar", "obd"}};

    private static String lookup(String[][] table, String key) {
        if (key == null) return null;
        for (String[] t : table) if (t[0].equals(key)) return t[1];
        return null;
    }

    /** Whether a row's feature (from UNDER, FIGURES or NEEDS) is on: "" and null always are. */
    static boolean depOn(Prefs p, String dep) {
        if (dep == null || dep.isEmpty()) return true;
        if ("valet".equals(dep)) return Valet.on(p);
        if ("obdReady".equals(dep)) return Features.on(p, "obd") && !p.str("obdMac", "").isEmpty();
        return Features.on(p, dep);
    }

    /** The feature a row waits for, or null: a switch's master switch, a setting's feature, or a figure's. */
    static String dependsOn(Row r) {
        if (r.feature != null) return lookup(NEEDS, r.feature);
        String u = lookup(UNDER, r.title);
        return u != null ? u : lookup(FIGURES, r.title);
    }

    /** The section a feature's switch goes in. */
    static String sectionOf(Features.F f) {
        String s = lookup(SECTION_OF, f.id);
        if (s != null) return s;
        if ("Dashboard".equals(f.group)) return "Home screen";
        if ("Sound".equals(f.group)) return "Radio and music";
        if ("Data".equals(f.group)) return "Backup and about";
        if ("Family".equals(f.group)) return FAMILY;
        if ("Cab".equals(f.group)) return "Cab driver";
        if ("Phone".equals(f.group)) return "Phone and messages";
        if ("Driving".equals(f.group)) return "Driving alerts";
        return f.group;   // Car, Radio and music
    }

    /** The old group a row is shown under, after moving fault-finding rows to Troubleshooting. */
    private static String groupOf(Row r) {
        if (TROUBLE.contains(r.title)) return "Troubleshooting";
        if (lookup(FIGURES, r.title) != null) return "Your driving";
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
        opened.clear();   // switches that are off start folded away each time
        Glass.screen(this);
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
        if (Glass.on) {
            // a glass pill: brighter when picked, edged in the accent
            GradientDrawable g = Glass.pane(18);
            if (on) {
                g.setColors(new int[]{Glass.mix(Ui.ACCENT, Ui.PANEL_TOP, 0.22f), Glass.mix(Ui.ACCENT, Ui.PANEL, 0.16f), Glass.mix(Ui.ACCENT, Ui.PANEL, 0.16f)});
                g.setStroke(Math.max(1, Ui.px(1.5f)), Ui.mix(Ui.ACCENT, 0xFFFFFFFF, 0.6f));
            }
            return g;
        }
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(12 * Ui.k);
        g.setColor(on ? Ui.PANEL_TOP : Ui.PANEL);
        g.setStroke(Math.max(1, Ui.px(1.2f)), on ? Ui.ACCENT : Ui.mix(Ui.EDGE, Ui.PANEL, 0.6f));
        return g;
    }

    /**
     * The rows each section shows, in order: feature switches first, each followed by the rows that set it up,
     * then each old group under its heading. Rows waiting for a feature that's off aren't shown.
     */
    static java.util.Map<String, List<Object>> arrange(List<Row> rows, Prefs p) {
        List<Row> live = new ArrayList<>();
        for (Row r : rows) if (depOn(p, dependsOn(r))) live.add(r);
        return place(live, opened);
    }

    /** The left-hand entry, and the row in each section, that gather the switches of features that are off. */
    static final String MORE = "More features";
    private static final Section MORE_SECTION = new Section(MORE, "Everything that's off, ready to turn on", new String[0], new String[0]);
    /**
     * Sections whose "More features" row was tapped, so their switched-off features show in place, and "#" and the id
     * of each switch used in its own section, which stays where it is when turned off.
     */
    static final java.util.Set<String> opened = new java.util.HashSet<>();

    private static boolean isOff(Row r) {
        return r.feature != null && Boolean.FALSE.equals(r.on);
    }

    /** A section's "More features" row: one line instead of every switch that's off; a tap opens them out. */
    private static Row moreRow(final String section, int n) {
        return new Row(section, MORE, Hindi.f(n == 1 ? "%1$s more feature you can turn on" : "%1$s more features you can turn on", n), new Runnable() {
            @Override
            public void run() {
                opened.add(section);
            }
        });
    }

    /**
     * The sections from the rows to show (live): in each, the switches of features that are on, each with the rows
     * that set it up, then one "More features" row for those that are off (all of them once `open` names the
     * section), then each old group under its heading. Last, More features gathers every switch that's off.
     * Pure apart from the translated words, for the tests.
     */
    static java.util.Map<String, List<Object>> place(List<Row> live, java.util.Set<String> open) {
        java.util.Set<String> switchIds = new java.util.HashSet<>();
        for (Row r : live) if (r.feature != null) switchIds.add(r.feature);
        java.util.Map<String, List<Object>> out = new java.util.LinkedHashMap<>();
        List<Object> more = new ArrayList<>();
        for (Section sec : SECTIONS) {
            List<Object> items = new ArrayList<>();
            List<Row> switches = new ArrayList<>(), off = new ArrayList<>();
            java.util.Set<String> switchNames = new java.util.HashSet<>();
            for (Row r : live) {
                if (r.feature == null) continue;
                Features.F f = Features.get(r.feature);
                if (f != null && sectionOf(f).equals(sec.name)) {
                    (isOff(r) && !open.contains("#" + r.feature) ? off : switches).add(r);
                    switchNames.add(r.title.toLowerCase(java.util.Locale.US));
                }
            }
            if (!switches.isEmpty() || !off.isEmpty()) {
                items.add("Turn on or off");
                for (Row s : switches) {
                    items.add(s);
                    for (Row r : live) {
                        if (r.feature == null && r.action != null && s.feature.equals(lookup(UNDER, r.title))) items.add(new Child(r));
                    }
                }
                // Valet and children keeps its three side by side, so their one-line differences can be compared
                if (open.contains(sec.name) || FAMILY.equals(sec.name)) items.addAll(off);
                else if (!off.isEmpty()) items.add(moreRow(sec.name, off.size()));
                if (!off.isEmpty()) {
                    more.add(sec.name);
                    more.addAll(off);
                }
            }
            for (int i = 0; i < sec.groups.length; i++) {
                List<Row> mine = new ArrayList<>();
                for (Row r : live) {
                    if (r.feature != null || r.action == null || !groupOf(r).equals(sec.groups[i])) continue;
                    if (switchNames.contains(r.title.toLowerCase(java.util.Locale.US))) continue;   // the switch above does this
                    if (switchIds.contains(lookup(UNDER, r.title))) continue;   // shown under its switch
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
        if (!more.isEmpty()) out.put(MORE, more);
        return out;
    }

    /** A row that sets up the switch above it: drawn indented. */
    static final class Child {
        final Row row;

        Child(Row row) {
            this.row = row;
        }
    }

    private static Row rowOf(Object o) {
        return o instanceof Row ? (Row) o : o instanceof Child ? ((Child) o).row : null;
    }

    static final String RECENT = "Recently used";
    private static final Section RECENT_SECTION = new Section(RECENT, "The settings you changed last", new String[0], new String[0]);

    /** Remembers a setting the owner just used, newest first, at most four. */
    static String remember(String list, String title) {
        List<String> l = new ArrayList<>();
        l.add(title);
        if (list != null) for (String t : list.split("\n")) if (!t.isEmpty() && !t.equals(title) && l.size() < 4) l.add(t);
        StringBuilder b = new StringBuilder();
        for (String t : l) b.append(b.length() == 0 ? "" : "\n").append(t);
        return b.toString();
    }

    /** The rows the owner used last, found again by title among today's rows (a row that's gone is skipped). */
    private List<Object> recent(java.util.Map<String, List<Object>> all) {
        List<Object> out = new ArrayList<>();
        String list = new Prefs(getContext()).str("recentRows", "");
        for (String t : list.split("\n")) {
            if (t.isEmpty()) continue;
            find:
            for (List<Object> items : all.values()) {
                for (Object o : items) {
                    Row r = rowOf(o);
                    if (r != null && r.title.equals(t)) {
                        out.add(r);
                        break find;
                    }
                }
            }
        }
        return out;
    }

    /**
     * A row's value as {state, hint}: a short state ("on", "₹100", "not set up") goes on the right; anything
     * after "; " or a long sentence goes in the grey line under the title.
     */
    static String[] split(String v) {
        if (v == null || v.isEmpty()) return new String[]{"", ""};
        int semi = v.indexOf("; ");
        String state = semi > 0 ? v.substring(0, semi) : v, hint = semi > 0 ? v.substring(semi + 2) : "";
        if (state.length() > 24) return new String[]{"", v};
        return new String[]{state, hint};
    }

    private static Section section(String name) {
        if (RECENT.equals(name)) return RECENT_SECTION;
        if (MORE.equals(name)) return MORE_SECTION;
        for (Section s : SECTIONS) if (s.name.equals(name)) return s;
        return SECTIONS[0];
    }

    /** Redraw from the current settings, staying on the same section and scroll position. */
    void refresh() {
        List<Row> rows = source.rows();
        Prefs prefs = new Prefs(getContext());
        java.util.Map<String, List<Object>> all = arrange(rows, prefs);
        List<Object> used = recent(all);
        if (!used.isEmpty()) {
            java.util.Map<String, List<Object>> withRecent = new java.util.LinkedHashMap<>();
            withRecent.put(RECENT, used);
            withRecent.putAll(all);
            all = withRecent;
        }
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
            java.util.Set<Row> listed = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Row, Boolean>());
            for (java.util.Map.Entry<String, List<Object>> e : all.entrySet()) {
                if (RECENT.equals(e.getKey())) continue;
                boolean head = false;
                for (Object o : e.getValue()) {
                    Row r = rowOf(o);
                    if (r == null || (r.feature == null && MORE.equals(r.title)) || !listed.add(r)) continue;   // each once
                    String hay = (r.title + " " + (r.value == null ? "" : r.value) + " " + e.getKey() + " " + Hindi.t(r.title) + " "
                            + Hindi.t(r.value) + " " + Hindi.t(e.getKey())).toLowerCase(java.util.Locale.US);
                    if (!hay.contains(q)) continue;
                    if (!head) {
                        addHeading(e.getKey());
                        head = true;
                    }
                    addRow(r, false);
                    found++;
                }
            }
            subheading.setText(found == 0 ? "Nothing matches \"" + q + "\"" : found + (found == 1 ? " setting" : " settings"));
        } else {
            heading.setText(Hindi.t(group));
            subheading.setText(Hindi.t(section(group).about));
            List<Object> items = all.get(group);
            Row home = homeBanner(rows);
            if (home != null && !"Backup and about".equals(group)) addRow(home, false);
            if (items != null) for (Object o : items) {
                if (o instanceof Row) addRow((Row) o, false);
                else if (o instanceof Child) addRow(((Child) o).row, true);
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

    /** The "Home app" row while OpenRath isn't the home app: shown at the top of every section until it is. */
    private static Row homeBanner(List<Row> rows) {
        for (Row r : rows) if ("Home app".equals(r.title) && r.value != null && r.value.startsWith("not OpenRath")) return r;
        return null;
    }

    private void addRow(final Row r, boolean child) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.px(18), Ui.px(12), Ui.px(18), Ui.px(12));
        row.setBackground(pill("Home app".equals(r.title) && r.value != null && r.value.startsWith("not OpenRath")));
        row.setMinimumHeight(Ui.px(68));
        LinearLayout text = new LinearLayout(getContext());
        text.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(getContext(), 17, Ui.SILVER, "sans-serif", Gravity.LEFT);
        title.setSingleLine(false);
        title.setText(Hindi.t(r.title));
        text.addView(title);
        // a switch's line is its explanation; any other row's value splits into its state and a hint
        String[] sv = r.on != null ? new String[]{"", r.value == null ? "" : Hindi.t(r.value)} : split(Hindi.t(r.value));
        if (!sv[1].isEmpty()) {
            TextView hint = Ui.text(getContext(), 14, Ui.SILVER_DIM, "sans-serif", Gravity.LEFT);
            hint.setSingleLine(false);
            hint.setText(sv[1]);
            hint.setPadding(0, Ui.px(5), 0, 0);
            text.addView(hint);
        }
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-2, -2);
        ep.leftMargin = Ui.px(16);
        if (!sv[0].isEmpty()) {
            TextView state = Ui.text(getContext(), 15, Ui.mix(Ui.ACCENT, Ui.SILVER, 0.5f), "sans-serif-medium", Gravity.RIGHT);
            state.setSingleLine(true);
            state.setText(sv[0]);
            row.addView(state, ep);
        }
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
                Prefs p = new Prefs(getContext());
                boolean link = r.feature == null && MORE.equals(r.title);   // opens out the switches that are off
                if (!link) p.put("recentRows", remember(p.str("recentRows", ""), r.title));
                r.action.run();
                if (MORE.equals(group) && r.feature != null && Features.on(p, r.feature)) {
                    // it leaves this list: say where it went
                    Features.F f = Features.get(r.feature);
                    if (f != null) android.widget.Toast.makeText(getContext(), Hindi.f("%1$s is on. Its settings are in %2$s", Hindi.t(f.name),
                            Hindi.t(sectionOf(f))), android.widget.Toast.LENGTH_SHORT).show();
                } else if (r.feature != null) opened.add("#" + r.feature);   // turned off here: it stays here until Settings closes
                refresh();
            }
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = Ui.px(10);
        if (child) p.leftMargin = Ui.px(28);
        list.addView(row, p);
    }

    /** The knob on a lit switch: white on a dark accent, dark on a light one, so it always shows. Pure, for the tests. */
    static int knob(int accent) {
        double lum = (0.299 * ((accent >> 16) & 0xFF) + 0.587 * ((accent >> 8) & 0xFF) + 0.114 * (accent & 0xFF)) / 255;
        return lum > 0.6 ? 0xFF15181C : 0xFFFFFFFF;
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
            p.setColor(on ? knob(Ui.ACCENT) : Ui.mix(Ui.SILVER, Ui.BACKGROUND, 0.8f));
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
