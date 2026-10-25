package org.openrath.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Locale;

/** The boxes on the dashboard. Each sizes its contents from its own height, so any box fits any slot. */
final class Cards {

    interface Host {
        void editSlot(int slot);
        void openApp(String key, String fallbackName);
        /** Turn-by-turn to a place picked on the map, in the navigation app. */
        void navigate(double lat, double lon, String name);
        /** Without internet: hand the trip to a map app that works offline. */
        void navigateOffline(double lat, double lon, String name);
        /** Start the unit's radio: open it so it tunes in, then come back to the dashboard. */
        void startRadio();
        /** Start the radio app's background services, which on many units switch the tuner on. */
        void startRadioService();
        Prefs prefs();
        /** Redraw every box now (after a setting changed). */
        void refresh();
        void setupLights();
        /** Speak a navigation prompt aloud. */
        void speak(String text);
        /** Listen for a place name; the answer goes to map.voiceResult(). */
        void voiceSearch(MapCard map);
        /** The fuel log: fill-ups, add one, delete one. */
        void fuel();
        /** Trip computer options (reset today, save parking spot). */
        void tripMenu();
        /** Engine (OBD-II) options: choose the adapter, read and clear fault codes. */
        void obdMenu();
        /** Tyre sensor setup. */
        void tyresMenu();
        /** Hosts other apps' widgets. */
        android.appwidget.AppWidgetHost widgetHost();
        /** Choose the widget for a widget box ("widget1" / "widget2"). */
        void pickWidget(String content);
        /** Call a number (a service centre from the map). */
        void call(String number);
        /** The parking spot's QR code, full screen. */
        void parkQr();
    }

    static Card make(String content, Context c, Host host, int slot) {
        Card card;
        if (Prefs.CAR.equals(content)) card = new CarCard(c);
        else if (Prefs.CLOCK.equals(content)) card = new ClockCard(c);
        else if (Prefs.WEATHER.equals(content)) card = new WeatherCard(c);
        else if (Prefs.MAP.equals(content)) card = map(c);
        else if (Prefs.TRIP.equals(content)) card = new TripCard(c);
        else if (Prefs.FUEL.equals(content)) card = new FuelCard(c);
        else if (Prefs.PHONE.equals(content)) card = new PhoneCard(c);
        else if (Prefs.OBD.equals(content)) card = new ObdCard(c);
        else if (Prefs.TYRES.equals(content)) card = new TyresCard(c);
        else if (Prefs.PERF.equals(content)) card = new PerfCard(c);
        else if (Prefs.NEWS.equals(content)) card = new NewsCard(c);
        else if (Prefs.CAB.equals(content)) card = new CabCard(c);
        else if (Prefs.COMPASS.equals(content)) card = new CompassCard(c);
        else if (Prefs.SOUNDS.equals(content)) card = new SoundCard(c);
        else if (Prefs.CALENDAR.equals(content)) card = new CalendarCard(c);
        else if (Prefs.VIDEO.equals(content)) card = new VideoCard(c);
        else if (content.startsWith("widget")) card = new WidgetCard(c, content, host);
        else card = new MediaCard(c, Prefs.RADIO.equals(content));
        card.host = host;
        card.slot = slot;
        card.setOnLongClickListener(card.edit);
        return card;
    }

