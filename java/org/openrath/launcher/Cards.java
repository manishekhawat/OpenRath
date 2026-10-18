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
        else if (content.startsWith("widget")) card = new WidgetCard(c, content, host);
        else card = new MediaCard(c, Prefs.RADIO.equals(content));
        card.host = host;
        card.slot = slot;
        card.setOnLongClickListener(card.edit);
        return card;
    }

    /** The map box, or a note when the unit has no working WebView (some stereos ship without one). */
    static Card map(Context c) {
        try {
            return new MapCard(c);
        } catch (Throwable e) {
            return new NoMapCard(c);
        }
    }

    static final class NoMapCard extends Card {
        final TextView note = new TextView(getContext());

        NoMapCard(Context c) {
            super(c);
            note.setText(Hindi.t("The map needs Android System WebView. Install or turn it on in Android's settings."));
            note.setTextColor(Ui.SILVER_DIM);
            note.setGravity(Gravity.CENTER);
            addView(note, new LayoutParams(-1, -1));
        }

        @Override
        void fit(int w, int h) {
            note.setTextSize(0, Math.min(Math.max(w / 22f, 11 * Ui.k), h / 6f));
            note.setPadding(w / 12, 0, w / 12, 0);
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

        void clickable(View v, OnClickListener l) {
            v.setOnClickListener(l);
            v.setOnLongClickListener(edit);
        }
    }

    // ---------------------------------------------------------------- clock

    static final class ClockCard extends Card {
        final TextClock time = new TextClock(getContext()), date = new TextClock(getContext());
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
            addView(col, new LayoutParams(-1, -1));
        }

        @Override
        void fit(int w, int h) {
            // time, gap and date together take at most ~80% of the box height
            float big = Math.min(h * 0.46f, w * 0.17f);
            float small = Math.min(Math.max(big * 0.22f, 11 * Ui.k), w / 16f);
            time.setTextSize(0, big);
            date.setTextSize(0, small);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-2, -2);
            dp.topMargin = Math.round(big * 0.14f);
            time.setLayoutParams(tp);
            date.setLayoutParams(dp);
        }

        @Override
        public void changed(Hub h) {
        }
    }

    // ---------------------------------------------------------------- weather

    static final class WeatherCard extends Card {
        final ImageView icon = new ImageView(getContext());
        final TextView temp = Ui.text(getContext(), 40, Ui.SILVER, "sans-serif-light", Gravity.LEFT);
        final TextView desc = Ui.text(getContext(), 15, Ui.SILVER, "sans-serif-medium", Gravity.RIGHT);
        final TextView place = Ui.text(getContext(), 13, Ui.SILVER_DIM, "sans-serif", Gravity.RIGHT);
        final TextView rain = Ui.text(getContext(), 13, 0xFF7FB8FF, "sans-serif-medium", Gravity.RIGHT);
        final LinearLayout left = new LinearLayout(getContext()), right = new LinearLayout(getContext());
        String lastIcon;

        WeatherCard(Context c) {
            super(c);
            left.setGravity(Gravity.CENTER_VERTICAL);
            left.addView(icon);
            left.addView(temp);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.RIGHT);
            right.addView(desc);
            right.addView(place);
            right.addView(rain);
            rain.setVisibility(GONE);
            addView(left);
            addView(right);
        }

        @Override
        void fit(int w, int h) {
            float s = Math.min(h / (134f * Ui.k), w / (300f * Ui.k));
            int m = Ui.px(24 * s);
            int is = Ui.px(56 * s);
            icon.setLayoutParams(new LinearLayout.LayoutParams(is, is));
            temp.setTextSize(0, 40 * Ui.k * s);
            ((LinearLayout.LayoutParams) temp.getLayoutParams()).leftMargin = Ui.px(16 * s);
            desc.setTextSize(0, 15 * Ui.k * s);
            place.setTextSize(0, 13 * Ui.k * s);
            desc.setMaxWidth(w / 2 - m);
            place.setMaxWidth(w / 2 - m);
            rain.setTextSize(0, 12 * Ui.k * s);
            rain.setMaxWidth(w / 2 - m);
            left.setLayoutParams(at(-2, -2, Gravity.CENTER_VERTICAL | Gravity.LEFT, m, 0, 0, 0));
            right.setLayoutParams(at(-2, -2, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, m, 0));
        }

        @Override
        public void changed(Hub h) {
            temp.setText(h.temp);
            desc.setText(Hindi.t(h.weather));
            int aq = Features.on(host.prefs(), "aqi") ? Air.aqi : -1;
            place.setText(aq < 0 ? h.place : h.place + (h.place.isEmpty() ? "" : "  ·  ") + "AQI " + aq);
            place.setTextColor(aq > 200 ? 0xFFFFB02E : Ui.SILVER_DIM);
            // rain on the planned route wins over rain here; both come and go
            String r = !Features.on(host.prefs(), "rain") ? "" : !Hub.routeRain.isEmpty() ? Hub.routeRain : Hub.showRain(h.rainSoon);
            // offline: say the reading comes from the kept forecast
            if (h.weatherSaved > 0) r = r.isEmpty() ? Weather.savedNote(h.weatherSaved, System.currentTimeMillis())
                    : r + "  ·  " + Hindi.t("offline");
            rain.setText(r);
            rain.setVisibility(r.isEmpty() ? GONE : VISIBLE);
            if (!h.weatherIcon.equals(lastIcon)) {
                icon.setImageBitmap(Ui.img(getContext(), h.weatherIcon));
                lastIcon = h.weatherIcon;
            }
        }
    }

    // ---------------------------------------------------------------- news

    /** The top five headlines. Read aloud reads them all; tapping one reads just that one. */
    static final class NewsCard extends Card {
        final TextView head = Ui.text(getContext(), 13, Ui.SILVER_DIM, "sans-serif-medium", Gravity.LEFT);
        final TextView read = Ui.text(getContext(), 13, Ui.SILVER, "sans-serif-medium", Gravity.CENTER);
        final TextView[] lines = new TextView[News.COUNT];
        final LinearLayout col = new LinearLayout(getContext());
        int shown = -1;
        boolean wasSpeaking;
        long lastAge;

        NewsCard(Context c) {
            super(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(head);
            for (int i = 0; i < lines.length; i++) {
                final int n = i;
                lines[i] = Ui.text(c, 15, Ui.SILVER, "sans-serif", Gravity.LEFT);
                lines[i].setSingleLine(true);
                lines[i].setEllipsize(android.text.TextUtils.TruncateAt.END);
                clickable(lines[i], new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (n < News.items.size()) Speak.sayLong(getContext(), News.read(News.items.get(n)));
                    }
                });
                col.addView(lines[i]);
            }
            addView(col);
            android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
            pill.setCornerRadius(999);
            pill.setStroke(Math.max(1, Ui.px(1.5f)), Ui.EDGE);
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
            float big = 16 * Ui.k * s, small = 12.5f * Ui.k * s;
            head.setTextSize(0, small);
            // a big target: it's pressed while driving
            read.setTextSize(0, 17 * Ui.k * s);
            read.setPadding(Ui.px(22 * s), Ui.px(9 * s), Ui.px(22 * s), Ui.px(9 * s));
            read.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.RIGHT, 0, Ui.px(10 * s), m, 0));
            // as many headlines as fit under the title row
            float row = big * 1.55f;
            int room = (int) ((h - Ui.px(62 * s) - m) / row);
            int n = Math.max(1, Math.min(News.COUNT, room));
            for (int i = 0; i < lines.length; i++) {
                lines[i].setTextSize(0, big);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = Math.round(big * 0.5f);
                lines[i].setLayoutParams(lp);
                lines[i].setVisibility(i < n ? VISIBLE : GONE);
            }
            col.setLayoutParams(at(-1, -2, Gravity.TOP | Gravity.LEFT, m, Ui.px(14 * s), m, 0));
            head.setLayoutParams(new LinearLayout.LayoutParams(-2, Ui.px(44 * s)));
            head.setGravity(Gravity.CENTER_VERTICAL);
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
            String age = News.age(now);
            String topic = News.topicName(p.str("newsTopic", "top"));
            head.setText((topic.equals("Top stories") ? Hindi.t("TOP NEWS") : Hindi.f("%1$s NEWS", Hindi.t(topic).toUpperCase(Locale.US)))
                    + (age.isEmpty() ? "" : "  ·  " + age + (Player.online(getContext()) ? "" : Hindi.t(", offline"))));
            for (int i = 0; i < lines.length; i++) {
                if (i < News.items.size()) {
                    News.Item it = News.items.get(i);
                    lines[i].setText((i + 1) + ".  " + it.title);
                    lines[i].setTextColor(Ui.SILVER);
                } else if (i == 0) {
                    lines[i].setText(!Features.on(p, "news") ? Hindi.t("The news box is switched off in Settings > Features.")
                            : Player.online(getContext()) ? Hindi.t("Getting the headlines…") : Hindi.t("Headlines show here when there's internet."));
                    lines[i].setTextColor(Ui.SILVER_DIM);
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
            addView(value);
            addView(label);
            value.setText("--");
        }

        void size(float s) {
            value.setTextSize(0, 30 * Ui.k * s);
            label.setTextSize(0, 10 * Ui.k * s);
        }
    }

    /** Three figures in a row under a header; tapping opens the box's menu. */
    abstract static class FiguresCard extends Card {
        final TextView head;
        final TextView foot = Ui.text(getContext(), 12, Ui.SILVER_DIM, "sans-serif", Gravity.CENTER);
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
            addView(head);
            addView(row);
            addView(foot);
            clickable(this, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    tapped();
                }
            });
        }

        abstract void tapped();

        @Override
        void fit(int w, int h) {
            float s = Math.min(h / (134f * Ui.k), w / (330f * Ui.k));
            int m = Ui.px(20 * s);
            head.setTextSize(0, 11 * Ui.k * s);
            head.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.LEFT, m, Ui.px(12 * s), 0, 0));
            for (Figure f : figs) f.size(s);
            row.setLayoutParams(at(-1, -2, Gravity.CENTER_VERTICAL, m / 2, Ui.px(4 * s), m / 2, 0));
            foot.setTextSize(0, 11 * Ui.k * s);
            foot.setLayoutParams(at(-1, -2, Gravity.BOTTOM, m, 0, m, Ui.px(10 * s)));
            // in a tall box the figures get room to grow
            if (h > w * 0.6f) for (Figure f : figs) f.value.setTextSize(0, 38 * Ui.k * s);
        }
    }

    static final class TripCard extends FiguresCard {
        TripCard(Context c) {
            super(c, "TODAY", "KM", "DRIVE TIME", "AVG KM/H");
        }

        @Override
        void tapped() {
            host.tripMenu();
        }

        @Override
        public void changed(Hub h) {
            figs[0].value.setText(Drive.todayKm < 100 ? Fuel.fmt(Drive.todayKm) : String.valueOf(Math.round(Drive.todayKm)));
            long min = Drive.todaySec / 60;
            figs[1].value.setText(min < 60 ? min + "m" : (min / 60) + "h " + (min % 60) + "m");
            double avg = Drive.avgKmh();
            figs[2].value.setText(avg <= 0 ? "--" : String.valueOf(Math.round(avg)));
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
            super(c, "FUEL", "LAST TANK", "AVERAGE", "₹ / KM");
        }

        @Override
        void tapped() {
            host.fuel();
        }

        @Override
        public void changed(Hub h) {
            Fuel.Stats st = Fuel.stats(host.prefs());
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

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            m.start();
            post(tick);
        }

        @Override
        protected void onDetachedFromWindow() {
            removeCallbacks(tick);
            m.stop();
            super.onDetachedFromWindow();
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
            double hd = Drive.headingNow >= 0 ? Drive.headingNow : Drive.headingKept;
            figs[0].value.setText(hd < 0 ? "--" : Hindi.t(point(hd)));
            figs[1].value.setText(hd < 0 ? "--" : Math.round(hd) % 360 + "°");
            double alt = Drive.altNow;
            figs[2].value.setText(Double.isNaN(alt) ? "--" : String.valueOf(Math.round(alt)));
            foot.setText(hd < 0 ? Hindi.t("The heading shows once the car moves")
                    : Drive.headingNow < 0 ? Hindi.t("Last heading while moving; GPS altitude is within about 10 m")
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
            head.setTextSize(0, 11 * Ui.k * s);
            head.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.LEFT, m, Ui.px(10 * s), 0, 0));
            for (TextView p : pads) {
                p.setTextSize(0, 13 * Ui.k * s);
                p.setSingleLine(true);
                p.setEllipsize(android.text.TextUtils.TruncateAt.END);
                ((LinearLayout.LayoutParams) p.getLayoutParams()).setMargins(g, g, g, g);
            }
            grid.setLayoutParams(at(-1, -1, Gravity.TOP, m - g, Ui.px(30 * s), m - g, Ui.px(24 * s)));
            note.setTextSize(0, 10 * Ui.k * s);
            note.setLayoutParams(at(-1, -2, Gravity.BOTTOM, m, 0, m, Ui.px(6 * s)));
        }

        @Override
        public void changed(Hub h) {
            boolean moving = Drive.kmhNow > Soundboard.PARKED_KMH;
            for (TextView p : pads) p.setAlpha(moving ? 0.4f : 1f);
            note.setText(moving ? Hindi.t("Plays when the car is stopped") : "");
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
                for (Figure f : figs) f.value.setText("--");
                foot.setText(Hindi.t("Engine data is off in Settings > Car"));
                return;
            }
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
            else foot.setText((Obd.load >= 0 ? Hindi.f("Load %1$s%%", Obd.load) : "") + (n > 0 ? "  ·  " + (n == 1 ? Hindi.t("1 fault code") : Hindi.f("%1$s fault codes", n)) : Obd.codesAt > 0 ? "  ·  " + Hindi.t("no fault codes") : ""));
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
                    }
                }
            }
        }

        @Override
        public void changed(Hub h) {
        }
    }
}
