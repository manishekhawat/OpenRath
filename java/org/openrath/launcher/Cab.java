package org.openrath.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.text.InputType;
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
        return Money.shown("₹") + Money.group(Math.round(v));
    }

    static String hm(long ms) {
        long m = Math.max(0, ms / 60000);
        return m < 60 ? Hindi.f("%1$s min", m) : Hindi.f("%1$s h %2$s min", m / 60, m % 60);
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

    /** dueText() for the screen, in the owner's language (dueText itself is also spoken). */
    static String dueShow(String name, long days) {
        String n = Hindi.t(name);
        if (days < 0) return -days == 1 ? Hindi.f("%1$s expired yesterday", n) : Hindi.f("%1$s expired %2$s days ago", n, -days);
        if (days == 0) return Hindi.f("%1$s expires today", n);
        return days == 1 ? Hindi.f("%1$s expires in 1 day", n) : Hindi.f("%1$s expires in %2$s days", n, days);
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
        } catch (Exception e) {
            Diag.note("cab log read", e);
        }
        cache = out;
        cacheSrc = src;
        return out;
    }

    static void save(Prefs p, List<Entry> l) {
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
        } catch (Exception e) {
            Diag.note("cab log save", e);
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
            Toast.makeText(a, Hindi.f("Target reached: %1$s today. Well done!", rs(before.net() + rs)), Toast.LENGTH_LONG).show();
            if (!quiet) a.speak(Money.say(t));
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
        String m = at == 0 ? "" : CabMeter.fareNow(p) + "/" + Math.round(CabMeter.meterKm(p) * 10) + "/" + p.longNum("cabMeterWait", 0) / 60000;
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
                Toast.makeText(c, Hindi.f("You've been on duty for %1$s hours. Time for a proper break.", h), Toast.LENGTH_LONG).show();
                if (!quiet) Speak.say(c, t, false);
            }
        }
        // papers: once a day, anything due within 15 days
        if (on(p, "cabPapers")) {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(now));
            if (!today.equals(p.str("cabPapersDay", "")) && now - Drive.fixAt < 600000) {
                p.put("cabPapersDay", today);
                List<String> due = new ArrayList<>(), shown = new ArrayList<>();
                for (int i = 0; i < PAPERS.length; i++) {
                    long d = daysTo(p.str(paperKey(PAPERS[i]), ""), now);
                    if (d != Long.MIN_VALUE && d <= 15) {
                        due.add(dueText(PAPER_NAMES[i], d));
                        shown.add(dueShow(PAPER_NAMES[i], d));
                    }
                }
                if (!due.isEmpty()) {
                    String t = android.text.TextUtils.join(". ", due) + ".";
                    Toast.makeText(c, android.text.TextUtils.join(". ", shown) + ".", Toast.LENGTH_LONG).show();
                    if (!quiet) Speak.say(c, t, false);
                }
            }
        }
    }

    static long waitMs = -1;

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
            Toast.makeText(a, Hindi.t("Turn on Cab driver tools in Settings > Cab driver"), Toast.LENGTH_LONG).show();
            return;
        }
        final List<String> names = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        if (on(p, "cabMeter")) {
            boolean running = p.longNum("cabMeterAt", 0) > 0;
            names.add(running ? Hindi.f("Meter: %1$s  (show it)", rs(CabMeter.fareNow(p))) : Hindi.t("Start the fare meter"));
            acts.add(new Runnable() {
                @Override
                public void run() {
                    if (p.longNum("cabMeterAt", 0) == 0) CabMeter.startMeter(a);
                    CabMeter.showMeter(a);
                }
            });
        }
        if (on(p, "cabEarnings")) {
            names.add(Hindi.t("Add a ride"));
            acts.add(new Runnable() {
                @Override
                public void run() {
                    CabEarnings.rideDialog(a, 0, 0, 0, 0, null);
                }
            });
            names.add(Hindi.t("Add a cost (fuel, toll, food…)"));
            acts.add(new Runnable() {
                @Override
                public void run() {
                    CabEarnings.costDialog(a);
                }
            });
            names.add(Hindi.t("Earnings: today and this week"));
            acts.add(new Runnable() {
                @Override
                public void run() {
                    CabEarnings.earnings(a);
                }
            });
        }
        if (on(p, "cabApps")) {
            names.add(Hindi.t("Ride apps"));
            acts.add(new Runnable() {
                @Override
                public void run() {
                    rideApps(a);
                }
            });
        }
        if (on(p, "cabQuiet")) {
            names.add(Hindi.t(quiet ? "Quiet ride: on (tap to end)" : "Passenger in: quiet ride"));
            acts.add(new Runnable() {
                @Override
                public void run() {
                    setQuiet(a, !quiet);
                }
            });
        }
        if (on(p, "cabPhrases")) {
            names.add(Hindi.t("Phrases for the passenger"));
            acts.add(new Runnable() {
                @Override
                public void run() {
                    phrases(a);
                }
            });
        }
        if (on(p, "cabSpots")) {
            names.add(Hindi.t("Pickup hotspots now"));
            acts.add(new Runnable() {
                @Override
                public void run() {
                    hotspotList(a);
                }
            });
        }
        if (on(p, "cabCng")) {
            names.add(Hindi.t("CNG stations near here"));
            acts.add(new Runnable() {
                @Override
                public void run() {
                    findCng(a);
                }
            });
            names.add(Hindi.t("CNG fill-ups"));
            acts.add(new Runnable() {
                @Override
                public void run() {
                    cngLog(a);
                }
            });
        }
        if (on(p, "cabNotes")) {
            names.add(Hindi.t("Ride notes and lost items"));
            acts.add(new Runnable() {
                @Override
                public void run() {
                    notes(a);
                }
            });
        }
        if (on(p, "cabPapers")) {
            names.add(Hindi.t("Papers due"));
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
            names.add(onDuty ? Hindi.f("End duty (on for %1$s)", hm(System.currentTimeMillis() - duty)) : Hindi.t("Start duty now"));
            acts.add(new Runnable() {
                @Override
                public void run() {
                    long d = p.longNum("cabDutyAt", 0);
                    if (d >= dayStart(System.currentTimeMillis())) {
                        Toast.makeText(a, Hindi.f("Duty ended after %1$s", hm(System.currentTimeMillis() - d)), Toast.LENGTH_LONG).show();
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
            names.add(Hindi.t("SOS: send my location"));
            acts.add(new Runnable() {
                @Override
                public void run() {
                    CabSos.sos(a);
                }
            });
        }
        if (names.isEmpty()) {
            Toast.makeText(a, Hindi.t("All the cab tools are switched off in Settings > Cab driver"), Toast.LENGTH_LONG).show();
            return;
        }
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title(p))
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        acts.get(w).run();
                        a.hub.notifyIfChanged(true);
                    }
                }).setNegativeButton(Hindi.t("Close"), null).show();
    }

    private static String title(Prefs p) {
        long now = System.currentTimeMillis();
        Sum s = sum(log(p), dayStart(now), now + 1);
        return s.rides == 1 ? Hindi.f("Cab  ·  today %1$s net, 1 ride", rs(s.net())) : Hindi.f("Cab  ·  today %1$s net, %2$s rides", rs(s.net()), s.rides);
    }

    // ================================================================== dialog helpers

    static LinearLayout box(Context a) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.px(20), Ui.px(8), Ui.px(20), 0);
        return box;
    }

    static EditText number(LinearLayout box, String hint, String value) {
        EditText e = new EditText(box.getContext());
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (value != null) e.setText(value);
        box.addView(e);
        return e;
    }

    static double num(EditText e) {
        try {
            return Double.parseDouble(e.getText().toString().trim());
        } catch (Exception ex) {
            return -1;
        }
    }

    // ================================================================== ride apps

    /** The ride apps on the unit: {name, package}, the last used first. */
    static List<String[]> installedRideApps(Context c, Prefs p) {
        List<String[]> out = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        List<String> seen = new ArrayList<>();
        for (String[] r : RIDE_APPS) {
            if (Compat.launch(pm, r[1]) != null) {
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
        } catch (Exception e) {
            Diag.note("ride app list", e);
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
            Toast.makeText(a, Hindi.t("No ride apps found. Install Uber Driver, Ola Driver, Rapido Captain or another from the Play Store."), Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[apps.size()];
        for (int i = 0; i < names.length; i++) names[i] = apps.get(i)[0];
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t("Ride apps"))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        openApp(a, apps.get(w)[1]);
                    }
                }).setNegativeButton(Hindi.t("Close"), null).show();
    }

    static void openApp(HomeActivity a, String pkg) {
        Intent i = Compat.launch(a.getPackageManager(), pkg);
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
        } catch (Exception e) {
            Diag.note("cab quiet volume", e);
        }
        quiet = yes;
        version++;
        Toast.makeText(c, Hindi.t(yes ? "Quiet ride: music down, messages not read aloud" : "Quiet ride ended"), Toast.LENGTH_SHORT).show();
    }

    // ================================================================== phrases

    static void phrases(final HomeActivity a) {
        final Prefs p = a.prefs;
        final String lang = p.str("cabLang", "hi");
        final List<String> list = new ArrayList<>();
        for (int i = 0; i < PHRASES.length; i++) list.add(phrase(i, lang));
        for (String s : p.str("cabOwnPhrases", "").split("\n")) if (!s.trim().isEmpty()) list.add(s.trim());
        int li = java.util.Arrays.asList(LANGS).indexOf(lang);
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.f("Say to the passenger (%1$s)", Hindi.t(LANG_NAMES[Math.max(0, li)])))
                .setItems(list.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String t = list.get(w);
                        boolean own = w >= PHRASES.length;
                        Locale loc = own ? Locale.getDefault() : "en".equals(lang) ? new Locale("en", "IN") : new Locale(lang, "IN");
                        if (!own || !t.matches("[\\x00-\\x7F]*")) loc = own ? guess(t) : loc;
                        if (!Speak.sayIn(a, t, loc)) {
                            Toast.makeText(a, Hindi.f("No %1$s voice on the unit, so here it is in English", loc.getDisplayLanguage(Locale.ENGLISH)), Toast.LENGTH_LONG).show();
                            if (!own) Speak.say(a, PHRASES[w][0], true);
                        }
                        Toast.makeText(a, t, Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton(Hindi.t("Language"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                                .setTitle(Hindi.t("Phrases in"))
                                .setItems(tr(LANG_NAMES), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d2, int w2) {
                                        p.put("cabLang", LANGS[w2]);
                                        phrases(a);
                                    }
                                }).show();
                    }
                })
                .setPositiveButton(Hindi.t("Add your own"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        final EditText e = new EditText(a);
                        e.setHint(Hindi.t("Type it in any language"));
                        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                                .setTitle(Hindi.t("Your own phrase")).setView(e)
                                .setPositiveButton(Hindi.t("Save"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d2, int w2) {
                                        String s = e.getText().toString().replace('\n', ' ').trim();
                                        if (s.isEmpty()) return;
                                        String old = p.str("cabOwnPhrases", "");
                                        p.put("cabOwnPhrases", old.isEmpty() ? s : old + "\n" + s);
                                        phrases(a);
                                    }
                                }).setNegativeButton(Hindi.t("Cancel"), null).show();
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
            new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(Hindi.t("Pickup hotspots"))
                    .setMessage(Hindi.t("Not enough rides around this hour yet. Each ride you log (or meter) remembers where it started; "
                            + "places with two or more rides around this time of day show here."))
                    .setPositiveButton(Hindi.t("OK"), null).show();
            return;
        }
        String[] rows = new String[spots.size()];
        for (int i = 0; i < rows.length; i++) {
            double[] s = spots.get(i);
            String far = Drive.latNow == 0 ? "" : Hindi.f("%1$s km away  ·  ",
                    String.format(Locale.US, "%.1f", Geo.dist(Drive.latNow, Drive.lonNow, s[0], s[1]) / 1000));
            rows[i] = far + Hindi.f("%1$s rides around %2$s:00   (%3$s)", Math.round(s[2]), hour, String.format(java.util.Locale.US, "%.5f, %.5f", s[0], s[1]));
        }
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t("Where you got rides at this hour (tap to drive there)"))
                .setItems(rows, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        a.navigate(spots.get(w)[0], spots.get(w)[1], "Pickup hotspot");
                    }
                }).setNegativeButton(Hindi.t("Close"), null).show();
    }

    // ================================================================== CNG

    static void findCng(HomeActivity a) {
        double lat = Drive.latNow, lon = Drive.lonNow;
        String q = "geo:" + (lat == 0 ? "0,0" : String.format(Locale.US, "%.5f,%.5f", lat, lon)) + "?q=" + android.net.Uri.encode("CNG station");
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(q)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(a, Hindi.t("Needs a map app (Google Maps, Organic Maps or OsmAnd)"), Toast.LENGTH_LONG).show();
        }
    }

    /** The CNG fills: kept in the fuel log as fills of type CNG, so there is one fuel history. */
    static JSONArray cng(Prefs p) {
        return Fuel.cng(p);
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
        String head = st[1] > 0 ? Hindi.f("CNG  ·  %1$s km/kg average, %2$s last, ₹%3$s a km", Fuel.fmt(st[1]), Fuel.fmt(st[0]), Fuel.fmt(st[2]))
                : Hindi.t(rows.length == 0 ? "No CNG fills yet" : "CNG  ·  km/kg shows after two fills");
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(head)
                .setItems(rows, null)
                .setPositiveButton(Hindi.t("Add a fill"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        addCng(a);
                    }
                })
                // the same fills, with the petrol ones, in the fuel log (where one can be deleted)
                .setNeutralButton(Hindi.t("All fill-ups"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Fuel.history(a, p, new Runnable() {
                            @Override
                            public void run() {
                                a.hub.notifyIfChanged(true);
                            }
                        });
                    }
                }).setNegativeButton(Hindi.t("Close"), null).show();
    }

    static void addCng(final HomeActivity a) {
        final Prefs p = a.prefs;
        LinearLayout box = box(a);
        final EditText kg = number(box, Hindi.t("Kg"), null);
        final EditText rupees = number(box, Hindi.t("Amount paid (₹)"), null);
        final EditText odo = number(box, Hindi.t("Odometer (km)"), String.valueOf(Care.odo(p)));
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t("Add a CNG fill")).setView(box)
                .setPositiveButton(Hindi.t("Save"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        double k = num(kg), r = num(rupees), o = num(odo);
                        if (k <= 0 || o <= 0) {
                            Toast.makeText(a, Hindi.t("Enter the kg and the odometer as numbers"), Toast.LENGTH_LONG).show();
                            return;
                        }
                        try {
                            JSONObject e = new JSONObject();
                            e.put("t", System.currentTimeMillis());
                            e.put("type", Fuel.CNG);
                            e.put("kg", k);
                            e.put("rs", Math.max(0, r));
                            e.put("odo", o);
                            e.put("gps", Drive.odoKm);
                            Fuel.put(p, e);
                        } catch (Exception e) {
                            Diag.note("CNG fill save", e);
                        }
                        // the fill is a cost in the earnings too
                        if (r > 0 && on(p, "cabEarnings")) addCost(p, r, "CNG");
                        a.hub.notifyIfChanged(true);
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
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
        } catch (Exception e) {
            Diag.note("cab note save", e);
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
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t(rows.length == 0 ? "No notes yet" : "Ride notes"))
                .setItems(rows, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        showNote(a, l.length() - 1 - w);
                    }
                })
                .setPositiveButton(Hindi.t("Lost item photo"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        lostItem(a);
                    }
                })
                .setNeutralButton(Hindi.t("Add a note"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        noteText(a, "", null);
                    }
                }).setNegativeButton(Hindi.t("Close"), null).show();
    }

    private static void noteText(final HomeActivity a, String start, final String photo) {
        final EditText e = new EditText(a);
        e.setHint(Hindi.t("Passenger's name, what was left, a phone number…"));
        e.setText(start);
        e.setSelection(start.length());
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t(photo == null ? "Note" : "What was left, and by whom?")).setView(e)
                .setPositiveButton(Hindi.t("Save"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String s = e.getText().toString().trim();
                        if (s.isEmpty() && photo == null) return;
                        addNote(a.prefs, s.isEmpty() ? Hindi.t("Left in the car") : s, photo);
                        Toast.makeText(a, Hindi.t("Saved in Ride notes"), Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    static void lostItem(final HomeActivity a) {
        final File f = Storage.dir("lost-items/" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".jpg");
        boolean shooting = Snap.photo(a, a.prefs, f, new Runnable() {
            @Override
            public void run() {
                Toast.makeText(a, Hindi.t("Photo saved"), Toast.LENGTH_SHORT).show();
            }
        });
        if (!shooting) Toast.makeText(a, Hindi.t("No camera free for a photo, so it's a note only"), Toast.LENGTH_LONG).show();
        else Toast.makeText(a, Hindi.t("Taking a photo with the car's camera…"), Toast.LENGTH_SHORT).show();
        noteText(a, Hindi.t("Left in the car: "), shooting ? f.getPath() : null);
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
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(o.optLong("t"))))
                .setView(box)
                .setPositiveButton(Hindi.t("Close"), null)
                .setNeutralButton(Hindi.t("Delete"), new DialogInterface.OnClickListener() {
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
            rows[i] = d == Long.MIN_VALUE ? Hindi.f("%1$s: tap to set the expiry date", Hindi.t(PAPER_NAMES[i])) : dueShow(PAPER_NAMES[i], d)
                    + "  (" + p.str(paperKey(PAPERS[i]), "") + ")";
        }
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t("Papers (reminded 15 days ahead)"))
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
                }).setNegativeButton(Hindi.t("Close"), null).show();
    }

    /** A list of English words, each in the owner's language, for a dialog. */
    static String[] tr(String[] en) {
        String[] out = new String[en.length];
        for (int i = 0; i < en.length; i++) out[i] = Hindi.t(en[i]);
        return out;
    }

    private Cab() {
    }
}
