package org.openrath.launcher;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tools for cab drivers, all behind one switch (Features > Cab driver tools) and each with its own:
 * an earnings log (rides and costs, net per day and week), a fare meter for the passenger, the ride
 * apps one tap away, permit / fitness / badge reminders, CNG stations and a CNG log, pickup hotspots
 * learnt from your own rides, a daily target and duty-hours reminder, one-tap SOS, quiet-ride mode,
 * ride notes with lost-item photos, and spoken phrases for passengers. Everything stays on the unit.
 * The sums, fare and hotspots have no Android types, so they are unit-tested.
 */
final class Cab {

    static boolean on(Prefs p, String id) {
        return Features.on(p, "cab") && Features.on(p, id);
    }

    // ================================================================== earnings log (pure part)

    /** One line of the log: a ride (money in) or a cost (money out). */
    static final class Entry {
        long t;
        boolean ride;
        double rs, km, lat, lon;
        String app = "", pay = "", what = "";
    }

    static final class Sum {
        int rides;
        double in, out;

        double net() {
            return in - out;
        }
    }

    static Sum sum(List<Entry> l, long from, long to) {
        Sum s = new Sum();
        for (Entry e : l) {
            if (e.t < from || e.t >= to) continue;
            if (e.ride) {
                s.rides++;
                s.in += e.rs;
            } else s.out += e.rs;
        }
        return s;
    }

    /** Ride money by app (or by how it was paid), biggest first: {name, ₹}. */
    static List<Object[]> by(List<Entry> l, long from, long to, boolean pay) {
        Map<String, Double> m = new HashMap<>();
        for (Entry e : l) {
            if (!e.ride || e.t < from || e.t >= to) continue;
            String k = pay ? e.pay : e.app;
            if (k.isEmpty()) k = "Other";
            m.put(k, (m.containsKey(k) ? m.get(k) : 0) + e.rs);
        }
        List<Object[]> out = new ArrayList<>();
        for (Map.Entry<String, Double> x : m.entrySet()) out.add(new Object[]{x.getKey(), x.getValue()});
        Collections.sort(out, new java.util.Comparator<Object[]>() {
            @Override
            public int compare(Object[] a, Object[] b) {
                return Double.compare((Double) b[1], (Double) a[1]);
            }
        });
        return out;
    }

