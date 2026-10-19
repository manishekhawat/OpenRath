package org.openrath.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * The car box: the car lit or dark with the headlights, the speed and the speed limit, turn
 * arrows, the welcome name, and warnings (speed cameras, service, tyres, engine) along the
 * bottom. The road drawn under the car and the chrome lettering are its own small views.
 */
final class CarCard extends Cards.Card {
    final ImageView brand = new ImageView(getContext()), car = new ImageView(getContext());
    final ChromeText carName = new ChromeText(getContext());
    final LinearLayout brandRow = new LinearLayout(getContext());
    final TextView speed = Ui.text(getContext(), 40, Ui.SILVER, "sans-serif-light", Gravity.RIGHT);
    final TextView unit = Ui.text(getContext(), 13, Ui.SILVER_DIM, "sans-serif-medium", Gravity.LEFT);
    final TextView lights = Ui.label(getContext(), Hindi.t("LIGHTS OFF"));
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
        hello.setText(Theme.text("welcomeTitle", Hindi.t("WELCOME")));
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
        lights.setText(Hindi.t(!learnt ? "LIGHTS: TAP TO SET UP" : on ? "LIGHTS ON" : "LIGHTS OFF"));
        lights.setTextColor(on ? Ui.SILVER : Ui.ACCENT);
        speed.setText(h.speed());
        String sp = h.speed();
        road.setSpeed(sp.equals("--") ? 0 : Integer.parseInt(sp));
        speed.setTextColor(Drive.over ? 0xFFFF5A4E : Ui.SILVER);
        limit.setText(String.valueOf(Drive.limit));
        limit.setVisibility(Drive.limit > 0 ? VISIBLE : GONE);
        String care = Features.on(p, "care") ? Hindi.t(Care.alert(p)) : "";
        // engine and tyre warnings come before service reminders
        if (Features.on(p, "tpms")) for (int w = 0; w < 4; w++) {
            String tw = Tpms.warning(p, w);
            if (!tw.isEmpty()) {
                care = Hindi.t(tw).toUpperCase(Locale.getDefault());
                break;
            }
        }
        if (Features.on(p, "obd") && Obd.connected && Obd.coolant >= p.num("obdHot", 105))
            care = Hindi.f("ENGINE HOT  %1$s°C", Obd.coolant);
        road.setVisibility(Ui.ROAD && Features.on(p, "road") ? VISIBLE : GONE);
        if (Drive.cameraM >= 0) {
            camera.setText(Hindi.f("SPEED CAMERA  %1$s m", Drive.cameraM / 10 * 10));
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
            if (kmh > 0.5f && last != 0 && Motion.frame() > 0) {
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
            if (kmh > 0.5f && Motion.frame() > 0) postInvalidateDelayed(Motion.frame());
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
}