    /** The map box, or a note when the unit has no working WebView (some stereos ship without one). */
    static Card map(Context c) {
        if (MapCard.resting()) {
            final Card note = new NoMapCard(c, "The map stopped working twice, so it's resting. It comes back in a few minutes.");
            final Context ctx = c;
            note.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (ctx instanceof HomeActivity && note.isAttachedToWindow()) ((HomeActivity) ctx).build();
                }
            }, 5 * 60000 + 1000);
            return note;
        }
        // the dashboard keeps its map page between rebuilds (a page swipe, a new theme)
        HomeActivity home = c instanceof HomeActivity ? (HomeActivity) c : null;
        if (home != null && home.keptMap != null) {
            if (home.keptMap.alive()) {
                home.keptMap.reuse();
                return home.keptMap;
            }
            home.keptMap.destroy();
            home.keptMap = null;
        }
        try {
            MapCard m = new MapCard(c);
            if (home != null) home.keptMap = m;
            return m;
        } catch (Throwable e) {
            return new NoMapCard(c, "The map needs Android System WebView. Install or turn it on in Android's settings.");
        }
    }

    static final class NoMapCard extends Card {
        final TextView note = new TextView(getContext());

        NoMapCard(Context c, String why) {
            super(c);
            note.setText(Hindi.t(why));
            note.setTextColor(Ui.SILVER_DIM);
            note.setGravity(Gravity.CENTER);
            addView(note, new LayoutParams(-1, -1));
        }

        @Override
        void fit(int w, int h) {
            float sz = Math.max(Ui.size(0), Math.min(w / 22f, h / 6f));
            note.setTextSize(0, sz);
            note.setPadding(w / 12, 0, w / 12, 0);
            // a low box: fewer lines, ending in "…", rather than words cut off at the edge
            note.setMaxLines(Math.max(1, (int) (h * 0.85f / (sz * 1.2f))));
            note.setEllipsize(android.text.TextUtils.TruncateAt.END);
        }

        @Override
        public void changed(Hub h) {
        }
    }

    abstract static class Card extends FrameLayout implements Hub.Listener {
        Host host;
        int slot;

        final OnLongClickListener edit = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                host.editSlot(slot);
                return true;
            }
        };

        Card(Context c) {
            super(c);
            setBackground(Ui.panel());
        }

        /** Lay out the children for a w x h box. */
        abstract void fit(int w, int h);

        private int fitW, fitH;

        // Size the children before they are measured. Doing it in onSizeChanged was too late:
        // layout had already run, so the new sizes were ignored until something else moved.
        @Override
        protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws), h = MeasureSpec.getSize(hs);
            if (w > 0 && h > 0 && (w != fitW || h != fitH)) {
                fitW = w;
                fitH = h;
                fit(w, h);
            }
            super.onMeasure(ws, hs);
        }

        static LayoutParams at(int w, int h, int gravity, int l, int t, int r, int b) {
            LayoutParams p = new LayoutParams(w, h, gravity);
            p.setMargins(l, t, r, b);
            return p;
        }

        void clickable(View v, final OnClickListener l) {
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View x) {
                    Tap.feedback(x, host == null ? null : host.prefs());
                    l.onClick(x);
                }
            });
            v.setOnLongClickListener(edit);
        }
    }

    // ---------------------------------------------------------------- clock

    static final class ClockCard extends Card {
        final TextClock time = new TextClock(getContext()), date = new TextClock(getContext());
        /** Under the date: the next event today, else the weather now. */
        final TextView next = Ui.text(getContext(), Ui.MIN_TEXT, Ui.SILVER_DIM, "sans-serif", Gravity.CENTER);
        /** Whether the box is tall enough for the line under the date. */
        boolean roomNext = true;
        final LinearLayout col = new LinearLayout(getContext());

        ClockCard(Context c) {
            super(c);
            for (TextClock t : new TextClock[]{time, date}) {
                t.setIncludeFontPadding(false);
                t.setGravity(Gravity.CENTER);
                t.setSingleLine(true);
            }
            time.setFormat12Hour("h:mm");
            time.setFormat24Hour("HH:mm");
            time.setTextColor(Ui.SILVER);
            time.setTypeface(android.graphics.Typeface.create("sans-serif-thin", 0));
            date.setFormat12Hour("EEEE  d MMMM");
            date.setFormat24Hour("EEEE  d MMMM");
            date.setTextColor(Ui.SILVER_DIM);
            date.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            col.addView(time);
            col.addView(date);
            next.setEllipsize(android.text.TextUtils.TruncateAt.END);
            next.setVisibility(GONE);
            col.addView(next);
            addView(col, new LayoutParams(-1, -1));
            if (c instanceof HomeActivity) {
                status = new StatusIcons((HomeActivity) c, true);
                addView(status);
            }
        }

        /** Wi-Fi, Bluetooth, GPS and battery along the top right, like a phone's status bar (see HomeActivity.statusPlace). */
        StatusIcons status;

        @Override
        void fit(int w, int h) {
            if (status != null) {
                boolean here = "clock".equals(((HomeActivity) getContext()).statusPlace());
                status.setVisibility(here ? VISIBLE : GONE);
                int sh = Math.round(Math.max(22 * Ui.k, Math.min(h * 0.13f, 30 * Ui.k)));
                status.setLayoutParams(at(Math.round(sh * 1.5f * 5), sh, Gravity.TOP | Gravity.RIGHT, 0, Ui.px(8), Ui.px(10), 0));
            }
            // time, date and the line under it together take at most ~80% of the box height
            float big = Math.min(h * 0.42f, w * 0.17f);
            float small = Math.max(Ui.size(0), Math.min(big * 0.22f, w / 16f));
            float under = Math.max(Ui.size(0), small * 0.92f);
            time.setTextSize(0, big);
            date.setTextSize(0, small);
            date.setMaxWidth(w - Ui.px(24));
            date.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-2, -2);
            dp.topMargin = Math.round(big * 0.14f);
            time.setLayoutParams(tp);
            date.setLayoutParams(dp);
            next.setTextSize(0, under);
            next.setMaxWidth(w - Ui.px(24));
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, -2);
            np.topMargin = Math.round(small * 0.5f);
            next.setLayoutParams(np);
            // a low box: the line under the date goes first, then the date, so the time never runs over
            float timeH = big * 1.2f, dateH = dp.topMargin + small * 1.2f, nextH = np.topMargin + under * 1.2f;
            boolean dateFits = timeH + dateH <= h * 0.92f;
            roomNext = dateFits && timeH + dateH + nextH <= h * 0.92f;
            date.setVisibility(dateFits ? VISIBLE : GONE);
            next.setVisibility(roomNext && next.length() > 0 ? VISIBLE : GONE);
        }

        @Override
        public void changed(Hub h) {
            String t = nextLine(h, System.currentTimeMillis());
            if (!t.contentEquals(next.getText())) next.setText(t);
            next.setVisibility(t.isEmpty() || !roomNext ? GONE : VISIBLE);
        }

        /** "14:30  Dentist" for the next event today (from the calendar box's last read), else "23° Clear", else "". */
        String nextLine(Hub h, long now) {
            java.util.List<Agenda.Event> ev = CalendarCard.seen;
            if (ev != null && Features.on(host.prefs(), "calendar")) for (Agenda.Event e : ev) {
                if (e.end <= now) continue;
                String[] w = Agenda.when(e.start, e.end, e.allDay, now, java.util.TimeZone.getDefault());
                if (!"Today".equals(w[0])) break;
                return (w[1].isEmpty() ? "" : Hindi.t(w[1]) + "  ") + e.title;
            }
            if (h.temp == null || h.temp.startsWith("--")) return "";
            return h.temp + (h.weather == null || h.weather.isEmpty() || "Weather".equals(h.weather) ? "" : " " + Hindi.t(h.weather));
        }
    }

    // ---------------------------------------------------------------- weather

    /**
     * The weather now, large: the icon and temperature, what it's like and where; then the car's
     * altitude, the way it points and the air quality; the next two days' highs and lows; and, in a
     * tall box, the next 24 hours. Rows that don't fit the box's height are left out, last first.
     */
    static final class WeatherCard extends Card {
        final ImageView icon = new ImageView(getContext());
        final TextView temp = Ui.text(getContext(), 48, Ui.SILVER, "sans-serif-light", Gravity.LEFT);
        final TextView desc = Ui.text(getContext(), 17, Ui.SILVER, "sans-serif-medium", Gravity.RIGHT);
        final TextView place = Ui.text(getContext(), 14, Ui.SILVER_DIM, "sans-serif", Gravity.RIGHT);
        final TextView rain = Ui.text(getContext(), 13, 0xFF7FB8FF, "sans-serif-medium", Gravity.RIGHT);
        final FrameLayout top = new FrameLayout(getContext());
        final LinearLayout col = new LinearLayout(getContext()), left = new LinearLayout(getContext()), right = new LinearLayout(getContext());
        final LinearLayout facts = new LinearLayout(getContext()), days = new LinearLayout(getContext());
        final Glyph[] factIcon = new Glyph[3];
        final TextView[] factText = new TextView[3];
        final ImageView[] dayIcon = new ImageView[2];
        final TextView[] dayName = new TextView[2], dayTemp = new TextView[2];
        final Hours hours = new Hours(getContext());
        String lastIcon;
        final String[] lastDayIcon = new String[2];
        int w, h;

        WeatherCard(Context c) {
            super(c);
            col.setOrientation(LinearLayout.VERTICAL);
            left.setGravity(Gravity.CENTER_VERTICAL);
            left.addView(icon);
            left.addView(temp);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.RIGHT);
            right.addView(desc);
            right.addView(place);
            right.addView(rain);
            rain.setVisibility(GONE);
            top.addView(left, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL | Gravity.LEFT));
            top.addView(right, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
            col.addView(top);
            for (int i = 0; i < 3; i++) {
                LinearLayout one = new LinearLayout(c);
                one.setGravity(Gravity.CENTER_VERTICAL | (i == 0 ? Gravity.LEFT : i == 1 ? Gravity.CENTER_HORIZONTAL : Gravity.RIGHT));
                factIcon[i] = new Glyph(c, i);
                factText[i] = Ui.text(c, 16, Ui.SILVER, "sans-serif-medium", Gravity.LEFT);
                factText[i].setSingleLine(true);
                one.addView(factIcon[i]);
                one.addView(factText[i]);
                facts.addView(one, new LinearLayout.LayoutParams(0, -2, 1));
            }
            col.addView(facts);
            for (int i = 0; i < 2; i++) {
                LinearLayout one = new LinearLayout(c);
                one.setGravity(Gravity.CENTER_VERTICAL | (i == 0 ? Gravity.LEFT : Gravity.RIGHT));
                dayName[i] = Ui.text(c, 16, Ui.SILVER_DIM, "sans-serif-medium", Gravity.LEFT);
                dayName[i].setSingleLine(true);
                dayIcon[i] = new ImageView(c);
                dayTemp[i] = Ui.text(c, 17, Ui.SILVER, "sans-serif-medium", Gravity.LEFT);
                one.addView(dayName[i]);
                one.addView(dayIcon[i]);
                one.addView(dayTemp[i]);
                days.addView(one, new LinearLayout.LayoutParams(0, -2, 1));
            }
            col.addView(days);
            col.addView(hours);
            addView(col);
        }

        @Override
        void fit(int w, int h) {
            this.w = w;
            this.h = h;
            if (wide(w, h, Ui.k)) {
                fitWide(w, h);
                return;
            }
            wideNow = false;
            col.setOrientation(LinearLayout.VERTICAL);
            facts.setOrientation(LinearLayout.HORIZONTAL);
            days.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < 3; i++) facts.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            for (int i = 0; i < 2; i++) {
                ((LinearLayout) days.getChildAt(i)).setGravity(Gravity.CENTER_VERTICAL | (i == 0 ? Gravity.LEFT : Gravity.RIGHT));
                days.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            }
            float s = Math.min(1.4f, Math.min(h / (134f * Ui.k), w / (300f * Ui.k)));
            float sw = Math.min(1.4f, w / (300f * Ui.k));   // the rows below the reading scale with the width
            int m = Ui.px(20 * Math.min(s, 1));
            int is = Ui.px(64 * s);
            icon.setLayoutParams(new LinearLayout.LayoutParams(is, is));
            temp.setTextSize(0, Ui.size(48 * s));
            ((LinearLayout.LayoutParams) temp.getLayoutParams()).leftMargin = Ui.px(12 * s);
            desc.setTextSize(0, Ui.size(17 * s));
            place.setTextSize(0, Ui.size(14 * s));
            rain.setTextSize(0, Ui.size(13 * s));
            for (TextView t : new TextView[]{desc, place, rain}) {
                t.setMaxWidth(w / 2 - m);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            }
            int gs = Ui.px(22 * sw);
            for (int i = 0; i < 3; i++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(gs, gs);
                lp.rightMargin = Ui.px(6 * sw);
                factIcon[i].setLayoutParams(lp);
                factText[i].setTextSize(0, Ui.size(16 * sw));
            }
            int ds = Ui.px(32 * sw);
            for (int i = 0; i < 2; i++) {
                dayName[i].setTextSize(0, Ui.size(16 * sw));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ds, ds);
                lp.leftMargin = lp.rightMargin = Ui.px(8 * sw);
                dayIcon[i].setLayoutParams(lp);
                dayTemp[i].setTextSize(0, Ui.size(17 * sw));
            }
            // which rows fit, from the reading down: facts, then the two days, then the 24-hour curve
            int topH = Math.round(Math.max(is, 70 * Ui.k * s));
            int factH = Math.round(Math.max(30 * Ui.k * sw, Ui.size(16 * sw) * 1.3f));
            int dayH = Math.round(Math.max(40 * Ui.k * sw, Math.max(ds, Ui.size(17 * sw) * 1.3f)));
            int room = h - 2 * m - topH;
            boolean showFacts = room >= factH;
            if (showFacts) room -= factH;
            boolean showDays = room >= dayH && hasDays;
            if (showDays) room -= dayH;
            boolean curve = hours.wanted && room >= 80 * Ui.k;
            rightRoom = topH;
            rightRows();
            facts.setVisibility(showFacts ? VISIBLE : GONE);
            days.setVisibility(showDays ? VISIBLE : GONE);
            hours.setVisibility(curve ? VISIBLE : GONE);
            boolean spread = !curve;   // with no curve the rows share the height evenly
            top.setLayoutParams(new LinearLayout.LayoutParams(-1, spread ? 0 : topH, spread ? 1.3f : 0));
            facts.setLayoutParams(new LinearLayout.LayoutParams(-1, spread ? 0 : factH, spread ? 0.8f : 0));
            days.setLayoutParams(new LinearLayout.LayoutParams(-1, spread ? 0 : dayH, spread ? 1 : 0));
            hours.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
            col.setLayoutParams(at(-1, -1, Gravity.TOP, m, Math.round(m * 0.6f), m, Math.round(m * 0.6f)));
        }

        /** A wide, low box (a strip across the page): too low for rows, so the facts and the next two days stand beside the reading. Pure, for the tests. */
        static boolean wide(int w, int h, float k) {
            return w >= 2.6f * h && w >= 420 * k;
        }

        private void fitWide(int w, int h) {
            col.setOrientation(LinearLayout.HORIZONTAL);
            facts.setOrientation(LinearLayout.VERTICAL);
            days.setOrientation(LinearLayout.VERTICAL);
            float s = Math.min(1.4f, Math.min(h / (110f * Ui.k), w / (700f * Ui.k)));
            int m = Ui.px(16 * Math.min(s, 1));
            int inner = h - 2 * m;
            int is = Ui.px(64 * s);
            icon.setLayoutParams(new LinearLayout.LayoutParams(is, is));
            temp.setTextSize(0, Ui.size(48 * s));
            ((LinearLayout.LayoutParams) temp.getLayoutParams()).leftMargin = Ui.px(12 * s);
            desc.setTextSize(0, Ui.size(17 * s));
            place.setTextSize(0, Ui.size(14 * s));
            rain.setTextSize(0, Ui.size(13 * s));
            int part = (w - 2 * m) / 10;
            for (TextView t : new TextView[]{desc, place, rain}) {
                t.setMaxWidth(Math.round(1.8f * part));
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            }
            rightRoom = inner;
            rightRows();
            // three facts one under the other, two days one under the other, each sized to fit its column
            float fs = Math.min(1.5f, Math.min(inner / (3 * 28f * Ui.k), 2.4f * part / (100f * Ui.k)));
            int gs = Ui.px(22 * fs);
            for (int i = 0; i < 3; i++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(gs, gs);
                lp.rightMargin = Ui.px(8 * fs);
                factIcon[i].setLayoutParams(lp);
                factText[i].setTextSize(0, Ui.size(16 * fs));
                ((LinearLayout) facts.getChildAt(i)).setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
                facts.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
                // a low strip: only as many facts as there are lines for, the first ones kept
                facts.getChildAt(i).setVisibility(i < Math.max(1, (int) (inner / (Ui.size(16 * fs) * 1.25f))) ? VISIBLE : GONE);
            }
            float ds = Math.min(1.5f, Math.min(inner / (2 * 40f * Ui.k), 3.6f * part / (175f * Ui.k)));
            int di = Ui.px(32 * ds);
            for (int i = 0; i < 2; i++) {
                dayName[i].setTextSize(0, Ui.size(16 * ds));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(di, di);
                lp.leftMargin = lp.rightMargin = Ui.px(8 * ds);
                dayIcon[i].setLayoutParams(lp);
                dayTemp[i].setTextSize(0, Ui.size(17 * ds));
                ((LinearLayout) days.getChildAt(i)).setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
                days.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
                days.getChildAt(i).setVisibility(i < Math.max(1, (int) (inner / (Ui.size(17 * ds) * 1.3f))) ? VISIBLE : GONE);
            }
            facts.setVisibility(VISIBLE);
            days.setVisibility(hasDays ? VISIBLE : GONE);
            hours.setVisibility(GONE);
            top.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 3.8f));
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(0, -1, 2.6f);
            fl.leftMargin = Ui.px(16 * s);
            facts.setLayoutParams(fl);
            days.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 3.6f));
            wideNow = true;
            col.setLayoutParams(at(-1, -1, Gravity.TOP, m, m, m, m));
        }

        boolean hasDays, wideNow;
        /** The height beside the reading for what it's like, the rain line and the place. */
        int rightRoom;

        /** What it's like always; then the rain line, then the place, while there is height for them. */
        void rightRows() {
            float descH = desc.getTextSize() * 1.2f;
            boolean rainOn = rain.length() > 0;
            if (rightRoom <= 0) {   // not laid out yet
                rain.setVisibility(rainOn ? VISIBLE : GONE);
                return;
            }
            boolean rainFits = descH + rain.getTextSize() * 1.2f <= rightRoom;
            rain.setVisibility(rainOn && rainFits ? VISIBLE : GONE);
            float used = descH + (rainOn && rainFits ? rain.getTextSize() * 1.2f : 0);
            place.setVisibility(used + place.getTextSize() * 1.2f <= rightRoom ? VISIBLE : GONE);
        }

        @Override
        public void changed(Hub h) {
            temp.setText(h.temp);
            desc.setText(Hindi.t(h.weather));
            boolean aqOn = Features.on(host.prefs(), "aqi");
            int aq = aqOn ? Air.aqi : -1;
            place.setText(h.place);
            // rain on the planned route wins over rain here; both come and go
            String r = !Features.on(host.prefs(), "rain") ? "" : !Hub.routeRain.isEmpty() ? Hub.routeRain : Hub.showRain(h.rainSoon);
            // offline: say the reading comes from the kept forecast
            if (h.weatherSaved > 0) r = r.isEmpty() ? Weather.savedNote(h.weatherSaved, System.currentTimeMillis())
                    : r + "  ·  " + Hindi.t("offline");
            rain.setText(r);
            rightRows();
            if (!h.weatherIcon.equals(lastIcon)) {
                icon.setImageBitmap(Ui.img(getContext(), h.weatherIcon));
                lastIcon = h.weatherIcon;
            }
            // altitude, the way the car points (kept from the last drive while parked), the air
            android.location.Location f = h.fix;
            double alt = f != null && f.hasAltitude() ? f.getAltitude() : Drive.altNow;
            factText[0].setText(Double.isNaN(alt) ? "--" : Math.round(alt) + " m");
            double hd = Drive.headingNow >= 0 ? Drive.headingNow : Drive.headingKept >= 0 ? Drive.headingKept
                    : f != null && f.hasBearing() ? f.getBearing() : -1;
            factText[1].setText(hd < 0 ? "--" : Layouts.compass(hd) + " " + Math.round(hd) + "°");
            factIcon[1].turn((float) hd);
            factText[2].setText(aq < 0 ? (aqOn ? "AQI --" : "") : "AQI\u00A0" + aq);
            factIcon[2].setVisibility(aqOn ? VISIBLE : INVISIBLE);
            factIcon[2].colour(aqiColour(aq));
            factText[2].setTextColor(aq > 200 ? 0xFFFFB02E : Ui.SILVER);
            // the next two days
            java.util.List<String[]> fc = h.forecast;
            boolean had = hasDays;
            hasDays = fc != null && fc.size() >= 3;
            for (int i = 0; i < 2 && hasDays; i++) {
                String[] d = fc.get(i + 1);
                // a narrow column has room for the day's short name only
                dayName[i].setText(i == 0 && !wideNow ? Hindi.t("Tomorrow") : Weather.dayName(d[0]));
                dayTemp[i].setText(d.length > 3 ? d[2] + " / " + d[3] : d[2]);
                if (!d[1].equals(lastDayIcon[i])) {
                    dayIcon[i].setImageBitmap(Ui.img(getContext(), d[1]));
                    lastDayIcon[i] = d[1];
                }
            }
            boolean want = Features.on(host.prefs(), "hourlyWeather") && h.hours.length >= 6;
            hours.set(h.hours, h.hoursRain);
            if ((want != hours.wanted || had != hasDays) && this.w > 0) {
                hours.wanted = want;
                fit(this.w, this.h);
            }
            hours.wanted = want;
        }
    }

    /** The air quality's colour, as on India's AQI scale: green, yellow, orange, red, purple. Pure, for the tests. */
    static int aqiColour(int aqi) {
        if (aqi < 0) return Ui.SILVER_DIM;
        if (aqi <= 50) return 0xFF4CAF50;
        if (aqi <= 100) return 0xFFCDDC39;
        if (aqi <= 200) return 0xFFFFB02E;
        if (aqi <= 300) return 0xFFF4511E;
        return 0xFFAB47BC;
    }

    /** The small pictures in the weather box: a hill for the altitude, an arrow that turns with the heading, a dot for the air. */
    static final class Glyph extends View {
        final android.graphics.Paint ink = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final android.graphics.Path path = new android.graphics.Path();
        final int kind;
        float angle;
        int dot = Ui.SILVER_DIM;

        Glyph(Context c, int kind) {
            super(c);
            this.kind = kind;
        }

        void turn(float deg) {
            if (deg == angle) return;
            angle = deg;
            invalidate();
        }

        void colour(int c) {
            if (c == dot) return;
            dot = c;
            invalidate();
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            float w = getWidth(), h = getHeight();
            path.reset();
            ink.setStyle(android.graphics.Paint.Style.FILL);
            if (kind == 0) {
                ink.setColor(Ui.SILVER_DIM);
                path.moveTo(0, h * 0.9f);
                path.lineTo(w * 0.38f, h * 0.2f);
                path.lineTo(w * 0.6f, h * 0.55f);
                path.lineTo(w * 0.72f, h * 0.4f);
                path.lineTo(w, h * 0.9f);
                path.close();
                c.drawPath(path, ink);
            } else if (kind == 1) {
                ink.setColor(angle < 0 ? Ui.SILVER_DIM : 0xFFEA4335);
                c.save();
                c.rotate(Math.max(0, angle), w / 2, h / 2);
                path.moveTo(w / 2, h * 0.05f);
                path.lineTo(w * 0.85f, h * 0.92f);
                path.lineTo(w / 2, h * 0.7f);
                path.lineTo(w * 0.15f, h * 0.92f);
                path.close();
                c.drawPath(path, ink);
                c.restore();
            } else {
                ink.setColor(dot);
                c.drawCircle(w / 2, h / 2, Math.min(w, h) * 0.38f, ink);
            }
        }
    }

    /**
     * "Gurugram  ·  AQI 197". The air quality's number never goes without its label: when the line
     * is too wide, the place name is shortened ("Gurug…  ·  AQI 197"), never the AQI.
     */
    static CharSequence placeLine(String place, int aqi, android.text.TextPaint paint, int maxW) {
        if (aqi < 0) return place;
        String aq = "AQI\u00A0" + aqi;   // a space that never breaks the line
        if (place.isEmpty()) return aq;
        String sep = "  ·  ";
        if (maxW <= 0 || maxW == Integer.MAX_VALUE) return place + sep + aq;
        CharSequence p = android.text.TextUtils.ellipsize(place, paint, maxW - paint.measureText(sep + aq), android.text.TextUtils.TruncateAt.END);
        return p.length() == 0 ? aq : p + sep + aq;
    }

    /** The next 24 hours: a temperature curve with the hours marked, and a blue bar where rain is expected. */
    static final class Hours extends View {
        final android.graphics.Paint ink = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final android.graphics.Path path = new android.graphics.Path();
        float[] temps = new float[0], rain = new float[0], raw;
        boolean wanted;

        Hours(Context c) {
            super(c);
            setVisibility(GONE);
        }

        void set(float[] t, float[] r) {
            if (t == raw && java.util.Arrays.equals(r, rain)) return;
            raw = t;
            temps = new float[t.length];
            for (int i = 0; i < t.length; i++) temps[i] = (float) Units.temp(t[i], Units.fahrenheit);
            rain = r;
            invalidate();
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            int n = temps.length;
            if (n < 2) return;
            float lo = temps[0], hi = temps[0];
            for (float t : temps) {
                lo = Math.min(lo, t);
                hi = Math.max(hi, t);
            }
            float text = Math.max(Ui.size(0), getHeight() * 0.2f);
            float top = text * 1.3f, bottom = getHeight() - text * 1.4f, w = getWidth();
            float span = Math.max(4, hi - lo);
            ink.setTextSize(text);
            // rain bars along the bottom
            ink.setStyle(android.graphics.Paint.Style.FILL);
            ink.setColor(0x887FB8FF);
            for (int i = 0; i < rain.length && i < n; i++) {
                if (rain[i] < 0.3f) continue;
                float x = w * i / (n - 1);
                c.drawRect(x - w / n / 2, bottom - Math.min(1, rain[i] / 4f) * (bottom - top) * 0.5f, x + w / n / 2, bottom, ink);
            }
            path.reset();
            for (int i = 0; i < n; i++) {
                float x = w * i / (n - 1), y = bottom - (temps[i] - lo) / span * (bottom - top);
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            ink.setStyle(android.graphics.Paint.Style.STROKE);
            ink.setStrokeWidth(Math.max(1.5f, text * 0.15f));
            ink.setColor(Ui.SILVER);
            c.drawPath(path, ink);
            // the high and low, and every sixth hour along the bottom
            ink.setStyle(android.graphics.Paint.Style.FILL);
            ink.setColor(Ui.SILVER_DIM);
            ink.setTextAlign(android.graphics.Paint.Align.LEFT);
            c.drawText(Math.round(hi) + "° / " + Math.round(lo) + "°", 0, text, ink);
            int nowHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            for (int i = 0; i < n; i += 6) {
                float x = w * i / (n - 1);
                ink.setTextAlign(i == 0 ? android.graphics.Paint.Align.LEFT : android.graphics.Paint.Align.CENTER);
                c.drawText(i == 0 ? Hindi.t("Now") : String.format(Locale.US, "%02d", (nowHour + i) % 24), x, getHeight() - text * 0.2f, ink);
            }
        }
    }

    // ---------------------------------------------------------------- news

    /**
     * The top headlines, as many as the box has room for (up to News.KEEP); spare room lets the first
     * ones run to two lines. Read aloud reads the top five; tapping one reads just that one.
     */
    static final class NewsCard extends Card {
        final TextView head = Ui.text(getContext(), 13, Ui.SILVER_DIM, "sans-serif-medium", Gravity.LEFT);
        final TextView read = Ui.text(getContext(), 13, Ui.SILVER, "sans-serif-medium", Gravity.CENTER);
        final TextView[] lines = new TextView[News.KEEP], srcs = new TextView[News.KEEP], nums = new TextView[News.KEEP];
        final LinearLayout[] items = new LinearLayout[News.KEEP];
        final LinearLayout col = new LinearLayout(getContext());
        /** Headline rows that fit under the title, and how many headlines the lines were laid out for. */
        int room = News.COUNT, laidFor = -1;
        int shown = -1;
        /** Whether the box is tall enough for each paper's name under its headline. */
        boolean papers = true;
        boolean wasSpeaking;
        long lastAge;

        NewsCard(Context c) {
            super(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(head);
            for (int i = 0; i < lines.length; i++) {
                final int n = i;
                // each headline: a numbered badge in the accent colour, the headline, and its paper under it
                items[i] = new LinearLayout(c);
                items[i].setGravity(Gravity.CENTER_VERTICAL);
                nums[i] = Ui.text(c, 13, 0xFF15181C, "sans-serif-medium", Gravity.CENTER);
                nums[i].setText(String.valueOf(i + 1));
                LinearLayout words = new LinearLayout(c);
                words.setOrientation(LinearLayout.VERTICAL);
                lines[i] = Ui.text(c, 15, Ui.SILVER, i == 0 ? "sans-serif-medium" : "sans-serif", Gravity.LEFT);
                lines[i].setSingleLine(false);
                lines[i].setMaxLines(1);
                lines[i].setEllipsize(android.text.TextUtils.TruncateAt.END);
                lines[i].setLineSpacing(0, 1.1f);
                srcs[i] = Ui.text(c, 12, Ui.SILVER_DIM, "sans-serif", Gravity.LEFT);
                srcs[i].setSingleLine(true);
                srcs[i].setEllipsize(android.text.TextUtils.TruncateAt.END);
                words.addView(lines[i]);
                words.addView(srcs[i]);
                items[i].addView(nums[i]);
                items[i].addView(words, new LinearLayout.LayoutParams(0, -2, 1));
                clickable(items[i], new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (n < News.items.size()) Speak.sayLong(getContext(), News.read(News.items.get(n)));
                    }
                });
                col.addView(items[i]);
            }
            addView(col);
            android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
            pill.setCornerRadius(999);
            pill.setStroke(Math.max(1, Ui.px(1.5f)), Ui.EDGE);
            if (Glass.on) pill.setColor(Ui.PANEL_TOP);   // a glass pill
            read.setBackground(pill);
            read.setText(Hindi.t("▶  Read aloud"));
            clickable(read, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (Speak.speaking()) Speak.stop();
                    else {
                        if (News.items.isEmpty()) News.refresh(getContext(), host.prefs(), true);
                        Speak.sayLong(getContext(), News.read(News.items, News.topicName(host.prefs().str("newsTopic", "top"))));
                    }
                    postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            button();
                        }
                    }, 600);
                }
            });
            addView(read);
            clickable(this, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    News.refresh(getContext(), host.prefs(), true);
                }
            });
        }

        @Override
        void fit(int w, int h) {
            float s = Math.min(h / (300f * Ui.k), w / (420f * Ui.k));
            s = Math.max(0.55f, Math.min(1.3f, s));
            int m = Ui.px(18 * s);
            // a big target: it's pressed while driving, so at least HomeDock.TARGET tall
            int target = Ui.px(HomeDock.TARGET);
            read.setTextSize(0, Ui.size(17 * s));
            read.setPadding(Ui.px(22 * s), 0, Ui.px(22 * s), 0);
            read.setGravity(Gravity.CENTER);
            read.setLayoutParams(at(-2, target, Gravity.TOP | Gravity.RIGHT, 0, Ui.px(4 * s), m, 0));
            // the heading beside it, read at a glance; what's left of the width is its room
            read.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            Ui.heading(head, s, w - 2 * m - read.getMeasuredWidth() - Ui.px(8));
            int headH = Math.max(Ui.px(44 * s), target + Ui.px(4 * s) - Ui.px(14 * s));
            // larger headlines, each a soft rounded panel with its paper's name under it, at least a target tall
            float big = Ui.size(17.5f * s);
            float src = Ui.size(12.5f * s);
            int gap = Math.round(big * 0.45f);
            int pad = Math.max(Math.round(big * 0.45f), Math.round((target - big * 1.2f - src * 1.2f) / 2));
            float row = big * 1.3f + src * 1.35f + gap + 2 * pad;
            float avail = h - Ui.px(14 * s) - headH - m;
            // a low box: one headline without its paper's name, its panel only as tall as there is room for
            papers = avail >= row;
            if (!papers) pad = Math.max(Ui.px(2), Math.round((avail - big * 1.3f - gap) / 2));
            room = Math.max(1, (int) (avail / row));
            int badge = Math.round(big * 1.45f);
            for (int i = 0; i < lines.length; i++) {
                lines[i].setTextSize(0, i == 0 ? big * 1.08f : big);
                srcs[i].setTextSize(0, src);
                srcs[i].setVisibility(papers && srcs[i].length() > 0 ? VISIBLE : GONE);
                nums[i].setTextSize(0, Math.max(Ui.size(0), big * 0.75f));
                android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
                dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                dot.setColor(i == 0 ? Ui.ACCENT : Ui.SILVER_DIM);
                nums[i].setBackground(dot);
                nums[i].setTextColor(SettingsView.knob(i == 0 ? Ui.ACCENT : Ui.SILVER_DIM));
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(badge, badge);
                bl.rightMargin = Math.round(big * 0.7f);
                nums[i].setLayoutParams(bl);
                android.graphics.drawable.GradientDrawable card = new android.graphics.drawable.GradientDrawable();
                card.setCornerRadius(Ui.px(12 * s));
                card.setColor(0x14FFFFFF);
                items[i].setBackground(card);
                items[i].setPadding(pad, pad, pad, pad);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = gap;
                items[i].setLayoutParams(lp);
            }
            laidFor = -1;
            rows();
            col.setLayoutParams(at(-1, -2, Gravity.TOP | Gravity.LEFT, m, Ui.px(14 * s), m, 0));
            head.setLayoutParams(new LinearLayout.LayoutParams(-2, headH));
            head.setGravity(Gravity.CENTER_VERTICAL);
        }

        /**
         * Show as many headlines as there are rows for; with rows to spare (fewer headlines than
         * rows), the first ones may wrap to a second line, never more lines than the box holds.
         */
        void rows() {
            int have = Math.max(1, News.items.size());
            if (have == laidFor) return;
            laidFor = have;
            int n = Math.min(Math.min(lines.length, room), have);
            int spare = room - n;
            for (int i = 0; i < lines.length; i++) {
                items[i].setVisibility(i < n ? VISIBLE : GONE);
                lines[i].setMaxLines(i < spare ? 2 : 1);
            }
        }

        void button() {
            boolean on = Speak.speaking();
            wasSpeaking = on;
            read.setText(on ? Hindi.t("■  Stop") : Hindi.t("▶  Read aloud"));
        }

        @Override
        public void changed(Hub h) {
            Prefs p = host.prefs();
            News.refresh(getContext(), p, false);
            long now = System.currentTimeMillis();
            if (Speak.speaking() != wasSpeaking) button();
            if (shown == News.version && now - lastAge < 60000) return;
            shown = News.version;
            lastAge = now;
            rows();
            String age = News.age(now);
            String topic = News.topicName(p.str("newsTopic", "top"));
            head.setText((topic.equals("Top stories") ? Hindi.t("TOP NEWS") : Hindi.f("%1$s NEWS", Hindi.t(topic).toUpperCase(Locale.US)))
                    + (age.isEmpty() ? "" : "  ·  " + age + (Player.online(getContext()) ? "" : Hindi.t(", offline"))));
            for (int i = 0; i < lines.length; i++) {
                if (i < News.items.size()) {
                    News.Item it = News.items.get(i);
                    lines[i].setText(it.title);
                    lines[i].setTextColor(Ui.SILVER);
                    srcs[i].setText(it.source);
                    srcs[i].setVisibility(it.source.isEmpty() || !papers ? GONE : VISIBLE);
                    nums[i].setVisibility(VISIBLE);
                } else if (i == 0) {
                    lines[i].setText(!Features.on(p, "news") ? Hindi.t("The news box is switched off in Settings > Features.")
                            : Player.online(getContext()) ? Hindi.t("Getting the headlines…") : Hindi.t("Headlines show here when there's internet."));
                    lines[i].setTextColor(Ui.SILVER_DIM);
                    srcs[i].setVisibility(GONE);
                    nums[i].setVisibility(GONE);
                } else lines[i].setText("");
            }
        }
    }

    // ---------------------------------------------------------------- trip computer

    /** A label over a big figure, used by the trip and fuel boxes. */
    static final class Figure extends LinearLayout {
        final TextView value, label;

        Figure(Context c, String name) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            value = Ui.text(c, 30, Ui.SILVER, "sans-serif-light", Gravity.CENTER);
            label = Ui.label(c, Hindi.t(name));
            label.setGravity(Gravity.CENTER);
            // at the 16 px floor, tighter spacing keeps "LAST TANK KM/L" inside its third of the box
            label.setLetterSpacing(0.04f);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(value);
            addView(label);
            value.setText("--");
        }

        void size(float s) {
            value.setTextSize(0, Ui.size(30 * s));
            label.setTextSize(0, Ui.size(10 * s));
        }
    }

    /** Three figures in a row under a header; tapping opens the box's menu. */
    abstract static class FiguresCard extends Card {
        final TextView head;
        final TextView foot = Ui.text(getContext(), 12, Ui.SILVER_DIM, "sans-serif", Gravity.CENTER);
        /** One friendly line in the middle, in place of a row of "--" (see note()). */
        final TextView middle = Ui.text(getContext(), 14, Ui.SILVER_DIM, "sans-serif", Gravity.CENTER);
        final Figure[] figs;
        final LinearLayout row = new LinearLayout(getContext());

        FiguresCard(Context c, String title, String... names) {
            super(c);
            head = Ui.label(c, Hindi.t(title));
            figs = new Figure[names.length];
            row.setGravity(Gravity.CENTER_VERTICAL);
            for (int i = 0; i < names.length; i++) {
                figs[i] = new Figure(c, names[i]);
                row.addView(figs[i], new LinearLayout.LayoutParams(0, -2, 1));
            }
            foot.setSingleLine(true);
            foot.setEllipsize(android.text.TextUtils.TruncateAt.END);
            middle.setSingleLine(false);
            middle.setMaxLines(3);
            middle.setVisibility(GONE);
            addView(head);
            addView(row);
            addView(foot);
            addView(middle);
            clickable(this, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    tapped();
                }
            });
        }

        abstract void tapped();

        /** Show one centred line instead of the figures and the foot line, or (null) the figures again. */
        void note(String text) {
            boolean on = text != null;
            if (on && !text.contentEquals(middle.getText())) middle.setText(text);
            middle.setVisibility(on ? VISIBLE : GONE);
            row.setVisibility(on ? INVISIBLE : VISIBLE);
            foot.setVisibility(!roomFoot ? GONE : on ? INVISIBLE : VISIBLE);
        }

        @Override
        void fit(int w, int h) {
            float s = Math.min(h / (134f * Ui.k), w / (330f * Ui.k));
            int m = Ui.px(20 * s);
            Ui.heading(head, s, w - 2 * m);
            head.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.LEFT, m, Ui.px(12 * s), 0, 0));
            for (Figure f : figs) f.size(s);
            row.setLayoutParams(at(-1, -2, Gravity.CENTER_VERTICAL, m / 2, Ui.px(4 * s), m / 2, 0));
            foot.setTextSize(0, Ui.size(11 * s));
            foot.setLayoutParams(at(-1, -2, Gravity.BOTTOM, m, 0, m, Ui.px(10 * s)));
            // in a tall box the figures get room to grow
            if (h > w * 0.6f) for (Figure f : figs) f.value.setTextSize(0, Ui.size(38 * s));
            // a low box: the foot line goes first, then the heading drops to the smallest size, so
            // the figures in the middle never run into them
            float rowH = (figs[0].value.getTextSize() + figs[0].label.getTextSize()) * 1.2f;
            float rowTop = h / 2f + Ui.px(2 * s) - rowH / 2, rowBottom = rowTop + rowH;
            roomFoot = rowBottom <= h - Ui.px(10 * s) - foot.getTextSize() * 1.2f;
            if (Ui.px(12 * s) + head.getTextSize() * 1.2f > rowTop) head.setTextSize(0, Ui.size(0));
            head.setVisibility(Ui.px(12 * s) + head.getTextSize() * 1.2f > rowTop + Ui.px(4) ? GONE : VISIBLE);
            foot.setVisibility(roomFoot && middle.getVisibility() != VISIBLE ? VISIBLE : roomFoot ? INVISIBLE : GONE);
            // the friendly line in the middle: as many lines as fit between the heading and the bottom
            float mid = Ui.size(14 * s);
            middle.setTextSize(0, mid);
            middle.setMaxLines(Math.max(1, Math.min(3, (int) ((h - head.getTextSize() * 1.2f - Ui.px(22 * s)) / (mid * 1.2f) - 0.2f))));
            middle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            middle.setLayoutParams(at(-1, -2, Gravity.CENTER, m, Math.round(head.getTextSize() * 0.6f), m, 0));
        }

        /** Whether the box is tall enough for the foot line under the figures. */
        boolean roomFoot = true;
    }

    static final class TripCard extends FiguresCard {
        /** The day-by-day history, read again every few minutes for the last drive. */
        private org.json.JSONObject hist;
        private long histAt;

        TripCard(Context c) {
            super(c, "TODAY", "KM", "DRIVE TIME", "AVG KM/H");
        }

        @Override
        void tapped() {
            host.tripMenu();
        }

        /** Distance, time and average speed in the three figures. */
        void figures(double km, long sec) {
            figs[0].value.setText(km < 100 ? Fuel.fmt(km) : String.valueOf(Math.round(km)));
            long min = sec / 60;
            figs[1].value.setText(min < 60 ? min + "m" : (min / 60) + "h " + (min % 60) + "m");
            double avg = sec < 60 ? 0 : km / (sec / 3600.0);
            figs[2].value.setText(avg <= 0 ? "--" : String.valueOf(Math.round(avg)));
        }

        /**
         * No driving yet today: the last day that had some, labelled with when it was; or a friendly
         * line when nothing was ever recorded.
         */
        private void lastDrive() {
            long now = System.currentTimeMillis();
            if (hist == null || now - histAt > 5 * 60000) {
                hist = Drive.history(getContext());
                histAt = now;
            }
            String today = Times.today();
            String day = Drive.lastDay(hist, today);
            if (day == null) {
                head.setText(Hindi.t("TODAY"));
                note(Hindi.t("Your trips show here after your first drive"));
                return;
            }
            note(null);
            org.json.JSONArray a = hist.optJSONArray(day);
            figures(a.optDouble(0, 0), a.optLong(1, 0));
            int ago = Drive.daysBetween(day, today);
            head.setText(Hindi.f("LAST DRIVE, %1$s", ago <= 1 ? Hindi.t("YESTERDAY") : Hindi.f("%1$s DAYS AGO", ago)));
            foot.setText(Drive.parkAt == 0 ? "" : Hindi.f("Parked %1$s", ago(Drive.parkAt)));
        }

        @Override
        public void changed(Hub h) {
            if (Drive.todayKm < 0.05 && Drive.todaySec < 60) {
                lastDrive();
                return;
            }
            head.setText(Hindi.t("TODAY"));
            note(null);
            figures(Drive.todayKm, Drive.todaySec);
            String park = Drive.parkAt == 0 ? "" : "  ·  " + Hindi.f("parked %1$s", ago(Drive.parkAt));
            double rs = Features.on(host.prefs(), "fuelPrice") ? Fuel.cost(host.prefs(), Drive.todayKm) : 0;
            foot.setText(Hindi.f("This drive %1$s km", Fuel.fmt(Drive.driveKm)) + (rs >= 1 ? "  ·  " + Hindi.f("₹%1$s petrol today", Math.round(rs)) : "")
                    + (Features.on(host.prefs(), "score") ? "  ·  " + Hindi.f("score %1$s", Drive.score()) : "") + park);
        }

        static String ago(long t) {
            long m = (System.currentTimeMillis() - t) / 60000;
            if (m < 1) return Hindi.t("just now");
            if (m < 60) return Hindi.f("%1$s min ago", m);
            if (m < 48 * 60) return Hindi.f("%1$s h ago", m / 60);
            return Hindi.f("%1$s days ago", m / 1440);
        }
    }

    static String tollsThisMonth(Prefs p) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.DAY_OF_MONTH, 1);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        double rs = Care.spent(Care.tolls(p), c.getTimeInMillis(), Long.MAX_VALUE);
        return rs > 0 ? "  ·  " + Hindi.f("tolls ₹%1$s this month", Math.round(rs)) : "";
    }

    static final class FuelCard extends FiguresCard {
        FuelCard(Context c) {
            super(c, "FUEL", "LAST TANK", "AVERAGE", Money.shown("₹") + " / KM");
        }

        @Override
        void tapped() {
            host.fuel();
        }

        @Override
        public void changed(Hub h) {
            Fuel.Stats st = Fuel.stats(host.prefs());
            note(st.fills == 0 ? Hindi.t("Add your first fill-up to see mileage") : null);
            figs[0].value.setText(st.lastKmpl > 0 ? Fuel.fmt(st.lastKmpl) : "--");
            figs[1].value.setText(st.avgKmpl > 0 ? Fuel.fmt(st.avgKmpl) : "--");
            figs[2].value.setText(st.rupeesPerKm > 0 ? Fuel.fmt(st.rupeesPerKm) : "--");
            figs[0].label.setText(Hindi.t("LAST TANK KM/L"));
            figs[1].label.setText(Hindi.t("AVERAGE KM/L"));
            if (st.fills == 0) foot.setText(Hindi.t("Tap to add your first fill-up"));
            else if (st.lastKmpl <= 0) foot.setText(Hindi.t("Mileage shows after two full-tank fill-ups"));
            else foot.setText(Hindi.f("%1$s fill-ups  ·  last %2$s", st.fills, TripCard.ago(st.lastAt)) + tollsThisMonth(host.prefs()));
        }
    }

    // ---------------------------------------------------------------- cab

    /** Cab drivers: today's money and rides; the meter while it runs. Tap for the Cab menu. */
    static final class CabCard extends FiguresCard {
        CabCard(Context c) {
            super(c, "CAB  ·  TODAY", "EARNED", "RIDES", "NET");
        }

        @Override
        void tapped() {
            if (getContext() instanceof HomeActivity) Cab.menu((HomeActivity) getContext());
        }

        @Override
        public void changed(Hub h) {
            Prefs p = host.prefs();
            if (!Features.on(p, "cab")) {
                for (Figure f : figs) f.value.setText("--");
                foot.setText(Hindi.t("Turn on Cab driver tools in Settings > Cab driver"));
                return;
            }
            long now = System.currentTimeMillis();
            Cab.Sum s = Cab.sum(Cab.log(p), Cab.dayStart(now), now + 1);
            figs[0].value.setText(Cab.rs(s.in));
            figs[1].value.setText(String.valueOf(s.rides));
            figs[2].value.setText(Cab.rs(s.net()));
            String f;
            if (p.longNum("cabMeterAt", 0) > 0) {
                f = Hindi.f("Meter %1$s  ·  %2$s km  ·  %3$s min waiting", Cab.rs(CabMeter.fareNow(p)), Fuel.fmt(CabMeter.meterKm(p)), CabMeter.meterWaitMs(p) / 60000);
            } else {
                int target = p.num("cabTargetRs", 2000);
                long duty = p.longNum("cabDutyAt", 0);
                f = Cab.on(p, "cabTarget") && target > 0 ? (s.net() >= target ? Hindi.f("Target %1$s reached", Cab.rs(target)) : Hindi.f("%1$s to the target", Cab.rs(target - s.net())))
                        + (duty >= Cab.dayStart(now) ? "  ·  " + Hindi.f("on duty %1$s", Cab.hm(now - duty)) : "") : Hindi.t("Tap for the meter, rides and SOS");
            }
            foot.setText((Cab.quiet ? Hindi.t("Quiet ride") + "  ·  " : "") + f);
        }
    }

    private Cards() {
    }

    // ---------------------------------------------------------------- engine (OBD-II)

    /** How the unit is coping: frames per second, CPU use and the hottest temperature sensor. */
    static final class PerfCard extends FiguresCard {
        private final Floaters.Meter m = Floaters.Meter.INSTANCE;
        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                show();
                postDelayed(this, 1000);
            }
        };

        PerfCard(Context c) {
            super(c, "PERFORMANCE", "FPS", "CPU %", "TEMP °C", "FRAME MS");
        }

        private boolean measuring;

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            measure(getWindowVisibility() == VISIBLE);
        }

        @Override
        protected void onDetachedFromWindow() {
            measure(false);
            super.onDetachedFromWindow();
        }

        // counting frames every frame only while the box can be seen
        @Override
        protected void onWindowVisibilityChanged(int v) {
            super.onWindowVisibilityChanged(v);
            measure(v == VISIBLE && isAttachedToWindow());
        }

        private void measure(boolean on) {
            if (on == measuring) return;
            measuring = on;
            removeCallbacks(tick);
            if (on) {
                m.start();
                post(tick);
            } else m.stop();
        }

        @Override
        void tapped() {
            host.editSlot(slot);
        }

        @Override
        public void changed(Hub h) {
        }

        void show() {
            figs[0].value.setText(m.fps == 0 ? "--" : String.valueOf(m.fps));
            figs[0].value.setTextColor(m.fps > 0 && m.fps < 30 ? 0xFFF5C542 : Ui.SILVER);
            figs[1].value.setText(m.sysPct >= 0 ? String.valueOf(m.sysPct) : String.valueOf(m.appPct));
            figs[2].value.setText(m.temp < 0 ? "--" : String.valueOf(m.temp));
            figs[2].value.setTextColor(m.temp >= 80 ? 0xFFFF5A4E : m.temp >= 70 ? 0xFFF5C542 : Ui.SILVER);
            figs[3].value.setText(m.fps == 0 ? "--" : String.valueOf(m.worst));
            figs[3].value.setTextColor(m.worst > 50 ? 0xFFF5C542 : Ui.SILVER);
            String mem = memory(getContext());
            foot.setText((m.sysPct >= 0 ? Hindi.f("Whole unit %1$s%% CPU, this app %2$s%%", m.sysPct, m.appPct)
                    : Hindi.t("CPU is this app's share; Android hides the whole unit's figure")) + (mem.isEmpty() ? "" : "  ·  " + mem));
        }

        /** "memory 1.2 of 2.0 GB free", or "" when Android won't say. */
        static String memory(Context c) {
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
                android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                if (mi.totalMem <= 0) return "";
                String free = String.format(Locale.US, "%.1f", mi.availMem / 1073741824.0);
                String all = String.format(Locale.US, "%.1f", mi.totalMem / 1073741824.0);
                return (mi.lowMemory ? Hindi.t("memory low") + ", " : "") + Hindi.f("memory %1$s of %2$s GB free", free, all);
            } catch (Exception e) {
                return "";
            }
        }
    }

    /** Where the car points and how high it is, from GPS: heading while moving (the last one when stopped), altitude. */
    static final class CompassCard extends FiguresCard {
        CompassCard(Context c) {
            super(c, "COMPASS", "HEADING", "DEGREES", "ALTITUDE M");
        }

        @Override
        void tapped() {
            host.editSlot(slot);
        }

        @Override
        public void changed(Hub h) {
            // parked: the last heading while driving (kept over a restart by the trip service), else the GPS's own
            android.location.Location f = h.fix;
            double hd = Drive.headingNow >= 0 ? Drive.headingNow : Drive.headingKept >= 0 ? Drive.headingKept
                    : f != null && f.hasBearing() ? f.getBearing() : -1;
            figs[0].value.setText(hd < 0 ? "--" : Hindi.t(point(hd)));
            figs[1].value.setText(hd < 0 ? "--" : Math.round(hd) % 360 + "°");
            double alt = !Double.isNaN(Drive.altNow) ? Drive.altNow : f != null && f.hasAltitude() ? f.getAltitude() : Double.NaN;
            figs[2].value.setText(Double.isNaN(alt) ? "--" : String.valueOf(Math.round(alt)));
            foot.setText(hd < 0 ? Hindi.t("The heading shows once the car moves")
                    : Drive.headingNow < 0 ? Hindi.t("Last heading while driving")
                    : Hindi.t("GPS altitude is within about 10 m"));
        }

        /** N, NE, E … for a heading in degrees. */
        static String point(double deg) {
            String[] p = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
            return p[(int) Math.round(((deg % 360) + 360) % 360 / 45) % 8];
        }
    }

    /** Six sound pads (Soundboard); they play only when the car is stopped. */
    static final class SoundCard extends Card {
        final TextView head = Ui.label(getContext(), Hindi.t("SOUNDBOARD"));
        final TextView note = Ui.text(getContext(), 12, Ui.SILVER_DIM, "sans-serif", Gravity.CENTER);
        final LinearLayout grid = new LinearLayout(getContext());
        final TextView[] pads = new TextView[Soundboard.PADS.length];

        SoundCard(Context c) {
            super(c);
            grid.setOrientation(LinearLayout.VERTICAL);
            LinearLayout row = null;
            for (int i = 0; i < pads.length; i++) {
                if (i % 3 == 0) {
                    row = new LinearLayout(c);
                    grid.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
                }
                final int pad = i;
                pads[i] = PhoneCard.pill(c, Hindi.t(Soundboard.PADS[i]), 0);
                row.addView(pads[i], new LinearLayout.LayoutParams(0, -1, 1));
                clickable(pads[i], new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!Features.on(host.prefs(), "soundboard")) {
                            android.widget.Toast.makeText(getContext(), Hindi.t("Turn on Soundboard in Settings, Radio and music"), android.widget.Toast.LENGTH_LONG).show();
                            return;
                        }
                        Soundboard.play(getContext(), pad);
                    }
                });
            }
            note.setSingleLine(true);
            addView(head);
            addView(grid);
            addView(note);
        }

        @Override
        void fit(int w, int h) {
            float s = Math.min(h / (134f * Ui.k), w / (330f * Ui.k));
            int m = Ui.px(16 * s), g = Ui.px(5 * s);
            Ui.heading(head, s, w - 2 * m);
            head.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.LEFT, m, Ui.px(10 * s), 0, 0));
            for (TextView p : pads) {
                p.setTextSize(0, Ui.size(13 * s));
                p.setSingleLine(true);
                p.setEllipsize(android.text.TextUtils.TruncateAt.END);
                ((LinearLayout.LayoutParams) p.getLayoutParams()).setMargins(g, g, g, g);
            }
            note.setTextSize(0, Ui.size(10 * s));
            note.setLayoutParams(at(-1, -2, Gravity.BOTTOM, m, 0, m, Ui.px(6 * s)));
            // the pads in rows of three, each row a full touch target tall: a low box shows one row.
            // The note (only while moving, when the pads are dimmed and don't play) sits over their lower edge.
            int top = Math.round(Ui.px(10 * s) + head.getTextSize() * 1.2f + Ui.px(4 * s));
            int bottom = Ui.px(6 * s);
            int rows = Math.max(1, Math.min(2, (h - top - bottom) / (Ui.px(HomeDock.TARGET) + 2 * g)));
            for (int i = 0; i < grid.getChildCount(); i++) grid.getChildAt(i).setVisibility(i < rows ? VISIBLE : GONE);
            grid.setLayoutParams(at(-1, -1, Gravity.TOP, m - g, top, m - g, bottom));
        }

        @Override
        public void changed(Hub h) {
            boolean moving = Drive.kmhNow > Soundboard.PARKED_KMH;
            for (TextView p : pads) p.setAlpha(moving ? 0.4f : 1f);
            note.setText(moving ? Hindi.t("Plays when the car is stopped") : "");
        }
    }

    /** Today's and the next few days' events from the unit's calendars. A tap opens the calendar app. */
    static final class CalendarCard extends Card {
        final TextView head = Ui.label(getContext(), Hindi.t("CALENDAR"));
        final TextView note = Ui.text(getContext(), 12, Ui.SILVER_DIM, "sans-serif", Gravity.CENTER);
        final LinearLayout col = new LinearLayout(getContext());
        final TextView[] lines = new TextView[Agenda.MAX];
        /** The events last read, for the clock box's line under the date; null before the first read. */
        static volatile java.util.List<Agenda.Event> seen;
        long readAt;
        boolean reading;
        int fitLines = Agenda.MAX;

        CalendarCard(Context c) {
            super(c);
            col.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < lines.length; i++) {
                lines[i] = Ui.text(c, 14, Ui.SILVER, "sans-serif", Gravity.LEFT);
                lines[i].setSingleLine(true);
                lines[i].setEllipsize(android.text.TextUtils.TruncateAt.END);
                col.addView(lines[i]);
            }
            note.setSingleLine(false);
            note.setMaxLines(3);
            addView(head);
            addView(col);
            addView(note);
            clickable(this, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!Features.on(host.prefs(), "calendar")) {
                        android.widget.Toast.makeText(getContext(), Hindi.t("Turn on Calendar in Settings, Home screen"), android.widget.Toast.LENGTH_LONG).show();
                    } else if (!Agenda.allowed(getContext()) && getContext() instanceof android.app.Activity) {
                        ((android.app.Activity) getContext()).requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR}, 7);
                        readAt = 0;
                    } else {
                        try {
                            getContext().startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("content://com.android.calendar/time/" + System.currentTimeMillis()))
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                        } catch (Exception e) {
                            android.widget.Toast.makeText(getContext(), Hindi.t("No calendar app on this unit"), android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });
        }

        @Override
        void fit(int w, int h) {
            float s = Math.min(h / (134f * Ui.k), w / (330f * Ui.k));
            int m = Ui.px(16 * s);
            Ui.heading(head, s, w - 2 * m);
            head.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.LEFT, m, Ui.px(10 * s), 0, 0));
            float line = Ui.size(14 * Math.max(0.8f, Math.min(1.4f, s)));
            for (TextView t : lines) t.setTextSize(0, line);
            int top = Math.round(Ui.px(10 * s) + head.getTextSize() * 1.2f + Ui.px(4 * s));
            // the events that fit between the heading and the note line at the bottom
            float noteH = Ui.size(11 * s) * 1.2f + Ui.px(8 * s);
            fitLines = Math.max(1, Math.min(Agenda.MAX, (int) ((h - top - noteH) / (line * 1.35f))));
            col.setLayoutParams(at(-1, -2, Gravity.TOP, m, top, m, 0));
            this.w = w;
            this.h = h;
            placeNote();
        }

        @Override
        public void changed(Hub h) {
            final android.content.Context c = getContext();
            if (!Features.on(host.prefs(), "calendar")) {
                show(null, Hindi.t("Calendar is off in Settings, Home screen"));
                return;
            }
            if (!Agenda.allowed(c)) {
                show(null, Hindi.t("Tap to allow OpenRath to read your calendar"));
                return;
            }
            long now = System.currentTimeMillis();
            if (reading || now - readAt < 5 * 60000) return;
            reading = true;
            readAt = now;
            Bg.run(new Runnable() {
                @Override
                public void run() {
                    final java.util.List<Agenda.Event> ev = Agenda.read(c, System.currentTimeMillis());
                    seen = ev;
                    post(new Runnable() {
                        @Override
                        public void run() {
                            reading = false;
                            show(ev, ev.isEmpty() ? nothing() : "");
                        }
                    });
                }
            });
        }

        /** No events: say so, with the next holiday when there is one (India's list only). */
        static String nothing() {
            java.util.List<String> hol = Holidays.upcoming(java.util.Calendar.getInstance(), 1);
            return Hindi.t("Nothing in the next few days") + (hol.isEmpty() ? "" : "\n" + Hindi.f("Next holiday: %1$s", hol.get(0)));
        }

        private int w, h;
        /** Nothing to list: the message sits in the middle of the box instead of under a gap. */
        private boolean blank = true;

        private void placeNote() {
            float s = Math.min(h / (134f * Ui.k), w / (330f * Ui.k));
            int m = Ui.px(16 * s);
            float sz = Ui.size((blank ? 13 : 11) * s);
            note.setTextSize(0, sz);
            note.setMaxLines(blank ? Math.max(1, Math.min(3, (int) ((h - head.getTextSize() * 1.2f - Ui.px(20 * s)) / (sz * 1.2f)))) : 1);
            note.setEllipsize(android.text.TextUtils.TruncateAt.END);
            note.setLayoutParams(blank ? at(-1, -2, Gravity.CENTER, m, Ui.px(10 * s), m, 0) : at(-1, -2, Gravity.BOTTOM, m, 0, m, Ui.px(8 * s)));
        }

        private void show(java.util.List<Agenda.Event> ev, String foot) {
            long now = System.currentTimeMillis();
            boolean empty = ev == null || ev.isEmpty();
            if (empty != blank) {
                blank = empty;
                if (w > 0) placeNote();
            }
            for (int i = 0; i < lines.length; i++) {
                boolean has = ev != null && i < ev.size() && i < fitLines;
                lines[i].setVisibility(has ? VISIBLE : GONE);
                if (!has) continue;
                Agenda.Event e = ev.get(i);
                String[] w = Agenda.when(e.start, e.end, e.allDay, now, java.util.TimeZone.getDefault());
                lines[i].setText(Hindi.t(w[0]) + (w[1].isEmpty() ? "" : " " + Hindi.t(w[1])) + "   " + e.title);
            }
            note.setText(foot);
        }
    }

    /**
     * A video from the unit or a USB stick. The picture shows only while GPS says the car is
     * stopped; while moving it is covered and the sound keeps going.
     */
    static final class VideoCard extends Card {
        final android.widget.VideoView video = new android.widget.VideoView(getContext());
        final TextView cover = Ui.text(getContext(), 14, Ui.SILVER_DIM, "sans-serif", Gravity.CENTER);
        String path;

        VideoCard(Context c) {
            super(c);
            addView(video, new LayoutParams(-1, -1, Gravity.CENTER));
            cover.setBackgroundColor(0xFF000000);
            addView(cover, new LayoutParams(-1, -1));
            video.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(android.media.MediaPlayer mp) {
                    path = null;
                    cover();
                }
            });
            video.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(android.media.MediaPlayer mp, int what, int extra) {
                    path = null;
                    cover.setText(Hindi.t("This video can't be played on this unit"));
                    return true;
                }
            });
            clickable(cover, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    tapped();
                }
            });
            video.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, android.view.MotionEvent e) {
                    if (e.getAction() == android.view.MotionEvent.ACTION_UP) tapped();
                    return true;
                }
            });
            cover();
        }

        private void tapped() {
            if (!Features.on(host.prefs(), "video")) {
                android.widget.Toast.makeText(getContext(), Hindi.t("Turn on Video window in Settings, Radio and music"), android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            if (path != null) {
                if (video.isPlaying()) video.pause();
                else video.start();
                return;
            }
            cover.setText(Hindi.t("Looking for videos…"));
            Bg.run(new Runnable() {
                @Override
                public void run() {
                    final java.util.List<String> found = Videos.find(getContext());
                    post(new Runnable() {
                        @Override
                        public void run() {
                            cover();
                            choose(found);
                        }
                    });
                }
            });
        }

        private void choose(final java.util.List<String> found) {
            if (found.isEmpty()) {
                android.widget.Toast.makeText(getContext(), Hindi.t("No videos found on the unit or USB stick"), android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            String[] names = new String[found.size()];
            for (int i = 0; i < names.length; i++) names[i] = new java.io.File(found.get(i)).getName();
            new Glass.Builder(getContext(), android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(Hindi.t("Play a video"))
                    .setItems(names, new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w) {
                            path = found.get(w);
                            video.setVideoPath(path);
                            video.start();
                            cover();
                        }
                    }).show();
        }

        private void cover() {
            boolean parked = Videos.parked(Drive.kmhNow, Drive.fixAt == 0 ? -1 : System.currentTimeMillis() - Drive.fixAt);
            boolean show = path != null && parked;
            cover.setVisibility(show ? GONE : VISIBLE);
            cover.setText(path == null ? Hindi.t("Tap to play a video. The picture shows only when the car is stopped.")
                    : Hindi.t("The picture shows when the car is stopped"));
        }

        @Override
        void fit(int w, int h) {
            float s = Math.min(h / (134f * Ui.k), w / (330f * Ui.k));
            float sz = Ui.size(13 * s);
            cover.setTextSize(0, sz);
            cover.setPadding(Ui.px(16 * s), 0, Ui.px(16 * s), 0);
            cover.setSingleLine(false);
            cover.setMaxLines(Math.max(1, (int) (h * 0.85f / (sz * 1.2f))));
            cover.setEllipsize(android.text.TextUtils.TruncateAt.END);
        }

        @Override
        public void changed(Hub h) {
            cover();
        }

        @Override
        protected void onDetachedFromWindow() {
            video.stopPlayback();
            super.onDetachedFromWindow();
        }
    }

    static final class ObdCard extends FiguresCard {
        ObdCard(Context c) {
            super(c, "ENGINE", "RPM", "COOLANT °C", "BATTERY V");
        }

        @Override
        void tapped() {
            host.obdMenu();
        }

        @Override
        public void changed(Hub h) {
            Prefs p = host.prefs();
            if (!Features.on(p, "obd")) {
                note(Hindi.t("Engine data is off in Settings > Car"));
                return;
            }
            // no adapter chosen yet: one line instead of a row of dashes
            note(p.str("obdMac", "").isEmpty() ? Hindi.t("Connect an OBD adapter to see engine data") : null);
            boolean live = Obd.connected && System.currentTimeMillis() - Obd.readAt < 5000;
            figs[0].value.setText(live && Obd.rpm >= 0 ? String.valueOf(Obd.rpm) : "--");
            figs[1].value.setText(live && Obd.coolant > -40 ? String.valueOf(Obd.coolant) : "--");
            figs[2].value.setText(live && Obd.volts > 0 ? String.format(Locale.US, "%.1f", Obd.volts) : "--");
            boolean hot = live && Obd.coolant >= p.num("obdHot", 105);
            figs[1].value.setTextColor(hot ? 0xFFFF5A4E : Ui.SILVER);
            boolean lowV = live && Obd.volts > 0 && Obd.volts < 11.8f;
            figs[2].value.setTextColor(lowV ? 0xFFF5C542 : Ui.SILVER);
            int n;
            synchronized (Obd.codes) {
                n = Obd.codes.size();
            }
            if (p.str("obdMac", "").isEmpty()) foot.setText(Hindi.t("Tap to choose your OBD-II Bluetooth adapter"));
            else if (!live) foot.setText(Hindi.t(Obd.status));
            else foot.setText((Obd.load >= 0 ? Hindi.f("Load %1$s%%", Obd.load) : "")
                    + (Obd.fuel >= 0 ? "  ·  " + Hindi.f("Fuel %1$s%%", Obd.fuel) : "")
                    + (Obd.intake > -999 ? "  ·  " + Hindi.f("Intake %1$s°C", Obd.intake) : "") + (n > 0 ? "  ·  " + (n == 1 ? Hindi.t("1 fault code") : Hindi.f("%1$s fault codes", n)) : Obd.codesAt > 0 ? "  ·  " + Hindi.t("no fault codes") : ""));
        }
    }

    // ---------------------------------------------------------------- tyres (TPMS)

    static final class TyresCard extends FiguresCard {
        TyresCard(Context c) {
            super(c, "TYRES", "FRONT L", "FRONT R", "REAR L", "REAR R");
        }

        @Override
        void tapped() {
            host.tyresMenu();
        }

        @Override
        public void changed(Hub h) {
            Prefs p = host.prefs();
            String unit = "bar".equals(p.str("tpmsUnit", "psi")) ? "BAR" : "PSI";
            head.setText(Hindi.f("TYRES  ·  %1$s", unit));
            if (!Features.on(p, "tpms")) {
                for (Figure f : figs) f.value.setText("--");
                foot.setText(Hindi.t("Tyre sensors are off in Settings > Car"));
                return;
            }
            String warn = "";
            boolean any = false;
            for (int w = 0; w < 4; w++) {
                Tpms.Tyre t = Tpms.tyres[w];
                boolean fresh = t != null && System.currentTimeMillis() - t.at < 15 * 60 * 1000;
                figs[w].value.setText(fresh ? Tpms.show(p, t.kpa) : "--");
                String wn = Tpms.warning(p, w);
                figs[w].value.setTextColor(wn.isEmpty() ? Ui.SILVER : 0xFFFF5A4E);
                if (warn.isEmpty()) warn = wn;
                any |= !p.str("tpms" + w, "").isEmpty();
            }
            if (!any) foot.setText(Hindi.t("Tap to find your tyre sensors"));
            else if (!warn.isEmpty()) foot.setText(Hindi.t(warn));
            else {
                Tpms.Tyre t = Tpms.tyres[0];
                foot.setText(t != null && !Float.isNaN(t.tempC) ? Hindi.f("Front left %1$s°C  ·  %2$s", Math.round(t.tempC), Hindi.t(Tpms.status)) : Hindi.t(Tpms.status));
            }
        }
    }

    // ---------------------------------------------------------------- another app's widget

    static final class WidgetCard extends Card {
        final String content;
        final TextView hint = Ui.text(getContext(), 15, Ui.SILVER_DIM, "sans-serif", Gravity.CENTER);
        View hosted;

        WidgetCard(Context c, final String content, Host host) {
            super(c);
            this.content = content;
            this.host = host;
            hint.setSingleLine(false);
            Prefs p = host.prefs();
            int id = p.num("widgetId:" + content, -1);
            if (id >= 0 && Features.on(p, "widgets")) {
                try {
                    android.appwidget.AppWidgetProviderInfo info = android.appwidget.AppWidgetManager.getInstance(c).getAppWidgetInfo(id);
                    if (info != null) {
                        hosted = host.widgetHost().createView(c.getApplicationContext(), id, info);
                        addView(hosted, new LayoutParams(-1, -1));
                    }
                } catch (Throwable e) {
                    hosted = null;
                }
            }
            if (hosted == null) {
                hint.setText(Features.on(p, "widgets") ? Hindi.t("Tap to choose a widget from another app\n(long-press for other box contents)")
                        : Hindi.t("Widgets are off in Settings > Home screen"));
                addView(hint, new LayoutParams(-1, -1));
                clickable(hint, new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        WidgetCard.this.host.pickWidget(WidgetCard.this.content);
                    }
                });
            }
        }

        @Override
        void fit(int w, int h) {
            int m = Ui.px(10);
            if (hosted != null) {
                hosted.setLayoutParams(at(-1, -1, Gravity.CENTER, m, m, m, m));
                // tell the widget its size so it can pick a layout
                if (hosted instanceof android.appwidget.AppWidgetHostView) {
                    float d = getResources().getDisplayMetrics().density;
                    int wd = Math.round((w - 2 * m) / d), hd = Math.round((h - 2 * m) / d);
                    try {
                        ((android.appwidget.AppWidgetHostView) hosted).updateAppWidgetSize(null, wd, hd, wd, hd);
                    } catch (Throwable ignored) {
                        // runs on every layout: the widget keeps its old size
                    }
                }
            }
        }

        @Override
        public void changed(Hub h) {
        }
    }
}