    static long dayStart(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Monday 00:00 of this week. */
    static long weekStart(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(dayStart(now));
        int back = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;   // Monday 0 … Sunday 6
        c.add(Calendar.DAY_OF_MONTH, -back);
        return c.getTimeInMillis();
    }

    static String rs(double v) {
        return "₹" + java.text.NumberFormat.getIntegerInstance(new Locale("en", "IN")).format(Math.round(v));
    }

    static String hm(long ms) {
        long m = Math.max(0, ms / 60000);
        return m < 60 ? m + " min" : m / 60 + " h " + m % 60 + " min";
    }

    // ================================================================== fare meter (pure part)

    static final class Rates {
        double base = 50, baseKm = 2, perKm = 16, waitPerMin = 2, freeWait = 5, nightPct = 25;
    }

    /** Base fare covers the first km, then per km; waiting after the free minutes; night adds a share. */
    static long fare(Rates r, double km, double waitMin, boolean night) {
        double f = r.base + Math.max(0, km - r.baseKm) * r.perKm + Math.max(0, waitMin - r.freeWait) * r.waitPerMin;
        if (night) f *= 1 + r.nightPct / 100;
        return Math.round(f);
    }

    /** Night charges from 11 pm to 5 am. */
    static boolean night(int hour) {
        return hour >= 23 || hour < 5;
    }

    // ================================================================== hotspots (pure part)

    /**
     * Where rides came from around this hour (±1 h), in squares of about 1 km: {lat, lon, rides},
     * busiest first, only squares with two rides or more.
     */
    static List<double[]> hotspots(List<Entry> l, int hourNow, int max) {
        Map<String, double[]> cells = new HashMap<>();
        Calendar c = Calendar.getInstance();
        for (Entry e : l) {
            if (!e.ride || e.lat == 0 && e.lon == 0) continue;
            c.setTimeInMillis(e.t);
            int d = Math.abs(c.get(Calendar.HOUR_OF_DAY) - hourNow);
            if (Math.min(d, 24 - d) > 1) continue;
            String k = Math.round(e.lat * 100) + ":" + Math.round(e.lon * 100);
            double[] v = cells.get(k);
            if (v == null) cells.put(k, v = new double[3]);
            v[0] += e.lat;
            v[1] += e.lon;
            v[2]++;
        }
        List<double[]> out = new ArrayList<>();
        for (double[] v : cells.values()) if (v[2] >= 2) out.add(new double[]{v[0] / v[2], v[1] / v[2], v[2]});
        Collections.sort(out, new java.util.Comparator<double[]>() {
            @Override
            public int compare(double[] a, double[] b) {
                return Double.compare(b[2], a[2]);
            }
        });
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    // ================================================================== papers (pure part)

    static final String[] PAPERS = {"permit", "fitness", "badge", "tax", "insurance", "puc"};
    static final String[] PAPER_NAMES = {"Permit", "Fitness certificate", "Driver badge", "Road tax", "Insurance", "PUC certificate"};

    /** Insurance and PUC share their dates with the service reminders. */
    static String paperKey(String id) {
        return "insurance".equals(id) || "puc".equals(id) ? "care:" + id + ":due" : "cab:" + id + ":due";
    }

    /** Whole days from today to a "yyyy-MM-dd" date (negative = gone by), or Long.MIN_VALUE if not set. */
    static long daysTo(String due, long now) {
        if (due == null || due.isEmpty()) return Long.MIN_VALUE;
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(due);
            return Math.round((dayStart(d.getTime()) - dayStart(now)) / 86400000.0);
        } catch (Exception e) {
            return Long.MIN_VALUE;
        }
    }

    static String dueText(String name, long days) {
        if (days < 0) return name + " expired " + (-days == 1 ? "yesterday" : -days + " days ago");
        if (days == 0) return name + " expires today";
        return name + " expires in " + days + (days == 1 ? " day" : " days");
    }

    // ================================================================== phrases

    static final String[] LANGS = {"hi", "en", "mr", "kn", "ta", "te", "bn"};
    static final String[] LANG_NAMES = {"Hindi", "English", "Marathi", "Kannada", "Tamil", "Telugu", "Bengali"};

    /** Each phrase: English, Hindi, Marathi, Kannada, Tamil, Telugu, Bengali (same order as LANGS). */
    static final String[][] PHRASES = {
            {"Welcome! Please fasten your seat belt.", "नमस्ते! कृपया सीट बेल्ट लगा लीजिए।", "नमस्कार! कृपया सीट बेल्ट लावा.",
                    "ನಮಸ್ಕಾರ! ದಯವಿಟ್ಟು ಸೀಟ್ ಬೆಲ್ಟ್ ಹಾಕಿಕೊಳ್ಳಿ.", "வணக்கம்! தயவுசெய்து சீட் பெல்ட் அணியுங்கள்.",
                    "నమస్కారం! దయచేసి సీట్ బెల్ట్ పెట్టుకోండి.", "নমস্কার! দয়া করে সিট বেল্ট লাগিয়ে নিন।"},
            {"Please tell me your OTP.", "कृपया अपना OTP बताइए।", "कृपया तुमचा OTP सांगा.", "ದಯವಿಟ್ಟು ನಿಮ್ಮ OTP ಹೇಳಿ.",
                    "தயவுசெய்து உங்கள் OTP-ஐ சொல்லுங்கள்.", "దయచేసి మీ OTP చెప్పండి.", "দয়া করে আপনার OTP বলুন।"},
            {"Is the AC temperature all right?", "AC का तापमान ठीक है?", "AC चे तापमान ठीक आहे का?", "AC ತಾಪಮಾನ ಸರಿಯಾಗಿದೆಯೇ?",
                    "AC வெப்பநிலை சரியாக உள்ளதா?", "AC ఉష్ణోగ్రత సరిగ్గా ఉందా?", "AC-র তাপমাত্রা ঠিক আছে তো?"},
            {"Please close the window, the AC is on.", "कृपया खिड़की बंद कर दीजिए, AC चल रहा है।", "कृपया खिडकी बंद करा, AC चालू आहे.",
                    "ದಯವಿಟ್ಟು ಕಿಟಕಿ ಮುಚ್ಚಿ, AC ಆನ್ ಆಗಿದೆ.", "தயவுசெய்து ஜன்னலை மூடுங்கள், AC ஓடுகிறது.",
                    "దయచేసి కిటికీ మూసేయండి, AC ఆన్‌లో ఉంది.", "দয়া করে জানালাটা বন্ধ করুন, AC চলছে।"},
            {"Shall I take the route on the map?", "क्या मैं मैप वाले रास्ते से चलूँ?", "मी नकाशातल्या रस्त्याने जाऊ का?",
                    "ನಕ್ಷೆಯ ದಾರಿಯಲ್ಲೇ ಹೋಗಲೇ?", "வரைபடத்தில் உள்ள வழியிலேயே போகலாமா?", "మ్యాప్‌లోని దారిలోనే వెళ్ళమంటారా?",
                    "ম্যাপের রাস্তা দিয়েই যাব?"},
            {"There is traffic ahead, it may take a little longer.", "आगे ट्रैफ़िक है, थोड़ा ज़्यादा समय लग सकता है।",
                    "पुढे ट्रॅफिक आहे, थोडा जास्त वेळ लागू शकतो.", "ಮುಂದೆ ಟ್ರಾಫಿಕ್ ಇದೆ, ಸ್ವಲ್ಪ ಹೆಚ್ಚು ಸಮಯ ಆಗಬಹುದು.",
                    "முன்னால் போக்குவரத்து நெரிசல் உள்ளது, கொஞ்சம் அதிக நேரம் ஆகலாம்.",
                    "ముందు ట్రాఫిక్ ఉంది, కొంచెం ఎక్కువ సమయం పట్టవచ్చు.", "সামনে জ্যাম আছে, একটু বেশি সময় লাগতে পারে।"},
            {"We have reached your destination.", "आपकी मंज़िल आ गई है।", "तुमचे ठिकाण आले आहे.", "ನಿಮ್ಮ ಸ್ಥಳ ಬಂದಿದೆ.",
                    "நீங்கள் செல்ல வேண்டிய இடம் வந்துவிட்டது.", "మీ గమ్యస్థానం వచ్చేసింది.", "আপনার গন্তব্যে পৌঁছে গেছি।"},
            {"You can pay by cash or UPI.", "आप नकद या UPI से भुगतान कर सकते हैं।", "तुम्ही रोख किंवा UPI ने पैसे देऊ शकता.",
                    "ನೀವು ನಗದು ಅಥವಾ UPI ಮೂಲಕ ಪಾವತಿಸಬಹುದು.", "நீங்கள் பணமாகவோ UPI மூலமாகவோ செலுத்தலாம்.",
                    "మీరు నగదు లేదా UPI ద్వారా చెల్లించవచ్చు.", "আপনি নগদ বা UPI-তে টাকা দিতে পারেন।"},
            {"Please check that you have all your belongings.", "कृपया देख लीजिए, आपका सारा सामान ले लिया है।",
                    "कृपया तुमचे सर्व सामान घेतले आहे का ते पाहा.", "ದಯವಿಟ್ಟು ನಿಮ್ಮ ಎಲ್ಲಾ ವಸ್ತುಗಳನ್ನು ತೆಗೆದುಕೊಂಡಿದ್ದೀರಾ ನೋಡಿ.",
                    "தயவுசெய்து உங்கள் எல்லா பொருட்களையும் எடுத்துக்கொண்டீர்களா என்று பாருங்கள்.",
                    "దయచేసి మీ వస్తువులన్నీ తీసుకున్నారో లేదో చూసుకోండి.", "দয়া করে দেখে নিন, আপনার সব জিনিস নিয়েছেন তো।"},
            {"Thank you, have a nice day!", "धन्यवाद, आपका दिन शुभ हो!", "धन्यवाद, तुमचा दिवस चांगला जावो!",
                    "ಧನ್ಯವಾದಗಳು, ನಿಮ್ಮ ದಿನ ಶುಭವಾಗಲಿ!", "நன்றி, உங்கள் நாள் இனிதாகட்டும்!", "ధన్యవాదాలు, మీ రోజు శుభంగా గడవాలి!",
                    "ধন্যবাদ, আপনার দিন ভালো কাটুক!"},
    };

    /** The phrase in a language; English is column 0, then LANGS without English. */
    static String phrase(int i, String lang) {
        int col;
        switch (lang) {
            case "en": col = 0; break;
            case "hi": col = 1; break;
            case "mr": col = 2; break;
            case "kn": col = 3; break;
            case "ta": col = 4; break;
            case "te": col = 5; break;
            case "bn": col = 6; break;
            default: col = 1;
        }
        return PHRASES[i][col];
    }

    // ================================================================== ride apps

    static final String[][] RIDE_APPS = {
            {"Uber Driver", "com.ubercab.driver"}, {"Ola Driver", "com.olacabs.oladriver"}, {"Rapido Captain", "com.rapido.rider"},
            {"inDrive", "sinet.startup.inDriver"}, {"Namma Yatri Driver", "in.juspay.nammayatripartner"},
    };
    private static final java.util.regex.Pattern RIDE_NAME = java.util.regex.Pattern.compile(
            "(?i)(uber|\\bola\\b|rapido|indrive|namma ?yatri|yatri sathi|blu ?smart|quick ?ride|jugnoo|meru)");

    // ================================================================== state

    private static List<Entry> cache;
    private static String cacheSrc;
    /** Bumped whenever the log changes, so the box redraws. */
    static volatile int version;
    static volatile boolean quiet;
    private static long lastTick, waitSavedAt;

    static List<Entry> log(Prefs p) {
        String src = p.str("cabLog", "[]");
        if (cache != null && src.equals(cacheSrc)) return cache;
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(src);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Entry e = new Entry();
                e.t = o.optLong("t");
                e.ride = "ride".equals(o.optString("k"));
                e.rs = o.optDouble("rs", 0);
                e.km = o.optDouble("km", 0);
                e.lat = o.optDouble("la", 0);
                e.lon = o.optDouble("lo", 0);
                e.app = o.optString("app", "");
                e.pay = o.optString("pay", "");
                e.what = o.optString("w", "");
                out.add(e);
            }
        } catch (Exception ignored) {
        }
        cache = out;
        cacheSrc = src;
        return out;
    }

    private static void save(Prefs p, List<Entry> l) {
        JSONArray a = new JSONArray();
        int from = Math.max(0, l.size() - 3000);   // a year or so of rides
        try {
            for (int i = from; i < l.size(); i++) {
                Entry e = l.get(i);
                JSONObject o = new JSONObject();
                o.put("t", e.t);
                o.put("k", e.ride ? "ride" : "cost");
                o.put("rs", e.rs);
                if (e.km > 0) o.put("km", e.km);
                if (e.lat != 0 || e.lon != 0) {
                    o.put("la", e.lat);
                    o.put("lo", e.lon);
                }
                if (!e.app.isEmpty()) o.put("app", e.app);
                if (!e.pay.isEmpty()) o.put("pay", e.pay);
                if (!e.what.isEmpty()) o.put("w", e.what);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        p.put("cabLog", a.toString());
        version++;
    }

    static void addRide(HomeActivity a, double rs, double km, String app, String pay, double lat, double lon) {
        Prefs p = a.prefs;
        long now = System.currentTimeMillis();
        Sum before = sum(log(p), dayStart(now), now + 1);
        List<Entry> l = new ArrayList<>(log(p));
        Entry e = new Entry();
        e.t = now;
        e.ride = true;
        e.rs = rs;
        e.km = km;
        e.app = app;
        e.pay = pay;
        e.lat = lat != 0 ? lat : Drive.latNow;
        e.lon = lon != 0 ? lon : Drive.lonNow;
        l.add(e);
        save(p, l);
        startDuty(p, now);
        // the daily target, said once when it is passed
        int target = p.num("cabTargetRs", 2000);
        if (on(p, "cabTarget") && target > 0 && before.net() < target && before.net() + rs >= target) {
            String t = "Target reached: " + rs(before.net() + rs) + " today. Well done!";
            Toast.makeText(a, t, Toast.LENGTH_LONG).show();
            if (!quiet) a.speak(t.replace("₹", "rupees "));
        }
    }

    static void addCost(Prefs p, double rs, String what) {
        List<Entry> l = new ArrayList<>(log(p));
        Entry e = new Entry();
        e.t = System.currentTimeMillis();
        e.rs = rs;
        e.what = what;
        l.add(e);
        save(p, l);
    }

    /** For the hub's change check: redraw the Cab box when any of this moves. */
    static String stamp(Prefs p) {
        if (!Features.on(p, "cab")) return "";
        long at = p.longNum("cabMeterAt", 0);
        String m = at == 0 ? "" : fareNow(p) + "/" + Math.round(meterKm(p) * 10) + "/" + p.longNum("cabMeterWait", 0) / 60000;
        long duty = p.longNum("cabDutyAt", 0);
        return version + m + quiet + (duty == 0 ? "" : String.valueOf((System.currentTimeMillis() - duty) / 60000));
    }

    // ================================================================== every second, from the hub

    static void tick(Context c, Prefs p) {
        if (!Features.on(p, "cab")) return;
        long now = System.currentTimeMillis();
        long dt = lastTick == 0 ? 0 : now - lastTick;
        lastTick = now;
        boolean fresh = now - Drive.fixAt < 10000;
        // meter: waiting time while standing or crawling
        if (p.longNum("cabMeterAt", 0) > 0 && fresh && Drive.kmhNow < 5 && dt > 0 && dt < 5000) {
            waitMs += dt;
            if (now - waitSavedAt > 15000) {
                waitSavedAt = now;
                p.putLong("cabMeterWait", waitMs);
            }
        }
        // duty starts with the first drive of the day
        if (on(p, "cabTarget")) {
            if (fresh && Drive.kmhNow > 10) startDuty(p, now);
            long duty = p.longNum("cabDutyAt", 0);
            int warnH = p.num("cabDutyH", 10);
            long h = duty == 0 ? 0 : (now - duty) / 3600000;
            if (duty > 0 && warnH > 0 && h >= warnH && h > p.num("cabDutyTold", 0)) {
                p.put("cabDutyTold", String.valueOf(h));
                String t = "You've been on duty for " + h + " hours. Time for a proper break.";
                Toast.makeText(c, t, Toast.LENGTH_LONG).show();
                if (!quiet) Speak.say(c, t, false);
            }
        }
        // papers: once a day, anything due within 15 days
        if (on(p, "cabPapers")) {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(now));
            if (!today.equals(p.str("cabPapersDay", "")) && now - Drive.fixAt < 600000) {
                p.put("cabPapersDay", today);
                List<String> due = new ArrayList<>();
                for (int i = 0; i < PAPERS.length; i++) {
                    long d = daysTo(p.str(paperKey(PAPERS[i]), ""), now);
                    if (d != Long.MIN_VALUE && d <= 15) due.add(dueText(PAPER_NAMES[i], d));
                }
                if (!due.isEmpty()) {
                    String t = android.text.TextUtils.join(". ", due) + ".";
                    Toast.makeText(c, t, Toast.LENGTH_LONG).show();
                    if (!quiet) Speak.say(c, t, false);
                }
            }
        }
    }

    private static long waitMs = -1;

    static void startDuty(Prefs p, long now) {
        if (!on(p, "cabTarget")) return;
        long duty = p.longNum("cabDutyAt", 0);
        if (duty >= dayStart(now)) return;
        p.putLong("cabDutyAt", now);
        p.put("cabDutyTold", "0");
    }

    // ================================================================== the menu

    /** Tap on the Cab box (or a steering-wheel button): everything in one list. */
    static void menu(final HomeActivity a) {
        final Prefs p = a.prefs;
        if (!Features.on(p, "cab")) {
            Toast.makeText(a, "Turn on Cab driver tools in Settings > Cab driver", Toast.LENGTH_LONG).show();
            return;
        }
        final List<String> names = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        if (on(p, "cabMeter")) {
            boolean running = p.longNum("cabMeterAt", 0) > 0;
            names.add(running ? "Meter: " + rs(fareNow(p)) + "  (show it)" : "Start the fare meter");
            acts.add(new Runnable() {
                @Override
                public void run() {
                    if (p.longNum("cabMeterAt", 0) == 0) startMeter(a);
                    showMeter(a);
                }
            });
        }
        if (on(p, "cabEarnings")) {
            names.add("Add a ride");
            acts.add(new Runnable() {
                @Override
                public void run() {
                    rideDialog(a, 0, 0, 0, 0, null);
                }
            });
            names.add("Add a cost (fuel, toll, food…)");
            acts.add(new Runnable() {
                @Override
                public void run() {
                    costDialog(a);
                }
            });
            names.add("Earnings: today and this week");
            acts.add(new Runnable() {
                @Override
                public void run() {
                    earnings(a);
                }
            });
        }
        if (on(p, "cabApps")) {
            names.add("Ride apps");
            acts.add(new Runnable() {
                @Override
                public void run() {
                    rideApps(a);
                }
            });
        }
        if (on(p, "cabQuiet")) {
            names.add(quiet ? "Quiet ride: on (tap to end)" : "Passenger in: quiet ride");
            acts.add(new Runnable() {
                @Override
                public void run() {
                    setQuiet(a, !quiet);
                }
            });
        }
        if (on(p, "cabPhrases")) {
            names.add("Phrases for the passenger");
            acts.add(new Runnable() {
                @Override
                public void run() {
                    phrases(a);
                }
            });
        }
        if (on(p, "cabSpots")) {
            names.add("Pickup hotspots now");
            acts.add(new Runnable() {
                @Override
                public void run() {
                    hotspotList(a);
                }
            });
        }
        if (on(p, "cabCng")) {
            names.add("CNG stations near here");
            acts.add(new Runnable() {
                @Override
                public void run() {
                    findCng(a);
                }
            });
            names.add("CNG fill-ups");
            acts.add(new Runnable() {
                @Override
                public void run() {
                    cngLog(a);
                }
            });
        }
        if (on(p, "cabNotes")) {
            names.add("Ride notes and lost items");
            acts.add(new Runnable() {
                @Override
                public void run() {
                    notes(a);
                }
            });
        }
        if (on(p, "cabPapers")) {
            names.add("Papers due");
            acts.add(new Runnable() {
                @Override
                public void run() {
                    papers(a, null);
                }
            });
        }
        if (on(p, "cabTarget")) {
            long duty = p.longNum("cabDutyAt", 0);
            boolean onDuty = duty >= dayStart(System.currentTimeMillis());
            names.add(onDuty ? "End duty (on for " + hm(System.currentTimeMillis() - duty) + ")" : "Start duty now");
            acts.add(new Runnable() {
                @Override
                public void run() {
                    long d = p.longNum("cabDutyAt", 0);
                    if (d >= dayStart(System.currentTimeMillis())) {
                        Toast.makeText(a, "Duty ended after " + hm(System.currentTimeMillis() - d), Toast.LENGTH_LONG).show();
                        p.putLong("cabDutyAt", 0);
                        // stays ended for today unless started again from here
                        p.putLong("cabDutyEnded", System.currentTimeMillis());
                    } else {
                        p.putLong("cabDutyAt", System.currentTimeMillis());
                        p.put("cabDutyTold", "0");
                    }
                    version++;
                }
            });
        }
        if (on(p, "cabSos")) {
            names.add("SOS: send my location");
            acts.add(new Runnable() {
                @Override
                public void run() {
                    sos(a);
                }
            });
        }
        if (names.isEmpty()) {
            Toast.makeText(a, "All the cab tools are switched off in Settings > Cab driver", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title(p))
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        acts.get(w).run();
                        a.hub.notifyIfChanged(true);
                    }
                }).setNegativeButton("Close", null).show();
    }

    private static String title(Prefs p) {
        long now = System.currentTimeMillis();
        Sum s = sum(log(p), dayStart(now), now + 1);
        return "Cab  ·  today " + rs(s.net()) + " net, " + s.rides + (s.rides == 1 ? " ride" : " rides");
    }

    // ================================================================== earnings screens

    static final String[] APPS = {"Uber", "Ola", "Rapido", "inDrive", "Namma Yatri", "Street", "Other"};
    static final String[] PAYS = {"Cash", "UPI", "In the app"};
    static final String[] COSTS = {"Fuel or CNG", "Toll", "Parking", "Food", "Commission", "Repair", "Other"};

    private static LinearLayout box(Context a) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.px(20), Ui.px(8), Ui.px(20), 0);
        return box;
    }

    private static EditText number(LinearLayout box, String hint, String value) {
        EditText e = new EditText(box.getContext());
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (value != null) e.setText(value);
        box.addView(e);
        return e;
    }

    /** A line that steps through choices on each tap: "App: Uber  ▸". */
    private static TextView chooser(LinearLayout box, final String label, final String[] choices, final String[] chosen) {
        final TextView t = new TextView(box.getContext());
        t.setTextSize(18);
        t.setPadding(0, Ui.px(12), 0, Ui.px(12));
        t.setText(label + ": " + chosen[0] + "   ▸");
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int i = java.util.Arrays.asList(choices).indexOf(chosen[0]);
                chosen[0] = choices[(i + 1) % choices.length];
                t.setText(label + ": " + chosen[0] + "   ▸");
            }
        });
        box.addView(t);
        return t;
    }

    private static double num(EditText e) {
        try {
            return Double.parseDouble(e.getText().toString().trim());
        } catch (Exception ex) {
            return -1;
        }
    }

    static void rideDialog(final HomeActivity a, long fare, final double km, final double lat, final double lon, String pay) {
        final Prefs p = a.prefs;
        LinearLayout box = box(a);
        final EditText amount = number(box, "Fare (₹)", fare > 0 ? String.valueOf(fare) : null);
        final String[] app = {p.str("cabLastApp", "Uber")};
        final String[] how = {pay != null ? pay : p.str("cabLastPay", "Cash")};
        chooser(box, "App", APPS, app);
        chooser(box, "Paid by", PAYS, how);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Add a ride").setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        double v = num(amount);
                        if (v <= 0) {
                            Toast.makeText(a, "Enter the fare in rupees", Toast.LENGTH_LONG).show();
                            return;
                        }
                        p.put("cabLastApp", app[0]);
                        p.put("cabLastPay", how[0]);
                        addRide(a, v, km, app[0], how[0], lat, lon);
                        a.hub.notifyIfChanged(true);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    static void costDialog(final HomeActivity a) {
        final Prefs p = a.prefs;
        LinearLayout box = box(a);
        final EditText amount = number(box, "Amount (₹)", null);
        final String[] what = {p.str("cabLastCost", COSTS[0])};
        chooser(box, "For", COSTS, what);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Add a cost").setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        double v = num(amount);
                        if (v <= 0) {
                            Toast.makeText(a, "Enter the amount in rupees", Toast.LENGTH_LONG).show();
                            return;
                        }
                        p.put("cabLastCost", what[0]);
                        addCost(p, v, what[0]);
                        a.hub.notifyIfChanged(true);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    static String report(Prefs p, long now) {
        List<Entry> l = log(p);
        long today = dayStart(now), week = weekStart(now);
        StringBuilder b = new StringBuilder();
        Object[][] spans = {{"Today", today, now + 1}, {"Yesterday", dayStart(today - 3600000), today},
                {"This week (from Monday)", week, now + 1}, {"Last 30 days", today - 29 * 86400000L, now + 1}};
        for (Object[] s : spans) {
            Sum x = sum(l, (Long) s[1], (Long) s[2]);
            b.append(s[0]).append(": ").append(rs(x.net())).append(" net\n   ").append(x.rides).append(x.rides == 1 ? " ride " : " rides ")
                    .append(rs(x.in)).append(",  costs ").append(rs(x.out)).append("\n");
        }
        List<Object[]> apps = by(l, week, now + 1, false), pays = by(l, today, now + 1, true);
        if (!apps.isEmpty()) {
            b.append("\nThis week by app: ");
            for (int i = 0; i < apps.size(); i++) b.append(i == 0 ? "" : ",  ").append(apps.get(i)[0]).append(" ").append(rs((Double) apps.get(i)[1]));
        }
        if (!pays.isEmpty()) {
            b.append("\nToday by payment: ");
            for (int i = 0; i < pays.size(); i++) b.append(i == 0 ? "" : ",  ").append(pays.get(i)[0]).append(" ").append(rs((Double) pays.get(i)[1]));
        }
        int target = p.num("cabTargetRs", 2000);
        if (on(p, "cabTarget") && target > 0) {
            double net = sum(l, today, now + 1).net();
            b.append("\n\nDaily target ").append(rs(target)).append(": ")
                    .append(net >= target ? "reached" : rs(target - net) + " to go");
        }
        return b.toString().trim();
    }

    /** What "how much did I earn" says. */
    static String spoken(Prefs p) {
        long now = System.currentTimeMillis();
        Sum s = sum(log(p), dayStart(now), now + 1);
        if (s.rides == 0 && s.out == 0) return "No rides logged today yet.";
        return "Today, " + s.rides + (s.rides == 1 ? " ride, " : " rides, ") + Math.round(s.in) + " rupees in, "
                + Math.round(s.out) + " rupees of costs, " + Math.round(s.net()) + " rupees net.";
    }

    static void earnings(final HomeActivity a) {
        final Prefs p = a.prefs;
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Earnings").setMessage(report(p, System.currentTimeMillis()))
                .setPositiveButton("Add a ride", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        rideDialog(a, 0, 0, 0, 0, null);
                    }
                })
                .setNeutralButton("All entries", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        entries(a);
                    }
                })
                .setNegativeButton("Close", null).show();
    }

    static void entries(final HomeActivity a) {
        final Prefs p = a.prefs;
        final List<Entry> l = log(p);
        int n = Math.min(l.size(), 200);
        String[] rows = new String[n];
        SimpleDateFormat f = new SimpleDateFormat("d MMM  HH:mm", Locale.getDefault());
        for (int i = 0; i < n; i++) {
            Entry e = l.get(l.size() - 1 - i);
            rows[i] = f.format(new Date(e.t)) + "   " + (e.ride ? "+" + rs(e.rs) + "  " + e.app + ", " + e.pay
                    + (e.km > 0 ? ", " + Fuel.fmt(e.km) + " km" : "") : "−" + rs(e.rs) + "  " + e.what);
        }
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(n == 0 ? "Nothing logged yet" : "Rides and costs (tap one to delete it)")
                .setItems(rows, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, final int which) {
                        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                                .setMessage("Delete this entry?")
                                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d2, int w) {
                                        List<Entry> out = new ArrayList<>(l);
                                        out.remove(l.size() - 1 - which);
                                        save(p, out);
                                        a.hub.notifyIfChanged(true);
                                    }
                                }).setNegativeButton("Keep", null).show();
                    }
                }).setNegativeButton("Close", null).show();
    }

    // ================================================================== fare meter

    static Rates rates(Prefs p) {
        Rates r = new Rates();
        r.base = dbl(p, "cabBase", r.base);
        r.baseKm = dbl(p, "cabBaseKm", r.baseKm);
        r.perKm = dbl(p, "cabPerKm", r.perKm);
        r.waitPerMin = dbl(p, "cabWaitRs", r.waitPerMin);
        r.freeWait = dbl(p, "cabFreeWait", r.freeWait);
        r.nightPct = dbl(p, "cabNightPct", r.nightPct);
        return r;
    }

    private static double dbl(Prefs p, String k, double def) {
        try {
            return Double.parseDouble(p.str(k, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }

    static double meterKm(Prefs p) {
        return Math.max(0, Drive.odoKm - dbl(p, "cabMeterOdo", Drive.odoKm));
    }

    static long meterWaitMs(Prefs p) {
        if (waitMs < 0) waitMs = p.longNum("cabMeterWait", 0);
        return waitMs;
    }

    static boolean meterNight(Prefs p) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(p.longNum("cabMeterAt", System.currentTimeMillis()));
        return night(c.get(Calendar.HOUR_OF_DAY)) || night(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
    }

    static long fareNow(Prefs p) {
        return fare(rates(p), meterKm(p), meterWaitMs(p) / 60000.0, meterNight(p));
    }

    static void startMeter(HomeActivity a) {
        Prefs p = a.prefs;
        long now = System.currentTimeMillis();
        p.putLong("cabMeterAt", now);
        p.put("cabMeterOdo", String.valueOf(Drive.odoKm));
        p.putLong("cabMeterWait", 0);
        p.put("cabMeterLat", String.valueOf(Drive.latNow));
        p.put("cabMeterLon", String.valueOf(Drive.lonNow));
        waitMs = 0;
        startDuty(p, now);
        if (on(p, "cabQuiet") && "on".equals(p.str("cabQuietWithMeter", "on")) && !quiet) setQuiet(a, true);
        version++;
    }

    /** The meter, big, for the passenger to see. */
    static void showMeter(final HomeActivity a) {
        final Prefs p = a.prefs;
        final Dialog d = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setBackgroundColor(0xFF050607);
        TextView head = Ui.text(a, 22, Ui.SILVER_DIM, "sans-serif-medium", Gravity.CENTER);
        head.setText("FARE  ·  किराया");
        col.addView(head);
        final TextView fare = Ui.text(a, 120, 0xFFFFFFFF, "sans-serif-light", Gravity.CENTER);
        col.addView(fare);
        final TextView info = Ui.text(a, 24, Ui.SILVER, "sans-serif", Gravity.CENTER);
        info.setPadding(0, 0, 0, Ui.px(26));
        col.addView(info);
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER);
        TextView hide = button(a, "Hide", 0xFF33363C), stop = button(a, "Stop and charge", 0xFF1F6F45);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.px(260), Ui.px(76));
        bp.leftMargin = bp.rightMargin = Ui.px(14);
        row.addView(hide, bp);
        row.addView(stop, bp);
        col.addView(row);
        d.setContentView(col);
        final Runnable[] tick = new Runnable[1];
        tick[0] = new Runnable() {
            @Override
            public void run() {
                if (p.longNum("cabMeterAt", 0) == 0) {
                    d.dismiss();
                    return;
                }
                Rates r = rates(p);
                fare.setText(rs(fareNow(p)));
                long w = meterWaitMs(p) / 60000;
                info.setText(Fuel.fmt(meterKm(p)) + " km   ·   " + w + " min waiting   ·   " + hm(System.currentTimeMillis() - p.longNum("cabMeterAt", 0))
                        + (meterNight(p) ? "\nNight charge +" + Math.round(r.nightPct) + "%" : "")
                        + "\n" + rs(r.base) + " for the first " + Fuel.fmt(r.baseKm) + " km, then " + rs(r.perKm) + " a km");
                fare.postDelayed(this, 1000);
            }
        };
        hide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                stopMeter(a);
            }
        });
        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface di) {
                fare.removeCallbacks(tick[0]);
            }
        });
        d.show();
        tick[0].run();
    }

    static void stopMeter(final HomeActivity a) {
        final Prefs p = a.prefs;
        if (p.longNum("cabMeterAt", 0) == 0) return;
        final long f = fareNow(p);
        final double km = meterKm(p);
        final double lat = dbl(p, "cabMeterLat", 0), lon = dbl(p, "cabMeterLon", 0);
        long w = meterWaitMs(p) / 60000;
        String said = Fuel.fmt(km) + " km, " + w + " min waiting" + (meterNight(p) ? ", night charge" : "");
        p.putLong("cabMeterAt", 0);
        p.putLong("cabMeterWait", 0);
        waitMs = 0;
        if (quiet && "on".equals(p.str("cabQuietWithMeter", "on"))) setQuiet(a, false);
        version++;
        final boolean log = on(p, "cabEarnings");
        final String upi = p.str("upiId", "");
        AlertDialog.Builder b = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Fare " + rs(f)).setMessage(said + (log ? "\n\nSave it to the earnings log?" : ""))
                .setPositiveButton(log ? "Paid in cash" : "OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (log) addRide(a, f, km, "Meter", "Cash", lat, lon);
                        a.hub.notifyIfChanged(true);
                    }
                })
                .setNeutralButton(Errands.validVpa(upi) ? "UPI QR" : "Paid by UPI", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (log) addRide(a, f, km, "Meter", "UPI", lat, lon);
                        if (Errands.validVpa(upi)) Qr.show(a, "Pay " + rs(f), Errands.upiLink(upi, p.str("upiName", ""), (int) f, "Cab fare"),
                                "Scan with any UPI app (GPay, PhonePe, Paytm, BHIM); the fare is filled in. Pays " + upi + ".");
                        else Toast.makeText(a, "Set your UPI id in Settings > Cab driver to show a QR code", Toast.LENGTH_LONG).show();
                        a.hub.notifyIfChanged(true);
                    }
                });
        if (log) b.setNegativeButton("Don't save", null);
        b.show();
    }

    private static TextView button(Context a, String s, int colour) {
        TextView b = Ui.text(a, 22, 0xFFFFFFFF, "sans-serif-medium", Gravity.CENTER);
        b.setText(s);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.px(16));
        g.setColor(colour);
        b.setBackground(g);
        b.setClickable(true);
        return b;
    }

    // ================================================================== ride apps

    /** The ride apps on the unit: {name, package}, the last used first. */
    static List<String[]> installedRideApps(Context c, Prefs p) {
        List<String[]> out = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        List<String> seen = new ArrayList<>();
        for (String[] r : RIDE_APPS) {
            if (pm.getLaunchIntentForPackage(r[1]) != null) {
                out.add(r);
                seen.add(r[1]);
            }
        }
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
                String pkg = ri.activityInfo.packageName;
                String label = String.valueOf(ri.loadLabel(pm));
                if (!seen.contains(pkg) && RIDE_NAME.matcher(label).find()) {
                    out.add(new String[]{label, pkg});
                    seen.add(pkg);
                }
            }
        } catch (Exception ignored) {
        }
        String last = p.str("cabLastRideApp", "");
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i)[1].equals(last)) {
                out.add(0, out.remove(i));
                break;
            }
        }
        return out;
    }

    static void rideApps(final HomeActivity a) {
        final List<String[]> apps = installedRideApps(a, a.prefs);
        if (apps.isEmpty()) {
            Toast.makeText(a, "No ride apps found. Install Uber Driver, Ola Driver, Rapido Captain or another from the Play Store.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[apps.size()];
        for (int i = 0; i < names.length; i++) names[i] = apps.get(i)[0];
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Ride apps")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        openApp(a, apps.get(w)[1]);
                    }
                }).setNegativeButton("Close", null).show();
    }

    static void openApp(HomeActivity a, String pkg) {
        Intent i = a.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return;
        a.prefs.put("cabLastRideApp", pkg);
        a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    // ================================================================== quiet ride

    static void setQuiet(Context c, boolean yes) {
        Prefs p = new Prefs(c);
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        try {
            if (yes && !quiet) {
                int now = am.getStreamVolume(AudioManager.STREAM_MUSIC), max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                p.put("cabQuietWas", String.valueOf(now));
                int to = Math.round(max * p.num("cabQuietPct", 30) / 100f);
                if (now > to) am.setStreamVolume(AudioManager.STREAM_MUSIC, to, 0);
            } else if (!yes && quiet) {
                int was = p.num("cabQuietWas", -1);
                if (was >= 0) am.setStreamVolume(AudioManager.STREAM_MUSIC, was, 0);
            }
        } catch (Exception ignored) {
        }
        quiet = yes;
        version++;
        Toast.makeText(c, yes ? "Quiet ride: music down, messages not read aloud" : "Quiet ride ended", Toast.LENGTH_SHORT).show();
    }

    // ================================================================== phrases

    static void phrases(final HomeActivity a) {
        final Prefs p = a.prefs;
        final String lang = p.str("cabLang", "hi");
        final List<String> list = new ArrayList<>();
        for (int i = 0; i < PHRASES.length; i++) list.add(phrase(i, lang));
        for (String s : p.str("cabOwnPhrases", "").split("\n")) if (!s.trim().isEmpty()) list.add(s.trim());
        int li = java.util.Arrays.asList(LANGS).indexOf(lang);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Say to the passenger (" + LANG_NAMES[Math.max(0, li)] + ")")
                .setItems(list.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String t = list.get(w);
                        boolean own = w >= PHRASES.length;
                        Locale loc = own ? Locale.getDefault() : "en".equals(lang) ? new Locale("en", "IN") : new Locale(lang, "IN");
                        if (!own || !t.matches("[\\x00-\\x7F]*")) loc = own ? guess(t) : loc;
                        if (!Speak.sayIn(a, t, loc)) {
                            Toast.makeText(a, "No " + loc.getDisplayLanguage(Locale.ENGLISH) + " voice on the unit, so here it is in English", Toast.LENGTH_LONG).show();
                            if (!own) Speak.say(a, PHRASES[w][0], true);
                        }
                        Toast.makeText(a, t, Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton("Language", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                                .setTitle("Phrases in")
                                .setItems(LANG_NAMES, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d2, int w2) {
                                        p.put("cabLang", LANGS[w2]);
                                        phrases(a);
                                    }
                                }).show();
                    }
                })
                .setPositiveButton("Add your own", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        final EditText e = new EditText(a);
                        e.setHint("Type it in any language");
                        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                                .setTitle("Your own phrase").setView(e)
                                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d2, int w2) {
                                        String s = e.getText().toString().replace('\n', ' ').trim();
                                        if (s.isEmpty()) return;
                                        String old = p.str("cabOwnPhrases", "");
                                        p.put("cabOwnPhrases", old.isEmpty() ? s : old + "\n" + s);
                                        phrases(a);
                                    }
                                }).setNegativeButton("Cancel", null).show();
                    }
                }).show();
    }

    /** The language of a typed phrase, from its script. */
    static Locale guess(String s) {
        for (char ch : s.toCharArray()) {
            Character.UnicodeBlock b = Character.UnicodeBlock.of(ch);
            if (b == Character.UnicodeBlock.DEVANAGARI) return new Locale("hi", "IN");
            if (b == Character.UnicodeBlock.KANNADA) return new Locale("kn", "IN");
            if (b == Character.UnicodeBlock.TAMIL) return new Locale("ta", "IN");
            if (b == Character.UnicodeBlock.TELUGU) return new Locale("te", "IN");
            if (b == Character.UnicodeBlock.BENGALI) return new Locale("bn", "IN");
            if (b == Character.UnicodeBlock.GUJARATI) return new Locale("gu", "IN");
            if (b == Character.UnicodeBlock.GURMUKHI) return new Locale("pa", "IN");
            if (b == Character.UnicodeBlock.MALAYALAM) return new Locale("ml", "IN");
        }
        return new Locale("en", "IN");
    }

    // ================================================================== hotspots

    static void hotspotList(final HomeActivity a) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        final List<double[]> spots = hotspots(log(a.prefs), hour, 6);
        if (spots.isEmpty()) {
            new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("Pickup hotspots")
                    .setMessage("Not enough rides around this hour yet. Each ride you log (or meter) remembers where it started; "
                            + "places with two or more rides around this time of day show here.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        String[] rows = new String[spots.size()];
        for (int i = 0; i < rows.length; i++) {
            double[] s = spots.get(i);
            String far = Drive.latNow == 0 ? "" : String.format(Locale.US, "%.1f km away  ·  ",
                    Geo.dist(Drive.latNow, Drive.lonNow, s[0], s[1]) / 1000);
            rows[i] = far + Math.round(s[2]) + " rides around " + hour + ":00   (" + Rescue.where(s[0], s[1]) + ")";
        }
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Where you got rides at this hour (tap to drive there)")
                .setItems(rows, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        a.navigate(spots.get(w)[0], spots.get(w)[1], "Pickup hotspot");
                    }
                }).setNegativeButton("Close", null).show();
    }

    // ================================================================== CNG

    static void findCng(HomeActivity a) {
        double lat = Drive.latNow, lon = Drive.lonNow;
        String q = "geo:" + (lat == 0 ? "0,0" : String.format(Locale.US, "%.5f,%.5f", lat, lon)) + "?q=" + android.net.Uri.encode("CNG station");
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(q)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(a, "Needs a map app (Google Maps, Organic Maps or OsmAnd)", Toast.LENGTH_LONG).show();
        }
    }

    static JSONArray cng(Prefs p) {
        try {
            return new JSONArray(p.str("cabCng", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /** Km per kg between fills (each fill tops the cylinder up): {last, average, ₹ a km}. */
    static double[] cngStats(JSONArray l) {
        int n = l.length();
        double[] odo = new double[n], kg = new double[n], rs = new double[n];
        boolean[] full = new boolean[n];
        long[] at = new long[n];
        for (int i = 0; i < n; i++) {
            JSONObject e = l.optJSONObject(i);
            odo[i] = e.optDouble("odo");
            kg[i] = e.optDouble("kg");
            rs[i] = e.optDouble("rs");
            full[i] = true;
            at[i] = e.optLong("t");
        }
        Fuel.Stats s = Fuel.compute(odo, kg, rs, full, at);
        return new double[]{s.lastKmpl, s.avgKmpl, s.rupeesPerKm};
    }

    static void cngLog(final HomeActivity a) {
        final Prefs p = a.prefs;
        final JSONArray l = cng(p);
        double[] st = cngStats(l);
        String[] rows = new String[l.length()];
        SimpleDateFormat f = new SimpleDateFormat("d MMM", Locale.getDefault());
        for (int i = 0; i < rows.length; i++) {
            JSONObject e = l.optJSONObject(l.length() - 1 - i);
            rows[i] = f.format(new Date(e.optLong("t"))) + "   " + Fuel.fmt(e.optDouble("kg")) + " kg   " + rs(e.optDouble("rs"))
                    + "   " + Math.round(e.optDouble("odo")) + " km";
        }
        String head = st[1] > 0 ? "CNG  ·  " + Fuel.fmt(st[1]) + " km/kg average, " + Fuel.fmt(st[0]) + " last, ₹" + Fuel.fmt(st[2]) + " a km"
                : rows.length == 0 ? "No CNG fills yet" : "CNG  ·  km/kg shows after two fills";
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(head)
                .setItems(rows, null)
                .setPositiveButton("Add a fill", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        addCng(a);
                    }
                }).setNegativeButton("Close", null).show();
    }

    static void addCng(final HomeActivity a) {
        final Prefs p = a.prefs;
        LinearLayout box = box(a);
        final EditText kg = number(box, "Kg", null);
        final EditText rupees = number(box, "Amount paid (₹)", null);
        final EditText odo = number(box, "Odometer (km)", String.valueOf(Care.odo(p)));
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Add a CNG fill").setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        double k = num(kg), r = num(rupees), o = num(odo);
                        if (k <= 0 || o <= 0) {
                            Toast.makeText(a, "Enter the kg and the odometer as numbers", Toast.LENGTH_LONG).show();
                            return;
                        }
                        try {
                            JSONObject e = new JSONObject();
                            e.put("t", System.currentTimeMillis());
                            e.put("kg", k);
                            e.put("rs", Math.max(0, r));
                            e.put("odo", o);
                            JSONArray l = cng(p);
                            l.put(e);
                            p.put("cabCng", l.toString());
                        } catch (Exception ignored) {
                        }
                        // the fill is a cost in the earnings too
                        if (r > 0 && on(p, "cabEarnings")) addCost(p, r, "CNG");
                        a.hub.notifyIfChanged(true);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    // ================================================================== notes and lost items

    static JSONArray notesList(Prefs p) {
        try {
            return new JSONArray(p.str("cabNotes", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static void addNote(Prefs p, String text, String photo) {
        try {
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis());
            o.put("text", text);
            if (photo != null) o.put("photo", photo);
            JSONArray l = notesList(p);
            l.put(o);
            p.put("cabNotes", l.toString());
        } catch (Exception ignored) {
        }
    }

    static void notes(final HomeActivity a) {
        final Prefs p = a.prefs;
        final JSONArray l = notesList(p);
        String[] rows = new String[l.length()];
        SimpleDateFormat f = new SimpleDateFormat("d MMM HH:mm", Locale.getDefault());
        for (int i = 0; i < rows.length; i++) {
            JSONObject o = l.optJSONObject(l.length() - 1 - i);
            rows[i] = f.format(new Date(o.optLong("t"))) + "   " + (o.has("photo") ? "📷 " : "") + o.optString("text");
        }
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(rows.length == 0 ? "No notes yet" : "Ride notes")
                .setItems(rows, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        showNote(a, l.length() - 1 - w);
                    }
                })
                .setPositiveButton("Lost item photo", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        lostItem(a);
                    }
                })
                .setNeutralButton("Add a note", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        noteText(a, "", null);
                    }
                }).setNegativeButton("Close", null).show();
    }

    private static void noteText(final HomeActivity a, String start, final String photo) {
        final EditText e = new EditText(a);
        e.setHint("Passenger's name, what was left, a phone number…");
        e.setText(start);
        e.setSelection(start.length());
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(photo == null ? "Note" : "What was left, and by whom?").setView(e)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String s = e.getText().toString().trim();
                        if (s.isEmpty() && photo == null) return;
                        addNote(a.prefs, s.isEmpty() ? "Left in the car" : s, photo);
                        Toast.makeText(a, "Saved in Ride notes", Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    static void lostItem(final HomeActivity a) {
        final File f = Storage.dir("lost-items/" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".jpg");
        boolean shooting = Snap.photo(a, a.prefs, f, new Runnable() {
            @Override
            public void run() {
                Toast.makeText(a, "Photo saved", Toast.LENGTH_SHORT).show();
            }
        });
        if (!shooting) Toast.makeText(a, "No camera free for a photo, so it's a note only", Toast.LENGTH_LONG).show();
        else Toast.makeText(a, "Taking a photo with the car's camera…", Toast.LENGTH_SHORT).show();
        noteText(a, "Left in the car: ", shooting ? f.getPath() : null);
    }

    private static void showNote(final HomeActivity a, final int index) {
        final Prefs p = a.prefs;
        JSONObject o = notesList(p).optJSONObject(index);
        if (o == null) return;
        LinearLayout box = box(a);
        TextView t = new TextView(a);
        t.setTextSize(18);
        t.setText(o.optString("text"));
        box.addView(t);
        String photo = o.optString("photo", "");
        if (!photo.isEmpty() && new File(photo).exists()) {
            android.graphics.BitmapFactory.Options op = new android.graphics.BitmapFactory.Options();
            op.inSampleSize = 4;
            ImageView img = new ImageView(a);
            img.setAdjustViewBounds(true);
            img.setImageBitmap(android.graphics.BitmapFactory.decodeFile(photo, op));
            box.addView(img, new LinearLayout.LayoutParams(-1, Ui.px(300)));
        }
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(o.optLong("t"))))
                .setView(box)
                .setPositiveButton("Close", null)
                .setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        JSONArray l = notesList(p), out = new JSONArray();
                        for (int i = 0; i < l.length(); i++) if (i != index) out.put(l.opt(i));
                        p.put("cabNotes", out.toString());
                    }
                }).show();
    }

    // ================================================================== papers

    static void papers(final HomeActivity a, final Runnable done) {
        final Prefs p = a.prefs;
        long now = System.currentTimeMillis();
        String[] rows = new String[PAPERS.length];
        for (int i = 0; i < rows.length; i++) {
            long d = daysTo(p.str(paperKey(PAPERS[i]), ""), now);
            rows[i] = d == Long.MIN_VALUE ? PAPER_NAMES[i] + ": tap to set the expiry date" : dueText(PAPER_NAMES[i], d)
                    + "  (" + p.str(paperKey(PAPERS[i]), "") + ")";
        }
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Papers (reminded 15 days ahead)")
                .setItems(rows, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, final int w) {
                        Calendar c = Calendar.getInstance();
                        new android.app.DatePickerDialog(a, android.R.style.Theme_Material_Dialog_Alert, new android.app.DatePickerDialog.OnDateSetListener() {
                            @Override
                            public void onDateSet(android.widget.DatePicker v, int y, int m, int day) {
                                p.put(paperKey(PAPERS[w]), String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, day));
                                p.put("cabPapersDay", "");   // check again today
                                papers(a, done);
                                if (done != null) done.run();
                            }
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
                    }
                }).setNegativeButton("Close", null).show();
    }

    // ================================================================== SOS

    static String[] contact(Prefs p, int i) {
        String v = p.str("cabSos" + i, "");
        int bar = v.indexOf('|');
        if (bar < 0 || bar == v.length() - 1) return null;
        return new String[]{v.substring(0, bar), v.substring(bar + 1)};
    }

    static String sosText(Prefs p, double lat, double lon, long now) {
        String who = p.welcome().isEmpty() ? "your driver" : p.welcome();
        String car = p.str("cabVehicle", "");
        return "SOS from " + who + (car.isEmpty() ? "" : " (" + car + ")") + ": I need help. "
                + (lat == 0 && lon == 0 ? "Location not known yet." : "I am here: " + Geo.mapsLink(lat, lon))
                + " at " + new SimpleDateFormat("HH:mm, d MMM", Locale.US).format(new Date(now)) + ".";
    }

    static void editContacts(final HomeActivity a, final Runnable done) {
        final Prefs p = a.prefs;
        LinearLayout box = box(a);
        final EditText[] f = new EditText[4];
        for (int i = 0; i < 2; i++) {
            String[] c = contact(p, i + 1);
            f[i * 2] = new EditText(a);
            f[i * 2].setHint("Contact " + (i + 1) + ": name");
            f[i * 2].setText(c == null ? "" : c[0]);
            box.addView(f[i * 2]);
            f[i * 2 + 1] = new EditText(a);
            f[i * 2 + 1].setHint("Contact " + (i + 1) + ": phone number");
            f[i * 2 + 1].setInputType(InputType.TYPE_CLASS_PHONE);
            f[i * 2 + 1].setText(c == null ? "" : c[1]);
            box.addView(f[i * 2 + 1]);
        }
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("SOS contacts")
                .setMessage("SOS texts them where the car is. Without a SIM in the stereo it calls the first one through your phone instead.")
                .setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        for (int i = 0; i < 2; i++) {
                            String n = f[i * 2 + 1].getText().toString().replaceAll("[^0-9+]", "");
                            String name = f[i * 2].getText().toString().trim().replace('|', ' ');
                            p.put("cabSos" + (i + 1), n.isEmpty() ? "" : (name.isEmpty() ? n : name) + "|" + n);
                        }
                        if (!canText(a))
                            a.requestPermissions(new String[]{android.Manifest.permission.SEND_SMS}, 6);
                        if (done != null) done.run();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    static boolean canText(Context c) {
        return c.checkCallingOrSelfPermission(android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean simReady(Context c) {
        try {
            android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
            return tm != null && tm.getSimState() == android.telephony.TelephonyManager.SIM_STATE_READY;
        } catch (Exception e) {
            return false;
        }
    }

    /** Five seconds to cancel (a mistaken tap), then text both contacts, or call the first. */
    static void sos(final HomeActivity a) {
        final Prefs p = a.prefs;
        final String[] c1 = contact(p, 1), c2 = contact(p, 2);
        if (c1 == null && c2 == null) {
            new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("No SOS contacts yet")
                    .setMessage("Add two people to text in an emergency.")
                    .setPositiveButton("Add them", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            editContacts(a, null);
                        }
                    })
                    .setNeutralButton("Call 112", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            a.call("112");
                        }
                    }).setNegativeButton("Close", null).show();
            return;
        }
        final Dialog d = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setBackgroundColor(0xF2120808);
        final TextView info = Ui.text(a, 26, 0xFFFFFFFF, "sans-serif-medium", Gravity.CENTER);
        info.setPadding(Ui.px(40), 0, Ui.px(40), Ui.px(10));
        col.addView(info);
        final TextView count = Ui.text(a, 96, 0xFFFF6A5A, "sans-serif-light", Gravity.CENTER);
        col.addView(count);
        final LinearLayout extra = new LinearLayout(a);
        extra.setGravity(Gravity.CENTER);
        col.addView(extra);
        final TextView cancel = button(a, "Cancel", 0xFF33363C);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.px(260), Ui.px(76));
        bp.topMargin = Ui.px(20);
        bp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(cancel, bp);
        d.setContentView(col);
        final String who = (c1 != null ? c1[0] : "") + (c1 != null && c2 != null ? " and " : "") + (c2 != null ? c2[0] : "");
        info.setText("Sending SOS with your location to " + who);
        final int[] left = {5};
        count.setText("5");
        final Runnable send = new Runnable() {
            @Override
            public void run() {
                count.setText("");
                cancel.setText("Close");
                double lat = Drive.latNow, lon = Drive.lonNow;
                final String msg = sosText(p, lat, lon, System.currentTimeMillis());
                List<String> to = new ArrayList<>();
                if (c1 != null) to.add(c1[1]);
                if (c2 != null) to.add(c2[1]);
                boolean sent = false;
                if (canText(a) && simReady(a)) {
                    try {
                        android.telephony.SmsManager sm = android.telephony.SmsManager.getDefault();
                        for (String n : to) sm.sendMultipartTextMessage(n, null, sm.divideMessage(msg), null, null);
                        sent = true;
                    } catch (Exception ignored) {
                    }
                }
                if (sent) {
                    info.setText("SOS sent to " + who + ".\n\n" + msg);
                    return;
                }
                // no SIM in the stereo: call through the paired phone, and a code to send the text from it
                info.setText("The stereo can't send texts (no SIM), so it's calling " + (c1 != null ? c1[0] : c2[0])
                        + " through your phone.\nScan the code with your phone to send the SOS text.");
                a.call(c1 != null ? c1[1] : c2[1]);
                ImageView qr = Qr.view(a, "SOS text", "SMSTO:" + to.get(0) + ":" + msg, "Scan to send the SOS text from your phone.", Ui.px(220));
                extra.addView(qr, new LinearLayout.LayoutParams(Ui.px(220), Ui.px(220)));
            }
        };
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!d.isShowing()) return;
                left[0]--;
                if (left[0] <= 0) send.run();
                else {
                    count.setText(String.valueOf(left[0]));
                    count.postDelayed(this, 1000);
                }
            }
        };
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                count.removeCallbacks(tick);
                d.dismiss();
            }
        });
        d.show();
        count.postDelayed(tick, 1000);
    }

    // ================================================================== settings

    static void rows(final HomeActivity a, List<SettingsView.Row> r) {
        final Prefs p = a.prefs;
        final String g = "Cab";
        if (!Features.on(p, "cab")) {
            r.add(new SettingsView.Row(g, "Cab driver tools are off", "switch them on above", null));
            return;
        }
        final Runnable redo = new Runnable() {
            @Override
            public void run() {
                if (a.settingsView != null) a.settingsView.refresh();
            }
        };
        r.add(new SettingsView.Row(g, "Cab box", "long-press any box on the dashboard and pick Cab driver", null));
        if (Features.on(p, "cabTarget")) {
            r.add(new SettingsView.Row(g, "Daily target", rs(p.num("cabTargetRs", 2000)) + " net", new Runnable() {
                @Override
                public void run() {
                    ask(a, "Daily target (₹ net)", String.valueOf(p.num("cabTargetRs", 2000)), "cabTargetRs", redo);
                }
            }));
            r.add(new SettingsView.Row(g, "Break reminder after", p.num("cabDutyH", 10) + " hours on duty", new Runnable() {
                @Override
                public void run() {
                    int[] steps = {8, 9, 10, 11, 12, 0};
                    int cur = p.num("cabDutyH", 10), next = 10;
                    for (int k = 0; k < steps.length; k++) if (steps[k] == cur) next = steps[(k + 1) % steps.length];
                    p.put("cabDutyH", String.valueOf(next));
                    redo.run();
                }
            }));
        }
        if (Features.on(p, "cabMeter")) {
            final Rates rt = rates(p);
            r.add(new SettingsView.Row(g, "Fare meter rates", rs(rt.base) + " for " + Fuel.fmt(rt.baseKm) + " km, " + rs(rt.perKm) + "/km, waiting "
                    + rs(rt.waitPerMin) + "/min after " + Math.round(rt.freeWait) + " min, night +" + Math.round(rt.nightPct) + "%", new Runnable() {
                @Override
                public void run() {
                    meterRates(a, redo);
                }
            }));
        }
        if (Features.on(p, "cabMeter") || Features.on(p, "cabEarnings")) {
            String upi = p.str("upiId", "");
            r.add(new SettingsView.Row(g, "UPI id for fares", upi.isEmpty() ? "not set: the meter can show a QR code to pay you" : upi, new Runnable() {
                @Override
                public void run() {
                    final EditText e = new EditText(a);
                    e.setHint("name@bank");
                    e.setText(p.str("upiId", ""));
                    new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle("Your UPI id").setView(e)
                            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    String v = e.getText().toString().trim();
                                    if (!v.isEmpty() && !Errands.validVpa(v)) {
                                        Toast.makeText(a, "That doesn't look like a UPI id (name@bank)", Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                    p.put("upiId", v);
                                    redo.run();
                                }
                            }).setNegativeButton("Cancel", null).show();
                }
            }));
        }
        if (Features.on(p, "cabQuiet")) {
            r.add(new SettingsView.Row(g, "Quiet ride volume", p.num("cabQuietPct", 30) + "% of full", new Runnable() {
                @Override
                public void run() {
                    int v = p.num("cabQuietPct", 30);
                    p.put("cabQuietPct", String.valueOf(v >= 50 ? 10 : v + 10));
                    redo.run();
                }
            }));
            r.add(new SettingsView.Row(g, "Quiet ride with the meter", "on".equals(p.str("cabQuietWithMeter", "on"))
                    ? "starts and ends with the fare meter" : "only from the Cab menu", new Runnable() {
                @Override
                public void run() {
                    p.put("cabQuietWithMeter", "on".equals(p.str("cabQuietWithMeter", "on")) ? "off" : "on");
                    redo.run();
                }
            }));
        }
        if (Features.on(p, "cabSos")) {
            String[] c1 = contact(p, 1), c2 = contact(p, 2);
            r.add(new SettingsView.Row(g, "SOS contacts", c1 == null && c2 == null ? "not set"
                    : (c1 != null ? c1[0] : "") + (c1 != null && c2 != null ? ", " : "") + (c2 != null ? c2[0] : ""), new Runnable() {
                @Override
                public void run() {
                    editContacts(a, redo);
                }
            }));
        }
        if (Features.on(p, "cabSos") || Features.on(p, "cabMeter")) {
            r.add(new SettingsView.Row(g, "Vehicle number", p.str("cabVehicle", "").isEmpty() ? "not set (goes in the SOS text)" : p.str("cabVehicle", ""), new Runnable() {
                @Override
                public void run() {
                    final EditText e = new EditText(a);
                    e.setHint("e.g. DL 1R AB 1234");
                    e.setText(p.str("cabVehicle", ""));
                    new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle("Vehicle number").setView(e)
                            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    p.put("cabVehicle", e.getText().toString().trim().toUpperCase(Locale.US));
                                    redo.run();
                                }
                            }).setNegativeButton("Cancel", null).show();
                }
            }));
        }
        if (Features.on(p, "cabPapers")) {
            long now = System.currentTimeMillis();
            int set = 0;
            String soonest = "";
            long best = Long.MAX_VALUE;
            for (int i = 0; i < PAPERS.length; i++) {
                long d = daysTo(p.str(paperKey(PAPERS[i]), ""), now);
                if (d == Long.MIN_VALUE) continue;
                set++;
                if (d < best) {
                    best = d;
                    soonest = dueText(PAPER_NAMES[i], d);
                }
            }
            r.add(new SettingsView.Row(g, "Papers due dates", set == 0 ? "permit, fitness, badge, road tax, insurance, PUC: not set" : soonest + "  (" + set + " of 6 set)",
                    new Runnable() {
                        @Override
                        public void run() {
                            papers(a, redo);
                        }
                    }));
        }
        if (Features.on(p, "cabPhrases")) {
            int li = java.util.Arrays.asList(LANGS).indexOf(p.str("cabLang", "hi"));
            r.add(new SettingsView.Row(g, "Phrases in", LANG_NAMES[Math.max(0, li)], new Runnable() {
                @Override
                public void run() {
                    int i = java.util.Arrays.asList(LANGS).indexOf(p.str("cabLang", "hi"));
                    p.put("cabLang", LANGS[(Math.max(0, i) + 1) % LANGS.length]);
                    redo.run();
                }
            }));
        }
        if (Features.on(p, "cabEarnings")) {
            r.add(new SettingsView.Row(g, "Clear the earnings log", log(p).size() + " entries", new Runnable() {
                @Override
                public void run() {
                    new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                            .setMessage("Delete every ride and cost in the log?")
                            .setPositiveButton("Delete all", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    save(p, new ArrayList<Entry>());
                                    redo.run();
                                }
                            }).setNegativeButton("Keep", null).show();
                }
            }));
        }
    }

    private static void ask(final HomeActivity a, String title, String value, final String key, final Runnable done) {
        final EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(value);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title).setView(e)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        try {
                            a.prefs.put(key, String.valueOf(Integer.parseInt(e.getText().toString().trim())));
                            done.run();
                        } catch (Exception ex) {
                            Toast.makeText(a, "Enter a number", Toast.LENGTH_SHORT).show();
                        }
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private static void meterRates(final HomeActivity a, final Runnable done) {
        final Prefs p = a.prefs;
        Rates r = rates(p);
        LinearLayout box = box(a);
        final String[] keys = {"cabBase", "cabBaseKm", "cabPerKm", "cabWaitRs", "cabFreeWait", "cabNightPct"};
        String[] labels = {"Base fare (₹)", "Km included in the base fare", "₹ a km after that", "Waiting ₹ a minute",
                "Free waiting minutes", "Night charge, 11 pm to 5 am (%)"};
        double[] vals = {r.base, r.baseKm, r.perKm, r.waitPerMin, r.freeWait, r.nightPct};
        final EditText[] f = new EditText[keys.length];
        for (int i = 0; i < keys.length; i++) {
            TextView t = new TextView(a);
            t.setText(labels[i]);
            box.addView(t);
            f[i] = number(box, "", Fuel.fmt(vals[i]).replace(".0", ""));
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(box);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Fare meter rates").setView(sv)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        for (int i = 0; i < keys.length; i++) {
                            double v = num(f[i]);
                            if (v >= 0) p.put(keys[i], String.valueOf(v));
                        }
                        done.run();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private Cab() {
    }
}
