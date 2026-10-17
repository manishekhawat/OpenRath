package org.openrath.launcher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Outline;
import android.location.Location;
import android.media.session.MediaController;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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
        else if (Prefs.MAP.equals(content)) card = new MapCard(c);
        else if (Prefs.TRIP.equals(content)) card = new TripCard(c);
        else if (Prefs.FUEL.equals(content)) card = new FuelCard(c);
        else if (Prefs.PHONE.equals(content)) card = new PhoneCard(c);
        else if (Prefs.OBD.equals(content)) card = new ObdCard(c);
        else if (Prefs.TYRES.equals(content)) card = new TyresCard(c);
        else if (Prefs.PERF.equals(content)) card = new PerfCard(c);
        else if (Prefs.NEWS.equals(content)) card = new NewsCard(c);
        else if (Prefs.CAB.equals(content)) card = new CabCard(c);
        else if (content.startsWith("widget")) card = new WidgetCard(c, content, host);
        else card = new MediaCard(c, Prefs.RADIO.equals(content));
        card.host = host;
        card.slot = slot;
        card.setOnLongClickListener(card.edit);
        return card;
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

    // ---------------------------------------------------------------- car and speed

    static final class CarCard extends Card {
        final ImageView brand = new ImageView(getContext()), car = new ImageView(getContext());
        final ChromeText carName = new ChromeText(getContext());
        final LinearLayout brandRow = new LinearLayout(getContext());
        final TextView speed = Ui.text(getContext(), 40, Ui.SILVER, "sans-serif-light", Gravity.RIGHT);
        final TextView unit = Ui.text(getContext(), 13, Ui.SILVER_DIM, "sans-serif-medium", Gravity.LEFT);
        final TextView lights = Ui.label(getContext(), "LIGHTS OFF");
        final ChromeText welcome = new ChromeText(getContext()), hello = new ChromeText(getContext());
        final Arrow left = new Arrow(getContext(), true), right = new Arrow(getContext(), false);
        final TextView limit = Ui.text(getContext(), 14, 0xFF111111, "sans-serif-medium", Gravity.CENTER);
        final TextView camera = Ui.label(getContext(), "");
        final Road road = new Road(getContext());
        Boolean lastOn;
        boolean leftOn, rightOn, lit;

        CarCard(Context c) {
            super(c);
            brand.setImageBitmap(Ui.img(c, "logo"));
            brand.setAdjustViewBounds(true);
            brandRow.setGravity(Gravity.CENTER_VERTICAL);
            car.setScaleType(ImageView.ScaleType.FIT_CENTER);
            speed.setText("--");
            unit.setText("km/h");
            addView(road, new LayoutParams(-1, -1));
            road.setVisibility(Ui.ROAD ? VISIBLE : GONE);
            addView(car);
            brandRow.addView(brand);
            brandRow.addView(carName);
            addView(brandRow);
            addView(speed);
            addView(unit);
            addView(lights);
            addView(hello);
            addView(welcome);
            hello.setText(Theme.text("welcomeTitle", "WELCOME"));
            addView(left);
            addView(right);
            android.graphics.drawable.GradientDrawable sign = new android.graphics.drawable.GradientDrawable();
            sign.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            sign.setColor(0xFFF2F2F2);
            sign.setStroke(Ui.px(4), 0xFFD42A2A);
            limit.setBackground(sign);
            limit.setVisibility(GONE);
            addView(limit);
            camera.setTextColor(0xFFFF5A4E);
            camera.setVisibility(GONE);
            addView(camera);
            clickable(lights, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Lights.deep = false;
                    host.setupLights();
                }
            });
        }

        @Override
        void fit(int w, int h) {
            float s = Math.min(h / (268f * Ui.k), w / (388f * Ui.k));
            int m = Ui.px(18 * s);
            brand.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, Ui.px(24 * s)));
            carName.setTextSize(0, 18 * Ui.k * s);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, -2);
            np.leftMargin = Ui.px(12 * s);
            carName.setLayoutParams(np);
            carName.setMaxWidth(w / 2);
            brandRow.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.LEFT, m, Ui.px(16 * s), 0, 0));
            speed.setTextSize(0, 44 * Ui.k * s);
            unit.setTextSize(0, 13 * Ui.k * s);
            unit.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.RIGHT, 0, Ui.px(30 * s), m, 0));
            speed.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.RIGHT, 0, Ui.px(10 * s), m + Ui.px(42 * s), 0));
            lights.setTextSize(0, 10 * Ui.k * s);
            lights.setLayoutParams(at(-2, -2, Gravity.BOTTOM | Gravity.LEFT, m, 0, 0, Ui.px(12 * s)));
            int side = Ui.px(34 * s);
            int bottom = Ui.px(34 * s), top = Ui.px(98 * s);
            car.setLayoutParams(at(-1, -1, Gravity.CENTER, m + side, top, m + side, bottom));
            // the road's horizon sits behind the car, its near edge just under the name
            int carTop = top, carBottom = h - bottom;
            road.horizon = carTop + (carBottom - carTop) * 0.42f;
            road.ground = h - Ui.px(4 * s);
            road.halfWidth = w * 0.62f;
            int as = Ui.px(26 * s), mid = (top - bottom) / 2;
            left.setLayoutParams(at(as, as, Gravity.CENTER_VERTICAL | Gravity.LEFT, m, mid, 0, 0));
            right.setLayoutParams(at(as, as, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, mid, m, 0));
            welcome.setTextSize(0, 18 * Ui.k * s);
            welcome.setMaxWidth(w - 2 * (m + Ui.px(40 * s)));
            welcome.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, Ui.px(68 * s), 0, 0));
            hello.setTextSize(0, 11 * Ui.k * s);
            hello.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, Ui.px(52 * s), 0, 0));
            int ls = Ui.px(34 * s);
            limit.setTextSize(0, 14 * Ui.k * s);
            ((android.graphics.drawable.GradientDrawable) limit.getBackground()).setStroke(Math.max(2, Ui.px(4 * s)), 0xFFD42A2A);
            limit.setLayoutParams(at(ls, ls, Gravity.TOP | Gravity.RIGHT, 0, Ui.px(60 * s), m, 0));
            camera.setTextSize(0, 10 * Ui.k * s);
            camera.setLayoutParams(at(-2, -2, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, m, Ui.px(12 * s)));
        }

        private final Runnable blink = new Runnable() {
            @Override
            public void run() {
                lit = !lit;
                left.setLit(leftOn && lit);
                right.setLit(rightOn && lit);
                if (leftOn || rightOn) postDelayed(this, 380);
            }
        };

        @Override
        public void changed(Hub h) {
            boolean on = h.lightsOn();
            if (lastOn == null || lastOn != on) {
                car.setImageBitmap(Ui.img(getContext(), on ? "car_on" : "car_off"));
                lastOn = on;
            }
            Prefs p = host.prefs();
            boolean learnt = p.lightSignal() != null || !"auto".equals(p.lights());
            lights.setText(!learnt ? "LIGHTS: TAP TO SET UP" : on ? "LIGHTS ON" : "LIGHTS OFF");
            lights.setTextColor(on ? Ui.SILVER : Ui.ACCENT);
            speed.setText(h.speed());
            String sp = h.speed();
            road.setSpeed(sp.equals("--") ? 0 : Integer.parseInt(sp));
            speed.setTextColor(Drive.over ? 0xFFFF5A4E : Ui.SILVER);
            limit.setText(String.valueOf(Drive.limit));
            limit.setVisibility(Drive.limit > 0 ? VISIBLE : GONE);
            String care = Features.on(p, "care") ? Care.alert(p) : "";
            // engine and tyre warnings come before service reminders
            if (Features.on(p, "tpms")) for (int w = 0; w < 4; w++) {
                String tw = Tpms.warning(p, w);
                if (!tw.isEmpty()) {
                    care = tw.toUpperCase(Locale.getDefault());
                    break;
                }
            }
            if (Features.on(p, "obd") && Obd.connected && Obd.coolant >= p.num("obdHot", 105))
                care = "ENGINE HOT  " + Obd.coolant + "°C";
            road.setVisibility(Ui.ROAD && Features.on(p, "road") ? VISIBLE : GONE);
            if (Drive.cameraM >= 0) {
                camera.setText("SPEED CAMERA  " + (Drive.cameraM / 10 * 10) + " m");
                camera.setTextColor(0xFFFF5A4E);
            } else {
                camera.setText(care);
                camera.setTextColor(0xFFF5C542);
            }
            camera.setVisibility(Drive.cameraM >= 0 || !care.isEmpty() ? VISIBLE : GONE);
            carName.setText(Car.name(p).toUpperCase(Locale.getDefault()));
            String name = p.welcome().trim();
            welcome.setText(name.toUpperCase(Locale.getDefault()));
            hello.setVisibility(name.isEmpty() ? GONE : VISIBLE);
            boolean arrows = Features.on(p, "arrows") && (p.signal("left") != null || p.signal("right") != null);
            left.setVisibility(arrows ? VISIBLE : GONE);
            right.setVisibility(arrows ? VISIBLE : GONE);
            boolean was = leftOn || rightOn;
            leftOn = h.leftOn;
            rightOn = h.rightOn;
            if ((leftOn || rightOn) && !was) {
                lit = false;
                removeCallbacks(blink);
                post(blink);
            } else if (!leftOn && !rightOn) {
                left.setLit(false);
                right.setLit(false);
            }
        }
    }

    /**
     * The road under the car, drawn in perspective. Its lane markings slide away toward the
     * horizon at a rate that follows the GPS speed, so the car looks like it's driving; standing
     * still, the road stops too.
     */
    static final class Road extends View {
        float horizon, ground, halfWidth;
        private float kmh, travelled;   // metres along the road, for the markings
        private long last;
        private final android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint line = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path path = new android.graphics.Path();
        private static final float FAR = 40f, DASH = 0.45f, GAP = 0.4f;   // in road units: short, so several show below the car

        Road(Context c) {
            super(c);
        }

        void setSpeed(float v) {
            boolean was = kmh > 0.5f;
            kmh = v;
            if (!was && kmh > 0.5f) {
                last = 0;
                invalidate();
            }
        }

        /** Screen y for a point z metres ahead of the near edge (z = 1 is the near edge). */
        private float y(float z) {
            return horizon + (ground - horizon) / z;
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            if (ground <= horizon) return;
            long now = android.os.SystemClock.uptimeMillis();
            if (kmh > 0.5f && last != 0) {
                // a little slower than real life reads better at this size
                travelled += kmh / 3.6f * 0.16f * Math.min(200, now - last) / 1000f;
                travelled %= DASH + GAP;
            }
            last = now;
            float cx = getWidth() / 2f;
            // asphalt, darkening into the distance
            float topY = y(FAR);
            path.reset();
            path.moveTo(cx - halfWidth / FAR, topY);
            path.lineTo(cx + halfWidth / FAR, topY);
            path.lineTo(cx + halfWidth, ground);
            path.lineTo(cx - halfWidth, ground);
            path.close();
            fill.setShader(new android.graphics.LinearGradient(0, topY, 0, ground,
                    new int[]{Ui.ROAD_FILL & 0x00FFFFFF, Ui.mix(Ui.ROAD_FILL, Ui.BACKGROUND, 0.8f), Ui.ROAD_FILL}, new float[]{0f, 0.35f, 1f},
                    android.graphics.Shader.TileMode.CLAMP));
            c.drawPath(path, fill);
            // solid edge lines
            line.setStyle(android.graphics.Paint.Style.FILL);
            edge(c, cx, -1);
            edge(c, cx, 1);
            // centre dashes, moving away from the viewer
            float period = DASH + GAP;
            for (float z0 = 1 - period + travelled; z0 < FAR; z0 += period) {
                float a = Math.max(z0, 1), b = Math.min(z0 + DASH, FAR);
                if (b <= a) continue;
                quad(c, cx, a, b, 0.028f, 0f);
            }
            if (kmh > 0.5f) postInvalidateDelayed(33);
        }

        private void edge(android.graphics.Canvas c, float cx, int side) {
            for (float z = 1; z < FAR; z *= 1.6f) quad(c, cx, z, Math.min(z * 1.6f, FAR), 0.01f, side * 0.86f);
        }

        /** A strip of paint from z a to z b, w wide (as a share of the road's half width), centred at x. */
        private void quad(android.graphics.Canvas c, float cx, float a, float b, float w, float x) {
            float ya = y(a), yb = y(b);
            float ha = halfWidth / a, hb = halfWidth / b;
            path.reset();
            path.moveTo(cx + (x - w) * ha, ya);
            path.lineTo(cx + (x + w) * ha, ya);
            path.lineTo(cx + (x + w) * hb, yb);
            path.lineTo(cx + (x - w) * hb, yb);
            path.close();
            // nearer paint is brighter; far away it fades into the dark
            int alpha = Math.round(200 * Math.max(0, 1 - (b - 1) / (FAR - 1)));
            line.setColor((alpha << 24) | (Ui.ROAD_LINE & 0x00FFFFFF));
            c.drawPath(path, line);
        }
    }

    /** Text in the DZIRE wordmark's font with a silver chrome gradient. */
    static final class ChromeText extends TextView {
        ChromeText(Context c) {
            super(c);
            try {
                android.graphics.Typeface tf = Theme.font();
                setTypeface(tf != null ? tf : android.graphics.Typeface.createFromAsset(c.getAssets(), "fonts/InterDisplay-Light.otf"));
            } catch (Exception ignored) {
            }
            setLetterSpacing(0.42f);
            setSingleLine(true);
            setEllipsize(android.text.TextUtils.TruncateAt.END);
            setIncludeFontPadding(false);
            setTextColor(Ui.SILVER);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            if (!Ui.CHROME) {
                getPaint().setShader(null);
                return;
            }
            getPaint().setShader(new android.graphics.LinearGradient(0, 0, 0, b - t,
                    new int[]{0xFFF6F8FA, 0xFFB9BEC5, 0xFF7E848C, 0xFFE4E7EB, 0xFFA3A8AF},
                    new float[]{0f, 0.38f, 0.5f, 0.62f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        }
    }

    /** A turn-indicator arrow: dark when off, amber when lit. */
    static final class Arrow extends View {
        final boolean leftSide;
        boolean lit;
        final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final android.graphics.Path path = new android.graphics.Path();

        Arrow(Context c, boolean left) {
            super(c);
            leftSide = left;
            setLayerType(LAYER_TYPE_SOFTWARE, null);   // for the glow
        }

        void setLit(boolean on) {
            if (on != lit) {
                lit = on;
                invalidate();
            }
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            float w = getWidth(), h = getHeight(), g = w * 0.12f;
            path.reset();
            float tip = leftSide ? g : w - g, back = leftSide ? w - g : g, mid = (tip + back) / 2;
            path.moveTo(tip, h / 2);
            path.lineTo(mid, g);
            path.lineTo(mid, h * 0.34f);
            path.lineTo(back, h * 0.34f);
            path.lineTo(back, h * 0.66f);
            path.lineTo(mid, h * 0.66f);
            path.lineTo(mid, h - g);
            path.close();
            paint.setColor(lit ? 0xFFFFB020 : Ui.mix(Ui.SILVER_DIM, Ui.PANEL, 0.3f));
            if (lit) paint.setShadowLayer(g * 1.2f, 0, 0, 0xFFFFB020);
            else paint.clearShadowLayer();
            c.drawPath(path, paint);
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
            desc.setText(h.weather);
            int aq = Features.on(host.prefs(), "aqi") ? Air.aqi : -1;
            place.setText(aq < 0 ? h.place : h.place + (h.place.isEmpty() ? "" : "  ·  ") + "AQI " + aq);
            place.setTextColor(aq > 200 ? 0xFFFFB02E : Ui.SILVER_DIM);
            // rain on the planned route wins over rain here; both come and go
            String r = !Features.on(host.prefs(), "rain") ? "" : !Hub.routeRain.isEmpty() ? Hub.routeRain : h.rainSoon;
            // offline: say the reading comes from the kept forecast
            if (h.weatherSaved > 0) r = r.isEmpty() ? Weather.savedNote(h.weatherSaved, System.currentTimeMillis())
                    : r + "  ·  offline";
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
            read.setText("▶  Read aloud");
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
            read.setText(on ? "■  Stop" : "▶  Read aloud");
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
            head.setText((topic.equals("Top stories") ? "TOP NEWS" : topic.toUpperCase(Locale.US) + " NEWS")
                    + (age.isEmpty() ? "" : "  ·  " + age + (Player.online(getContext()) ? "" : ", offline")));
            for (int i = 0; i < lines.length; i++) {
                if (i < News.items.size()) {
                    News.Item it = News.items.get(i);
                    lines[i].setText((i + 1) + ".  " + it.title);
                    lines[i].setTextColor(Ui.SILVER);
                } else if (i == 0) {
                    lines[i].setText(!Features.on(p, "news") ? "The news box is switched off in Settings > Features."
                            : Player.online(getContext()) ? "Getting the headlines…" : "Headlines show here when there's internet.");
                    lines[i].setTextColor(Ui.SILVER_DIM);
                } else lines[i].setText("");
            }
        }
    }

    // ---------------------------------------------------------------- map

    static final class MapCard extends Card {
        static final String MAP_BASE = "https://openrath.local/";
        final WebView web;
        double lastLat, lastLon;
        long lastPark = -1;
        boolean ready;

        @SuppressLint("SetJavaScriptEnabled")
        MapCard(Context c) {
            super(c);
            web = new WebView(c);
            WebSettings ws = web.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setAllowFileAccess(true);
            web.setBackgroundColor(0xFF0E0F12);
            ws.setDomStorageEnabled(true);
            final android.content.res.AssetManager assets = c.getAssets();
            web.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView v, String url) {
                    ready = true;
                    lastLat = lastLon = 0;
                    lastPark = -1;
                }

                // The page is served from a made-up https address so the map tile servers see a
                // normal web page asking for tiles (some refuse requests from local files).
                @Override
                public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v, String url) {
                    // map tiles: kept on the unit so places already seen show without internet
                    if (Tiles.isTile(url) && host != null && Features.on(host.prefs(), "offlineMap")) return Tiles.serve(v.getContext(), url);
                    if (!url.startsWith(MAP_BASE)) return null;
                    String name = url.substring(MAP_BASE.length());
                    int q = name.indexOf('?');
                    if (q >= 0) name = name.substring(0, q);
                    String type = name.endsWith(".js") ? "application/javascript"
                            : name.endsWith(".css") ? "text/css" : name.endsWith(".png") ? "image/png" : "text/html";
                    try {
                        return new android.webkit.WebResourceResponse(type, "UTF-8", assets.open("map/" + name));
                    } catch (java.io.IOException e) {
                        return null;
                    }
                }
            });
            web.loadUrl(MAP_BASE + "map.html");
            final float r = Ui.RADIUS * Ui.k;
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
                }
            });
            setClipToOutline(true);
            web.addJavascriptInterface(new Bridge(), "OpenRath");
            web.setOnLongClickListener(edit);
            addView(web, new LayoutParams(-1, -1));
            View ring = new View(c);
            android.graphics.drawable.GradientDrawable g = Ui.panel();
            g.setColors(new int[]{0, 0});
            ring.setBackground(g);
            ring.setClickable(false);
            addView(ring, new LayoutParams(-1, -1));
        }

        /** What the map page can ask of the app. */
        final class Bridge {
            /** Turn-by-turn in Google Maps (when navigation is set to Google Maps). */
            @android.webkit.JavascriptInterface
            public void navigate(final double lat, final double lon, final String name) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        host.navigate(lat, lon, name);
                    }
                });
            }

            @android.webkit.JavascriptInterface
            public boolean online() {
                return Player.online(getContext());
            }

            /** The offline map app Go hands over to without internet, or "" when there is none. */
            @android.webkit.JavascriptInterface
            public String offlineNavName() {
                String pkg = OfflineNav.app(getContext(), host.prefs());
                return pkg == null ? "" : OfflineNav.label(getContext(), pkg);
            }

            @android.webkit.JavascriptInterface
            public void navigateOffline(final double lat, final double lon, final String name) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        host.navigateOffline(lat, lon, name);
                    }
                });
            }

            /** "own" to guide in this map, "google" to hand over to Google Maps. */
            @android.webkit.JavascriptInterface
            public String navMode() {
                return host.prefs().source("navi");
            }

            @android.webkit.JavascriptInterface
            public void setNavMode(String v) {
                host.prefs().setSource("navi", "google".equals(v) ? "google" : "own");
            }

            @android.webkit.JavascriptInterface
            public void speak(final String text) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        host.speak(text);
                    }
                });
            }

            /** Rain expected on the planned route ("" = none), shown in the weather box. */
            @android.webkit.JavascriptInterface
            public void routeRain(final String text) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        Hub.routeRain = text == null ? "" : text;
                    }
                });
            }

            /** Rupees per toll plaza, for the route's toll estimate. */
            @android.webkit.JavascriptInterface
            public int tollRate() {
                return Features.on(host.prefs(), "tolls") ? host.prefs().num("tollRate", 100) : 0;
            }

            /** Save where the car is now as the parking spot. */
            @android.webkit.JavascriptInterface
            public void parkHere(final double lat, final double lon) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        Drive.setPark(getContext(), lat, lon);
                    }
                });
            }

            /** Call a place's phone number through the unit's Bluetooth phone. */
            @android.webkit.JavascriptInterface
            public void call(final String number) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        host.call(number);
                    }
                });
            }

            /** "Mine" on a service centre: kept for the service booking reminder. */
            @android.webkit.JavascriptInterface
            public void saveService(String name, String phone) {
                host.prefs().put("myService", (name == null ? "" : name.replace("|", " ")) + "|" + (phone == null ? "" : phone));
            }

            /** Where the map is taking you, for the kids' distance countdown. */
            @android.webkit.JavascriptInterface
            public void destination(String name, double lat, double lon) {
                if (!host.prefs().str("dest", "").startsWith((name == null ? "" : name.replace("|", " ")) + "|"))
                    host.prefs().put("destNick", "");   // a new place: the children's name for the old one no longer fits
                host.prefs().put("dest", (name == null ? "" : name.replace("|", " ")) + "|" + String.format(Locale.US, "%.6f|%.6f", lat, lon));
            }

            /** The map's saved Home and Office, so drives can be named for the commute insights. */
            @android.webkit.JavascriptInterface
            public void savedPlace(String name, double lat, double lon) {
                if ("Home".equals(name) || "Office".equals(name))
                    host.prefs().put("place:" + name, String.format(Locale.US, "%.6f,%.6f", lat, lon));
            }

            /** Whether the parking panel shows its QR code. */
            @android.webkit.JavascriptInterface
            public boolean parkLink() {
                return Features.on(host.prefs(), "parkLink");
            }

            /** Tap on the parking panel's code: show it full screen and bright, which phones read far better. */
            @android.webkit.JavascriptInterface
            public void bigQr() {
                post(new Runnable() {
                    @Override
                    public void run() {
                        host.parkQr();
                    }
                });
            }

            @android.webkit.JavascriptInterface
            public String carBrand() {
                return Car.brandName(host.prefs());
            }

            /** Words to find the brand's workshops by in OpenStreetMap names, as a regular expression. */
            @android.webkit.JavascriptInterface
            public String brandWords() {
                return Car.searchWords(Car.brand(host.prefs()), Car.brandName(host.prefs()));
            }

            @android.webkit.JavascriptInterface
            public boolean serviceFinder() {
                return Features.on(host.prefs(), "serviceFinder");
            }

            @android.webkit.JavascriptInterface
            public void voice() {
                post(new Runnable() {
                    @Override
                    public void run() {
                        host.voiceSearch(MapCard.this);
                    }
                });
            }
        }

        /** Run a function of the map page, e.g. "goFav('Home')". */
        void js(String code) {
            web.evaluateJavascript(code, null);
        }

        /** What voice search heard (null: nothing, or no speech recogniser on the unit). */
        void voiceResult(String heard) {
            String js = heard == null ? "voiceResult(null)" : "voiceResult(" + org.json.JSONObject.quote(heard) + ")";
            web.evaluateJavascript(js, null);
        }

        @Override
        void fit(int w, int h) {
        }

        @Override
        public void changed(Hub h) {
            if (ready && Drive.parkAt != lastPark) {
                lastPark = Drive.parkAt;
                if (Drive.parkAt > 0) web.evaluateJavascript(String.format(Locale.US, "setPark(%.6f,%.6f,%d)",
                        Drive.parkLat, Drive.parkLon, Drive.parkAt), null);
            }
            Location f = h.fix;
            if (!ready || f == null) return;
            if (Math.abs(f.getLatitude() - lastLat) < 1e-5 && Math.abs(f.getLongitude() - lastLon) < 1e-5) return;
            lastLat = f.getLatitude();
            lastLon = f.getLongitude();
            web.evaluateJavascript(String.format(Locale.US, "setPos(%.6f,%.6f,%.0f,%.1f)",
                    lastLat, lastLon, f.hasBearing() && f.getSpeed() > 1 ? f.getBearing() : -1f,
                    f.hasSpeed() ? f.getSpeed() : 0f), null);
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
            label = Ui.label(c, name);
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
            head = Ui.label(c, title);
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
            String park = Drive.parkAt == 0 ? "" : "  ·  parked " + ago(Drive.parkAt);
            double rs = Features.on(host.prefs(), "fuelPrice") ? Fuel.cost(host.prefs(), Drive.todayKm) : 0;
            foot.setText("This drive " + Fuel.fmt(Drive.driveKm) + " km" + (rs >= 1 ? "  ·  ₹" + Math.round(rs) + " petrol today" : "")
                    + (Features.on(host.prefs(), "score") ? "  ·  score " + Drive.score() : "") + park);
        }

        static String ago(long t) {
            long m = (System.currentTimeMillis() - t) / 60000;
            if (m < 1) return "just now";
            if (m < 60) return m + " min ago";
            if (m < 48 * 60) return (m / 60) + " h ago";
            return (m / 1440) + " days ago";
        }
    }

    static String tollsThisMonth(Prefs p) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.DAY_OF_MONTH, 1);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        double rs = Care.spent(Care.tolls(p), c.getTimeInMillis(), Long.MAX_VALUE);
        return rs > 0 ? "  ·  tolls ₹" + Math.round(rs) + " this month" : "";
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
            figs[0].label.setText("LAST TANK KM/L");
            figs[1].label.setText("AVERAGE KM/L");
            if (st.fills == 0) foot.setText("Tap to add your first fill-up");
            else if (st.lastKmpl <= 0) foot.setText("Mileage shows after two full-tank fill-ups");
            else foot.setText(st.fills + " fill-ups  ·  last " + TripCard.ago(st.lastAt) + tollsThisMonth(host.prefs()));
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
                foot.setText("Turn on Cab driver tools in Settings > Cab driver");
                return;
            }
            long now = System.currentTimeMillis();
            Cab.Sum s = Cab.sum(Cab.log(p), Cab.dayStart(now), now + 1);
            figs[0].value.setText(Cab.rs(s.in));
            figs[1].value.setText(String.valueOf(s.rides));
            figs[2].value.setText(Cab.rs(s.net()));
            String f;
            if (p.longNum("cabMeterAt", 0) > 0) {
                f = "Meter " + Cab.rs(Cab.fareNow(p)) + "  ·  " + Fuel.fmt(Cab.meterKm(p)) + " km  ·  " + Cab.meterWaitMs(p) / 60000 + " min waiting";
            } else {
                int target = p.num("cabTargetRs", 2000);
                long duty = p.longNum("cabDutyAt", 0);
                f = Cab.on(p, "cabTarget") && target > 0 ? (s.net() >= target ? "Target " + Cab.rs(target) + " reached" : Cab.rs(target - s.net()) + " to the target")
                        + (duty >= Cab.dayStart(now) ? "  ·  on duty " + Cab.hm(now - duty) : "") : "Tap for the meter, rides and SOS";
            }
            foot.setText((Cab.quiet ? "Quiet ride  ·  " : "") + f);
        }
    }

    // ---------------------------------------------------------------- phone

    /**
     * Bluetooth phone through Topway's service: who is calling (from its Bluetooth callback),
     * answer and hang up, and up to three favourite numbers to call with one tap.
     */
    static final class PhoneCard extends Card {
        final TextView head = Ui.label(getContext(), "PHONE");
        final TextView title = Ui.text(getContext(), 24, Ui.SILVER, "sans-serif-light", Gravity.LEFT);
        final TextView sub = Ui.text(getContext(), 13, Ui.SILVER_DIM, "sans-serif", Gravity.LEFT);
        final LinearLayout row = new LinearLayout(getContext());
        final TextView answer, end;
        final TextView[] favs = new TextView[3];
        /** The last calls, while a phone is connected; tap one to call back. */
        final LinearLayout calls = new LinearLayout(getContext());
        final TextView[] callRows = new TextView[5];
        java.util.List<String[]> recentCalls = new java.util.ArrayList<>();
        int callRoom = 5;
        String callsKey = "";
        long callsAt;
        Unit unit;

        PhoneCard(Context c) {
            super(c);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            answer = pill(c, "Answer", 0xFF1F6F45);
            end = pill(c, "End", 0xFF7A2328);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(answer);
            row.addView(end);
            for (int i = 0; i < 3; i++) {
                favs[i] = pill(c, "", 0);
                row.addView(favs[i]);
                final int n = i;
                clickable(favs[i], new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String[] f = fav(host.prefs(), n);
                        if (f == null) return;
                        String err = unit == null ? "the stereo isn't ready" : unit.call(getContext(), f[1]);
                        sub.setText(err == null ? "Calling " + f[0] + "…" : "Couldn't call: " + err);
                    }
                });
            }
            addView(head);
            addView(title);
            addView(sub);
            addView(row);
            calls.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < callRows.length; i++) {
                callRows[i] = Ui.text(c, 13, Ui.SILVER, "sans-serif", Gravity.LEFT);
                callRows[i].setSingleLine(true);
                callRows[i].setEllipsize(android.text.TextUtils.TruncateAt.END);
                final int n = i;
                clickable(callRows[i], new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (n >= recentCalls.size()) return;
                        String[] f = recentCalls.get(n);
                        String err = unit == null ? "the stereo isn't ready" : unit.call(getContext(), f[0]);
                        android.widget.Toast.makeText(getContext(), err == null ? "Calling " + (f[1].isEmpty() ? f[0] : f[1]) + "…"
                                : "Couldn't call: " + err, android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
                calls.addView(callRows[i]);
            }
            calls.setVisibility(GONE);
            addView(calls);
            clickable(answer, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (unit != null) unit.answer(getContext());
                }
            });
            clickable(end, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (unit != null) unit.hangUp(getContext());
                }
            });
            OnClickListener back = new OnClickListener() {
                @Override
                public void onClick(View v) {
                    // tap the last caller to ring them back; with no call yet, open the phone app
                    if (last == null || unit == null || host.prefs() == null) {
                        host.openApp("dock:phone", "Phone");
                        return;
                    }
                    String err = unit.call(getContext(), last[0]);
                    sub.setText(err == null ? "Calling " + (last[1].isEmpty() ? last[0] : last[1]) + "…" : "Couldn't call: " + err);
                }
            };
            clickable(title, back);
            clickable(sub, back);
        }

        String[] last;

        static String ago(long at) {
            long m = (System.currentTimeMillis() - at) / 60000;
            if (m < 1) return "just now";
            if (m < 60) return m + " min ago";
            if (m < 24 * 60) return (m / 60) + " h ago";
            return new java.text.SimpleDateFormat("d MMM", java.util.Locale.US).format(new java.util.Date(at));
        }

        static TextView pill(Context c, String text, int fill) {
            TextView t = Ui.text(c, 14, Ui.SILVER, "sans-serif-medium", Gravity.CENTER);
            t.setText(text);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setCornerRadius(10 * Ui.k);
            g.setColor(fill != 0 ? fill : Ui.PANEL_TOP);
            g.setStroke(Math.max(1, Ui.px(1.2f)), fill != 0 ? fill : Ui.EDGE);
            t.setBackground(g);
            return t;
        }

        /** Favourite i as {name, number}, or null. Stored as "name|number". */
        static String[] fav(Prefs p, int i) {
            String v = p.str("fav" + (i + 1), "");
            int bar = v.indexOf('|');
            if (bar <= 0 || bar == v.length() - 1) return null;
            return new String[]{v.substring(0, bar), v.substring(bar + 1)};
        }

        @Override
        void fit(int w, int h) {
            float s = Math.min(h / (134f * Ui.k), w / (330f * Ui.k));
            int m = Ui.px(18 * s);
            head.setTextSize(0, 11 * Ui.k * s);
            head.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.LEFT, m, Ui.px(12 * s), 0, 0));
            title.setTextSize(0, 24 * Ui.k * s);
            title.setMaxWidth(w - 2 * m);
            title.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.LEFT, m, Ui.px(30 * s), m, 0));
            sub.setTextSize(0, 12 * Ui.k * s);
            sub.setMaxWidth(w - 2 * m);
            sub.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.LEFT, m, Ui.px(60 * s), m, 0));
            int bh = Ui.px(34 * s);
            for (int i = 0; i < row.getChildCount(); i++) {
                TextView t = (TextView) row.getChildAt(i);
                t.setTextSize(0, 13 * Ui.k * s);
                t.setPadding(Ui.px(12 * s), 0, Ui.px(12 * s), 0);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, bh);
                lp.rightMargin = Ui.px(8 * s);
                t.setLayoutParams(lp);
            }
            row.setLayoutParams(at(-1, -2, Gravity.BOTTOM | Gravity.LEFT, m, 0, m, Ui.px(12 * s)));
            // the call list: from under the heading down to the favourites, as many rows as fit (up to 5)
            int top = Ui.px(32 * s), rowH = Ui.px(19 * s);
            int bottomRoom = Ui.px(12 * s) + bh + Ui.px(6 * s);
            callRoom = Math.max(1, Math.min(callRows.length, (h - top - bottomRoom) / rowH));
            for (TextView t : callRows) {
                t.setTextSize(0, 13 * Ui.k * s);
                t.setLayoutParams(new LinearLayout.LayoutParams(-1, rowH));
                t.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            }
            calls.setLayoutParams(at(-1, -2, Gravity.TOP | Gravity.LEFT, m, top, m, 0));
        }

        @Override
        public void changed(Hub h) {
            unit = h.unit;
            Prefs p = host.prefs();
            for (int i = 0; i < 3; i++) {
                String[] f = fav(p, i);
                favs[i].setText(f == null ? "" : f[0]);
                favs[i].setVisibility(f == null ? GONE : VISIBLE);
            }
            String down = unit.phoneDown();
            if (down != null) {
                head.setText("PHONE");
                title.setText("Phone");
                sub.setText(down);
                return;
            }
            String said = unit.caller();
            head.setText(h.btConnected ? "PHONE  ·  CONNECTED" : "PHONE  ·  NOT CONNECTED");
            boolean call = h.phoneStatus != 0;
            last = h.lastCall;
            if (call) {
                String who = last != null && System.currentTimeMillis() - Long.parseLong(last[3]) < 120000
                        ? (last[1].isEmpty() ? last[0] : last[1]) : !said.isEmpty() ? said : "Call";
                title.setText(who);
                sub.setText("Call in progress or ringing");
            } else if (last != null) {
                title.setText((last[1].isEmpty() ? last[0] : last[1]));
                sub.setText(("out".equals(last[2]) ? "Called " : "in".equals(last[2]) ? "Called you " : "Last call ")
                        + ago(Long.parseLong(last[3])) + (h.btConnected ? "  ·  tap to call back" : ""));
            } else {
                title.setText(h.btConnected ? "Phone connected" : "No phone connected");
                sub.setText(h.btConnected ? "Your last call shows here" : "Pair your phone in the Bluetooth app");
            }
            answer.setVisibility(call ? VISIBLE : GONE);
            end.setVisibility(call ? VISIBLE : GONE);
            // connected and no call on: the last five calls instead of just the last one
            String key = p.str("callHistory", "").hashCode() + "/" + h.btConnected + call;
            if (!key.equals(callsKey) || System.currentTimeMillis() - callsAt > 15000) {
                callsKey = key;
                callsAt = System.currentTimeMillis();
                recentCalls = h.btConnected && !call ? Calls.recent(getContext(), p, callRows.length) : new java.util.ArrayList<String[]>();
            }
            boolean list = !recentCalls.isEmpty();
            calls.setVisibility(list ? VISIBLE : GONE);
            title.setVisibility(list ? GONE : VISIBLE);
            sub.setVisibility(list ? GONE : VISIBLE);
            for (int i = 0; i < callRows.length; i++) {
                boolean show = list && i < recentCalls.size() && i < callRoom;
                callRows[i].setVisibility(show ? VISIBLE : GONE);
                if (!show) continue;
                String[] f = recentCalls.get(i);
                String arrow = "out".equals(f[2]) ? "↗  " : "missed".equals(f[2]) ? "✕  " : "↙  ";
                callRows[i].setText(arrow + (f[1].isEmpty() ? f[0] : f[1]) + "   ·   " + ago(Long.parseLong(f[3])));
                callRows[i].setTextColor("missed".equals(f[2]) ? 0xFFE0796F : Ui.SILVER);
            }
        }
    }

    // ---------------------------------------------------------------- music / radio

    static final class MediaCard extends Card {
        final boolean radio;
        final TextView head = Ui.label(getContext(), "");
        final TextView title = Ui.text(getContext(), 22, Ui.SILVER, "sans-serif-light", Gravity.LEFT);
        final TextView sub = Ui.text(getContext(), 13, Ui.SILVER_DIM, "sans-serif", Gravity.LEFT);
        final ImageView prev = new ImageView(getContext()), play = new ImageView(getContext()), next = new ImageView(getContext());
        final LinearLayout buttons = new LinearLayout(getContext()), text = new LinearLayout(getContext());
        final TextView scan = Ui.text(getContext(), 12, Ui.SILVER, "sans-serif-medium", Gravity.CENTER);
        Hub.Media media;      // the other app's player, when it shows one
        Unit unit;            // the stereo: FM through it when it can (a maker's add-on), else the radio app
        boolean unitFm;       // the buttons drive FM through the unit
        boolean localOn;      // our own guess for app players we can't see
        /** Which box last started something; the other box shows paused. */
        static String owner;

        String kind() {
            return radio ? "radio" : "music";
        }

        /** This box is starting to play: the other box's sound stops. */
        void claim() {
            owner = kind();
            if (!radio && unit != null) unit.fmQuiet();
        }
        String fmNote = "";   // why FM couldn't start from the box, if it couldn't
        String source;        // what the buttons drive now: "web", "files" or "app"
        boolean audioOn;

        MediaCard(Context c, boolean isRadio) {
            super(c);
            radio = isRadio;
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text.setOrientation(LinearLayout.VERTICAL);
            text.addView(title);
            text.addView(sub);
            prev.setImageBitmap(Ui.img(c, "btn_prev"));
            play.setImageBitmap(Ui.img(c, "btn_play"));
            next.setImageBitmap(Ui.img(c, "btn_next"));
            buttons.setGravity(Gravity.CENTER_VERTICAL);
            buttons.addView(prev);
            buttons.addView(play);
            buttons.addView(next);
            addView(head);
            addView(text);
            addView(buttons);
            if (radio) {
                // FM station scan: steps the stock radio round the band and lists what it found
                android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
                pill.setCornerRadius(999);
                pill.setStroke(Math.max(1, Ui.px(1.5f)), Ui.EDGE);
                scan.setBackground(pill);
                scan.setText("SCAN");
                scan.setVisibility(GONE);
                addView(scan);
                clickable(scan, new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!(getContext() instanceof android.app.Activity)) return;
                        unit.scan((android.app.Activity) getContext(), host.prefs(), new Runnable() {
                            @Override
                            public void run() {
                                host.refresh();
                            }
                        });
                    }
                });
            }
            final String key = radio ? "media:radio" : "media:music";
            final String name = radio ? "Radio" : "Music";
            // the header is the source switch: auto, online / app / files
            clickable(head, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs p = host.prefs();
                    String what = radio ? "radio" : "music";
                    String[] all = radio ? Prefs.RADIO_SRC : Prefs.MUSIC_SRC;
                    String cur = p.source(what);
                    int i = 0;
                    while (i < all.length && !all[i].equals(cur)) i++;
                    p.setSource(what, all[(i + 1) % all.length]);
                    if (Player.playing && isMine()) Player.send(getContext(), Player.STOP, null);
                    host.refresh();
                }
            });
            clickable(text, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if ("app".equals(source)) host.openApp(key, name);
                    else Player.send(getContext(), Player.TOGGLE, source);
                }
            });
            clickable(prev, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!"app".equals(source)) Player.send(getContext(), Player.PREV, source);
                    else if (radio && unitFm && unit.fmSeek(host.prefs(), false)) host.refresh();
                    else if (media != null) media.controller.getTransportControls().skipToPrevious();
                    else key(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                }
            });
            clickable(next, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!"app".equals(source)) Player.send(getContext(), Player.NEXT, source);
                    else if (radio && unitFm && unit.fmSeek(host.prefs(), true)) host.refresh();
                    else if (media != null) media.controller.getTransportControls().skipToNext();
                    else key(android.view.KeyEvent.KEYCODE_MEDIA_NEXT);
                }
            });
            clickable(play, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!"app".equals(source)) {
                        if (!audioOn) claim();
                        Resume.remember(host.prefs(), audioOn ? "" : source);
                        Player.send(getContext(), Player.TOGGLE, source);
                        return;
                    }
                    if (!audioOn) claim();
                    if (Player.playing) Player.send(getContext(), Player.STOP, null);
                    if (radio && unitFm) {
                        // FM through the stereo's own services, the way the stock launcher's radio widget does
                        final boolean start = !audioOn;
                        Prefs pr = host.prefs();
                        String err = unit.fmPlay(getContext(), pr, start);
                        Resume.remember(pr, start ? "fm" : "");
                        boolean own = unit.fmFirst(pr);
                        if (start && !own) host.startRadioService();
                        fmNote = start && err != null ? "FM didn't start: " + err
                                : "";
                        localOn = start;
                        audioOn = start;
                        postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (unit != null) unit.fmPoll();
                            }
                        }, 800);
                        showPlay();
                        return;
                    }
                    Resume.remember(host.prefs(), audioOn ? "" : "app:" + (media != null ? media.controller.getPackageName() : ""));
                    if (media != null) {
                        MediaController.TransportControls t = media.controller.getTransportControls();
                        if (media.playing) t.pause();
                        else t.play();
                    } else {
                        key(audioOn ? android.view.KeyEvent.KEYCODE_MEDIA_PAUSE : android.view.KeyEvent.KEYCODE_MEDIA_PLAY);
                    }
                    localOn = !audioOn;
                    audioOn = localOn;
                    showPlay();
                }
            });
        }

        /** Our own player is playing this box's kind of thing. */
        boolean isMine() {
            return radio ? Player.WEB.equals(Player.mode) : Player.FILES.equals(Player.mode);
        }

        /** Media keys, as a steering-wheel button sends, for app players we can't see. */
        void key(int code) {
            android.media.AudioManager am = (android.media.AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            long t = android.os.SystemClock.uptimeMillis();
            am.dispatchMediaKeyEvent(new android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_DOWN, code, 0));
            am.dispatchMediaKeyEvent(new android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_UP, code, 0));
        }

        @Override
        void fit(int w, int h) {
            float s = Math.min(h / (138f * Ui.k), w / (420f * Ui.k));
            int m = Ui.px(22 * s);
            int bs = Ui.px(52 * s);
            head.setTextSize(0, 11 * Ui.k * s);
            head.setPadding(0, Ui.px(6 * s), Ui.px(12 * s), Ui.px(6 * s));
            head.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.LEFT, m, Ui.px(10 * s), 0, 0));
            title.setTextSize(0, (radio ? 30 : 24) * Ui.k * s);
            sub.setTextSize(0, 13 * Ui.k * s);
            int bw = 3 * bs + 2 * Ui.px(14 * s);
            text.setLayoutParams(at(Math.max(w - 3 * m - bw, Ui.px(60)), -2, Gravity.CENTER_VERTICAL | Gravity.LEFT, m, Ui.px(14 * s), 0, 0));
            for (ImageView b : new ImageView[]{prev, play, next}) {
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(bs, bs);
                p.leftMargin = b == prev ? 0 : Ui.px(14 * s);
                b.setLayoutParams(p);
            }
            buttons.setLayoutParams(at(-2, -2, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, Ui.px(8 * s), m, 0));
            scan.setTextSize(0, 17 * Ui.k * s);
            scan.setPadding(Ui.px(26 * s), Ui.px(9 * s), Ui.px(26 * s), Ui.px(9 * s));
            scan.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.RIGHT, 0, Ui.px(8 * s), m, 0));
        }

        @Override
        public void changed(Hub h) {
            unit = h.unit;
            Prefs p = host.prefs();
            String pref = p.source(radio ? "radio" : "music");
            boolean auto = "auto".equals(pref);
            if (radio) source = auto ? (h.online ? Player.WEB : "app") : pref;
            else source = auto ? (h.online ? "app" : Player.FILES) : pref;
            String label = radio ? (Player.WEB.equals(source) ? "ONLINE RADIO" : "FM RADIO")
                    : (Player.FILES.equals(source) ? "MUSIC FILES" : "MUSIC APP");
            head.setText(label + (auto ? "  ·  AUTO" : "") + "  ▾");
            // FM through the unit, unless a radio app shows its own player (then that app's controls)
            unitFm = radio && "app".equals(source) && unit.fm(p) && (unit.fmFirst(p) || h.radio == null);
            if (radio) scan.setVisibility(unitFm && unit.canScan(p) ? VISIBLE : GONE);
            if (unitFm) {
                media = null;
                Unit.Show sh = unit.fmShow(p, fmNote, localOn);
                title.setText(sh.title);
                sub.setText(sh.sub);
                boolean on = sh.playing != null ? sh.playing : localOn;
                audioOn = on && (unit.fmFirst(p) || "radio".equals(owner));
                showPlay();
                return;
            }
            if (!"app".equals(source)) {
                media = null;
                boolean mine = isMine();
                Player.Track t = mine ? Player.current() : null;
                if (mine && !Player.status.isEmpty() && (Player.busy || !Player.playing)) {
                    title.setText(t != null ? t.name : radio ? "Online radio" : "Music files");
                    sub.setText(Player.status);
                } else if (t != null) {
                    title.setText(t.name);
                    sub.setText(Player.busy ? "Connecting…" : t.sub);
                } else {
                    title.setText(radio ? "Online radio" : "Music files");
                    sub.setText(radio ? "Tap play for Indian stations" : "Tap play for music on the unit or USB");
                }
                audioOn = mine && Player.playing;
            } else {
                media = radio ? h.radio : h.music;
                if (media != null && media.title != null) {
                    title.setText(media.title);
                    sub.setText(media.subtitle == null ? "" : media.subtitle);
                } else {
                    title.setText(radio ? "FM radio" : "Music app");
                    sub.setText("Tap to open " + (radio ? "the radio" : "the music app"));
                }
                audioOn = media != null ? media.playing : localOn && kind().equals(owner);
            }
            showPlay();
        }

        void showPlay() {
            play.setImageBitmap(Ui.img(getContext(), audioOn ? "btn_pause" : "btn_play"));
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
            foot.setText(m.sysPct >= 0 ? "Whole unit " + m.sysPct + "% CPU, this app " + m.appPct + "%"
                    : "CPU is this app's share; Android hides the whole unit's figure");
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
                foot.setText("Engine data is off in Settings > Car");
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
            if (p.str("obdMac", "").isEmpty()) foot.setText("Tap to choose your OBD-II Bluetooth adapter");
            else if (!live) foot.setText(Obd.status);
            else foot.setText((Obd.load >= 0 ? "Load " + Obd.load + "%" : "") + (n > 0 ? "  ·  " + n + " fault code" + (n == 1 ? "" : "s") : Obd.codesAt > 0 ? "  ·  no fault codes" : ""));
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
            head.setText("TYRES  ·  " + unit);
            if (!Features.on(p, "tpms")) {
                for (Figure f : figs) f.value.setText("--");
                foot.setText("Tyre sensors are off in Settings > Car");
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
            if (!any) foot.setText("Tap to find your tyre sensors");
            else if (!warn.isEmpty()) foot.setText(warn);
            else {
                Tpms.Tyre t = Tpms.tyres[0];
                foot.setText(t != null && !Float.isNaN(t.tempC) ? "Front left " + Math.round(t.tempC) + "°C  ·  " + Tpms.status : Tpms.status);
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
                hint.setText(Features.on(p, "widgets") ? "Tap to choose a widget from another app\n(long-press for other box contents)"
                        : "Widgets are off in Settings > Home screen");
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
