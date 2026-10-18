package org.openrath.launcher;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for the parts with no screen: OBD-II and TPMS parsing, mileage, driving score,
 * speed limits, sunset, the 0–100 timer, speed volume, GPX output, voice commands and the
 * mileage coach. Plain Java, no test
 * framework: `python3 build.py test` compiles and runs them; a failure exits non-zero.
 */
public final class Tests {

    private static int run, failed;

    private static void check(String name, boolean ok) {
        run++;
        if (!ok) {
            failed++;
            System.out.println("FAIL  " + name);
        }
    }

    private static void eq(String name, Object want, Object got) {
        check(name + " (want " + want + ", got " + got + ")", want == null ? got == null : want.equals(got));
    }

    private static void near(String name, double want, double got, double tol) {
        check(name + " (want " + want + ", got " + got + ")", Math.abs(want - got) <= tol);
    }

    /** Radio text, szchoiceway, speed correction, projection apps, soundboard, compass and box sizes. */
    private static void radioAndBoxes() {
        RadioText.Found r = RadioText.parse("FM1 98.30", "Radio Mirchi");
        eq("radio fm1", "FM 98.3", r == null ? null : r.big());
        eq("radio station", "Radio Mirchi", r == null ? null : r.station);
        r = RadioText.parse("Radio City - 91.1 MHz", null);
        eq("radio mhz", "FM 91.1", r == null ? null : r.big());
        eq("radio name before", "Radio City", r == null ? null : r.station);
        r = RadioText.parse("AM 1040", "");
        eq("radio am", "AM 1040", r == null ? null : r.big());
        r = RadioText.parse("594 kHz", "AIR");
        eq("radio khz", "AM 594", r == null ? null : r.big());
        r = RadioText.parse("FM 93,5", "");
        eq("radio comma", "FM 93.5", r == null ? null : r.big());
        check("radio not a year", RadioText.parse("Summer of 2019", "Bryan Adams") == null);
        check("radio no number", RadioText.parse("Tum Hi Ho", "Arijit Singh") == null);
        check("radio empty", RadioText.parse(null, null) == null);

        String[] sz = SzUnit.fromTitle("FM1 90.10");
        eq("sz band", "FM1", sz == null ? null : sz[0]);
        eq("sz freq", "90.1", sz == null ? null : sz[1]);
        sz = SzUnit.fromTitle("AM 1040");
        eq("sz am", "1040", sz == null ? null : sz[1]);
        check("sz music title", SzUnit.fromTitle("Kesariya Arijit") == null);
        check("sz empty", SzUnit.fromTitle("") == null);
        byte[] f = SzUnit.tuneFrame("FM1", 98.3);
        eq("sz frame length", 19, f.length);
        eq("sz frame prefix", "0d08", String.format("%02x%02x", f[0], f[1]));
        eq("sz frame head", "5aa50d91", String.format("%02x%02x%02x%02x", f[2], f[3] & 0xff, f[4], f[5] & 0xff));
        eq("sz frame band", 1, (int) f[6]);
        eq("sz frame bank", "01", new String(new byte[]{f[7], f[8]}));
        eq("sz frame freq", "98.3", new String(f, f.length - 5, 4));
        int sum = 0;
        for (int i = 2; i < f.length - 1; i++) sum += f[i] & 0xff;
        eq("sz frame checksum", sum & 0xff, f[f.length - 1] & 0xff);
        eq("sz frame am band", 4, (int) SzUnit.tuneFrame("AM", 1040)[6]);
        eq("sz frame fm2 bank", "02", new String(SzUnit.tuneFrame("FM2", 91.1), 7, 2));
        eq("sz make", "szchoiceway", Stereo.from(java.util.Arrays.asList("com.szchoiceway.eventcenter")));

        eq("speed fix off", 100f, Drive.corrected(100, 0));
        eq("speed fix +5", 105f, Drive.corrected(100, 5));
        eq("speed fix -3", 97f, Drive.corrected(100, -3));
        eq("speed fix capped", 110f, Drive.corrected(100, 40));

        check("projection zlink", Projection.looksLike("com.zjinnova.zlink", "ZLink"));
        check("projection autokit label", Projection.looksLike("com.xyz.box", "AutoKit"));
        check("projection carplay", Projection.looksLike("cn.manstep.phonemirrorBox", "CarPlay"));
        check("projection not gearhead", !Projection.looksLike("com.google.android.projection.gearhead", "Android Auto"));
        check("projection not maps", !Projection.looksLike("com.google.android.apps.maps", "Maps"));

        for (int i = 0; i < Soundboard.PADS.length; i++) {
            short[] snd = Soundboard.make(i);
            int peak = 0;
            for (short v : snd) peak = Math.max(peak, Math.abs(v));
            check("soundboard " + Soundboard.PADS[i] + " length", snd.length > Soundboard.RATE / 4 && snd.length < Soundboard.RATE * 3);
            check("soundboard " + Soundboard.PADS[i] + " audible", peak > 3000);
            check("soundboard " + Soundboard.PADS[i] + " starts quiet", Math.abs(snd[0]) < 2000);
        }
        eq("compass north", "N", Cards.CompassCard.point(2));
        eq("compass north wrap", "N", Cards.CompassCard.point(355));
        eq("compass east", "E", Cards.CompassCard.point(90));
        eq("compass south-west", "SW", Cards.CompassCard.point(225));
        float[] z = Dashboard.clampSizes(0.1f, 0.9f, 0.5f, 0.95f);
        check("sizes column kept", z[0] == 0.25f && z[3] == 0.85f);
        check("sizes left column fits", z[1] + z[2] <= 0.9001f && z[2] >= 0.1f);
        check("contents and names match", Prefs.CONTENTS.length == Prefs.CONTENT_NAMES.length);
    }

    /** Settings rows: state on the right, recently used, setup offered when a feature is switched on. */
    private static void settingsLook() {
        String[] v = SettingsView.split("₹100");
        check("split short", v[0].equals("₹100") && v[1].isEmpty());
        v = SettingsView.split("not OpenRath; tap to make the unit start in it");
        check("split state and hint", v[0].equals("not OpenRath") && v[1].equals("tap to make the unit start in it"));
        v = SettingsView.split("send a photo if something doesn't work");
        check("split long is a hint", v[0].isEmpty() && v[1].startsWith("send a photo"));
        check("split empty", SettingsView.split(null)[0].isEmpty());
        String r = SettingsView.remember("", "Home app");
        r = SettingsView.remember(r, "Speed correction");
        r = SettingsView.remember(r, "Home app");
        eq("recent newest first, no repeats", "Home app\nSpeed correction", r);
        for (String t : new String[]{"a", "b", "c", "d", "e"}) r = SettingsView.remember(r, t);
        eq("recent keeps four", 4, r.split("\n").length);
        check("setup arrows asked", Extras.needs("arrows", false, false, false, false, true) != null);
        check("setup arrows ready", Extras.needs("arrows", true, false, false, false, true) == null);
        check("setup obd asked", Extras.needs("obd", true, true, false, true, true) != null);
        check("setup tyres asked", Extras.needs("tpms", true, true, true, false, true) != null);
        check("setup projection asked", Extras.needs("projection", true, true, true, true, false) != null);
        check("setup weather needs nothing", Extras.needs("weather", false, false, false, false, false) == null);
        for (String id : new String[]{"arrows", "cam360Turn", "lightsReminder", "obd", "tpms", "projection"})
            check("setup feature exists " + id, Features.get(id) != null);
    }

    public static void main(String[] args) {
        levels();
        hindi();
        fleets();
        obd();
        tpms();
        fuel();
        score();
        limits();
        sunset();
        timer();
        volume();
        gpx();
        voice();
        news();
        lightTimes();
        radioInfo();
        offlineWeather();
        scanList();
        cab();
        callList();
        speechParts();
        coach();
        batch2();
        layouts();
        batch3();
        floaters();
        fixes119();
        batch4();
        batch5();
        generic();
        radioAndBoxes();
        settingsLook();
        bootArt();
        System.out.println(run + " checks, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void hindi() {
        java.util.Map<String, String> m = Hindi.pair(new String[]{"Search", "Radio", "Empty"}, new String[]{"खोज", "Radio", ""});
        eq("hindi: one word", 1, m.size());
        eq("hindi: translated", "खोज", Hindi.t(m, true, "Search"));
        eq("hindi: missing stays English", "Radio", Hindi.t(m, true, "Radio"));
        eq("hindi: off stays English", "Search", Hindi.t(m, false, "Search"));
        eq("hindi: null", null, Hindi.t(m, true, null));
        eq("hindi: format without a translation", "Toll in 2 km", Hindi.f("Toll in %1$s km", 2));
        java.io.File en = new java.io.File("res/values/ui.xml"), hi = new java.io.File("res/values-hi/ui.xml");
        if (en.exists() && hi.exists()) {
            try {
                java.util.Map<String, String> e = new java.util.HashMap<>(), h = new java.util.HashMap<>();
                for (String name : Hindi.ARRAYS) {
                    String file = name.equals("ui_strings") ? "ui" : name;
                    java.io.File ef = new java.io.File("res/values/" + file + ".xml"), hf = new java.io.File("res/values-hi/" + file + ".xml");
                    if (!ef.exists()) continue;
                    java.util.Map<String, String> one = strings(ef);
                    for (String k : one.keySet()) check("hindi: " + k + " named once", !e.containsKey(k));
                    e.putAll(one);
                    String body = new String(java.nio.file.Files.readAllBytes(ef.toPath()), "UTF-8");
                    check("hindi: " + file + ".xml has its list " + name, body.contains("<string-array name=\"" + name + "\">"));
                    if (hf.exists()) h.putAll(strings(hf));
                }
                java.util.Map<String, String> all = new java.util.HashMap<>();
                for (java.util.Map.Entry<String, String> x : e.entrySet()) if (h.containsKey(x.getKey())) all.put(x.getValue(), h.get(x.getKey()));
                check("hindi: the files have the settings", all.size() > 300);
                check("hindi: every Hindi name is an English one", e.keySet().containsAll(h.keySet()));
                for (Features.F x : Features.ALL) check("hindi: feature " + x.id, all.containsKey(x.name) || x.level == Features.DEV);
                for (SettingsView.Section sec : SettingsView.SECTIONS) check("hindi: section " + sec.name, all.containsKey(sec.name) && all.containsKey(sec.about));
                check("hindi: back button keeps its spaces", "‹  Back to the dashboard".equals(e.get("ui_back_to_the_dashboard")));
            } catch (java.io.IOException x) {
                check("hindi: files read", false);
            }
        }
    }

    /** name to text from an Android strings file, unescaped the way aapt2 does it. */
    private static java.util.Map<String, String> strings(java.io.File f) throws java.io.IOException {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        java.util.regex.Matcher x = java.util.regex.Pattern.compile("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>")
                .matcher(new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8"));
        while (x.find()) {
            String v = x.group(2);
            if (v.length() > 1 && v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length() - 1);
            v = v.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < v.length(); i++) {
                char c = v.charAt(i);
                if (c == '\\' && i + 1 < v.length()) {
                    char n = v.charAt(++i);
                    b.append(n == 'n' ? '\n' : n);
                } else b.append(c);
            }
            m.put(x.group(1), b.toString());
        }
        return m;
    }

    private static void fleets() {
        eq("managed: ids", "backup|speedAlerts|reports", String.join("|", Managed.ids(" backup, speedAlerts,reports ")));
        eq("managed: no ids", 0, Managed.ids("").length);
        eq("managed: null ids", 0, Managed.ids(null).length);
        check("reports: https only", Reports.sendable("https://fleet.example/r") && !Reports.sendable("http://fleet.example/r"));
        check("reports: empty address", !Reports.sendable("") && !Reports.sendable("https://") && !Reports.sendable(null));
        java.util.Map<String, Integer> m = new java.util.TreeMap<>();
        m.put("Weather", 2);
        m.put("app start", 5);
        m.put("Backup", 2);
        eq("reports: most used first", "app start: 5\nBackup: 2\nWeather: 2\n", Reports.format(m));
        Features.F r = Features.get("reports");
        check("reports: off by default", r != null && !r.def);
        check("reports: no level turns it on", !Features.onAt(r, Features.SIMPLE, false) && !Features.onAt(r, Features.CUSTOM, false));
    }

    private static void levels() {
        int s = 0, f = 0, c = 0, d = 0;
        for (Features.F x : Features.ALL) {
            if (x.level == Features.SIMPLE) s++;
            else if (x.level == Features.FULL) f++;
            else if (x.level == Features.CUSTOM) c++;
            else d++;
        }
        eq("levels: simple count", 15, s);
        eq("levels: custom count", 8, c);
        eq("levels: dev count", 2, d);
        check("levels: full has the most", f > s && f > c);
        eq("levels: unknown name is custom", Features.CUSTOM, Features.levelOf("max"));
        for (String[] pp : Features.PARTS) {
            check("merged: " + pp[0] + " has no switch of its own", Features.get(pp[0]) == Features.get(pp[1]));
            check("merged: " + pp[1] + " has a switch", Features.get(pp[1]) != null && pp[1].equals(Features.get(pp[1]).id));
        }
        eq("merged: plain id is itself", "weather", Features.parent("weather"));
        eq("levels: simple by name", Features.SIMPLE, Features.levelOf("simple"));
        Features.F weather = Features.get("weather"), score = Features.get("score"), eqF = Features.get("eq"),
                widgets = Features.get("widgets"), maker = Features.get("maker"), cab = Features.get("cab"), obd = Features.get("obd");
        check("levels: weather in simple", Features.inLevel(weather, Features.SIMPLE));
        check("levels: score not in simple", !Features.inLevel(score, Features.SIMPLE));
        check("levels: score in full", Features.inLevel(score, Features.FULL));
        check("levels: eq only in custom", !Features.inLevel(eqF, Features.FULL) && Features.inLevel(eqF, Features.CUSTOM));
        check("levels: maker in no level", !Features.inLevel(maker, Features.CUSTOM));
        check("levels: simple turns score off", !Features.onAt(score, Features.SIMPLE, true));
        check("levels: full turns score on", Features.onAt(score, Features.FULL, false));
        check("levels: custom puts widgets back on", Features.onAt(widgets, Features.CUSTOM, false));
        check("levels: eq keeps its default (off)", !Features.onAt(eqF, Features.CUSTOM, true));
        check("levels: maker untouched", Features.onAt(maker, Features.SIMPLE, true) && !Features.onAt(maker, Features.CUSTOM, false));
        check("levels: cab stays as the owner set it", Features.onAt(cab, Features.FULL, true) && !Features.onAt(cab, Features.FULL, false));
        check("levels: obd off in simple", !Features.onAt(obd, Features.SIMPLE, true));
        check("levels: obd not forced on", !Features.onAt(obd, Features.CUSTOM, false));
    }

    static void obd() {
        eq("speed", 60, Obd.speed("410D3C\r\r"));
        eq("speed with spaces and prompt", 60, Obd.speed("41 0D 3C \r>"));
        eq("speed after SEARCHING", 0, Obd.speed("SEARCHING...\r410D00"));
        eq("rpm", 1726, Obd.rpm("410C1AF8"));
        eq("rpm no data", -1, Obd.rpm("NO DATA"));
        eq("coolant", 90, Obd.coolant("4105 82"));
        eq("load", 50, Obd.load("41047F"));
        near("volts", 12.6, Obd.volts("12.6V"), 0.001);
        eq("volts junk", -1f, Obd.volts("?"));
        // non-CAN: three codes per line, zero padding ignored
        eq("dtc non-CAN", Arrays.asList("P0133", "P0420"), Obd.dtcs("43 01 33 04 20 00 00\r", false));
        // CAN single frame: count byte first
        eq("dtc CAN single", Arrays.asList("P0171", "P0300"), Obd.dtcs("43 02 01 71 03 00 00\r", true));
        // CAN multi-frame
        List<String> multi = Obd.dtcs("00A\r0:43040133042\r1:00171030000\r", true);
        check("dtc CAN multi frame has 4 or fewer codes " + multi, multi.size() <= 4 && multi.contains("P0133"));
        eq("dtc letters", Arrays.asList("C0123", "B0456", "U0789"), Obd.dtcs("43 41 23 84 56 C7 89", false));
        eq("dtc none", 0, Obd.dtcs("43 00 00 00 00 00 00", false).size());
        eq("meaning known", "Engine running lean", Obd.meaning("P0171"));
    }

    static void tpms() {
        // flags, then manufacturer data: 00 01 | 80 EA CA 10 8A 78 | 9A B6 03 00 (243354 Pa) | 2C 0A 00 00 (2604 = 26.04 C) | 5B | 00
        byte[] adv = bytes("020106" + "13FF" + "0001" + "80EACA108A78" + "9AB60300" + "2C0A0000" + "5B" + "00");
        Tpms.Tyre t = Tpms.parse(adv);
        check("tpms parsed", t != null);
        if (t != null) {
            eq("tpms id", "80EACA108A78", t.id);
            near("tpms kPa", 243.354, t.kpa, 0.01);
            near("tpms temp", 26.04, t.tempC, 0.01);
            eq("tpms battery", 91, t.battery);
            eq("tpms wheel", 0, Tpms.wheelOf(t.id));
            near("tpms psi", 35.3, Tpms.psi(t.kpa), 0.1);
        }
        eq("tpms other device", null, Tpms.parse(bytes("020106" + "05FF4C000215")));
        eq("tpms truncated", null, Tpms.parse(bytes("020106" + "13FF0001")));
        eq("wheel unknown", -1, Tpms.wheelOf("12AB"));
    }

    static void fuel() {
        // full, partial, full: 500 km on 30 + 10 litres
        Fuel.Stats s = Fuel.compute(new double[]{1000, 1300, 1500}, new double[]{35, 30, 10}, new double[]{3500, 3000, 1000},
                new boolean[]{true, false, true}, new long[]{1, 2, 3});
        near("last km/l", 12.5, s.lastKmpl, 1e-9);
        near("average km/l", 12.5, s.avgKmpl, 1e-9);
        near("rupees per km", 8.0, s.rupeesPerKm, 1e-9);
        eq("fills", 3, s.fills);
        Fuel.Stats one = Fuel.compute(new double[]{1000}, new double[]{35}, new double[]{3500}, new boolean[]{true}, new long[]{1});
        near("one fill-up has no mileage", 0, one.lastKmpl, 0);
        Fuel.Stats back = Fuel.compute(new double[]{1000, 900}, new double[]{35, 30}, new double[]{1, 1}, new boolean[]{true, true}, new long[]{1, 2});
        near("odometer going back is ignored", 0, back.avgKmpl, 0);
    }

    static void score() {
        Drive.todayBrakes = 0;
        Drive.todayAccels = 0;
        Drive.todaySpeedSec = 0;
        eq("perfect", 100, Drive.score());
        Drive.todayBrakes = 2;
        Drive.todayAccels = 3;
        Drive.todaySpeedSec = 90;
        eq("deductions", 100 - 8 - 6 - 3, Drive.score());
        Drive.todayBrakes = 40;
        eq("never below 0", 0, Drive.score());
        Drive.todayBrakes = Drive.todayAccels = 0;
        Drive.todaySpeedSec = 0;
    }

    static void limits() {
        eq("plain", 50, Drive.speed("50"));
        eq("mph", 48, Drive.speed("30 mph"));
        eq("none", 0, Drive.speed("IN:urban"));
        eq("null", 0, Drive.speed(null));
    }

    static void sunset() {
        // New Delhi, 12 Oct 2026: sunrise about 06:20, sunset about 17:55 IST (UTC+5:30)
        long noonUtc = 1791806400000L;            // 2026-10-12 12:00 UTC = 17:30 IST
        check("Delhi 17:30 IST is light", !Care.dark(28.61, 77.21, noonUtc));
        check("Delhi 18:30 IST is dark", Care.dark(28.61, 77.21, noonUtc + 3600000L));
        check("Delhi 07:00 IST is light", !Care.dark(28.61, 77.21, noonUtc - 10 * 3600000L - 30 * 60000L));
        check("Delhi 05:30 IST is dark", Care.dark(28.61, 77.21, noonUtc - 12 * 3600000L));
    }

    static void timer() {
        Tracks.Timer t = new Tracks.Timer();
        long at = 0;
        t.feed(0, at);
        t.feed(0, at += 1000);
        t.feed(0, at += 1000);              // armed
        float done = -1;
        // 0 -> 100 in 10 readings a second apart, 10 km/h a second
        for (int k = 1; k <= 11 && done < 0; k++) done = t.feed(Math.min(110, k * 10 + 5), at += 1000);
        check("timer finished", done > 0);
        near("0-100 time", 9.5 + 0.0, done, 1.0);
        near("0-60 time", 5.5, t.result60, 1.0);
        // backing off cancels the run
        Tracks.Timer u = new Tracks.Timer();
        at = 0;
        u.feed(0, at);
        u.feed(0, at += 1500);
        u.feed(30, at += 1000);
        u.feed(20, at += 1000);
        eq("lifting off cancels", -1f, u.feed(100, at += 1000));
    }

    static void volume() {
        eq("town", 0, Tracks.extraSteps(35, 20));
        eq("just over", 1, Tracks.extraSteps(40, 20));
        eq("highway", 4, Tracks.extraSteps(100, 20));
        eq("capped", 6, Tracks.extraSteps(250, 15));
        eq("off", 0, Tracks.extraSteps(120, 0));
    }

    static void gpx() {
        String p = Tracks.gpxPoint(28.613939, 77.209021, 216, 1791806400000L);
        check("gpx point " + p, p.contains("lat=\"28.613939\"") && p.contains("lon=\"77.209021\"") && p.contains("<time>2026-10-12T12:00:00Z</time>"));
        check("gpx escapes", Tracks.gpxHead("A & B").contains("A &amp; B"));
    }

    private static byte[] bytes(String hex) {
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        return b;
    }

    static void radioInfo() {
        eq("radioinfo 10k", 98.3f, Xt.textMhz("9830"));
        eq("radioinfo khz", 91.1f, Xt.textMhz("91100"));
        eq("radioinfo text", 104.0f, Xt.textMhz("104.0"));
        eq("radioinfo junk", 0f, Xt.textMhz("abc"));
        eq("radioinfo am", 0f, Xt.textMhz("1500"));
        eq("fm 100k", 101.5f, Xt.fmMhz(1015));
    }

    static void lightTimes() {
        check("lights overnight on", Lights.inWindow(23 * 60, 18 * 60 + 30, 6 * 60 + 30));
        check("lights overnight early", Lights.inWindow(5 * 60, 18 * 60 + 30, 6 * 60 + 30));
        check("lights overnight off", !Lights.inWindow(12 * 60, 18 * 60 + 30, 6 * 60 + 30));
        check("lights end excluded", !Lights.inWindow(6 * 60 + 30, 18 * 60 + 30, 6 * 60 + 30));
        check("lights same day", Lights.inWindow(10 * 60, 9 * 60, 17 * 60) && !Lights.inWindow(18 * 60, 9 * 60, 17 * 60));
        check("lights empty window", !Lights.inWindow(10 * 60, 600, 600));
        eq("hhmm", "06:05", Lights.hhmm(365));
        int[] spy = Tw.spyCodes();
        boolean has210 = false;
        for (int c : spy) has210 |= c == 0x0210;
        check("spy codes cover xt's 0x210", has210);
    }

    static void offlineWeather() {
        // 2026-10-14 09:40 UTC is 15:10 in India (+5:30)
        long t = 1791972000000L - 20 * 60000L;
        eq("hour key in India", Weather.hourKey(t, 19800), "2026-10-14T15:00");
        eq("hour key in UTC", Weather.hourKey(t, 0), "2026-10-14T09:00");
        String[] hours = {"2026-10-14T14:00", "2026-10-14T15:00", "2026-10-14T16:00"};
        eq("slot found", Weather.slot(hours, "2026-10-14T15:00"), 1);
        eq("slot past the forecast", Weather.slot(hours, "2026-10-19T15:00"), -1);
        eq("saved note min", Weather.savedNote(0, 25 * 60000L), "Offline · forecast from 25 min ago");
        eq("saved note hours", Weather.savedNote(0, 3 * 3600000L + 5), "Offline · forecast from 3 h ago");
        eq("saved note days", Weather.savedNote(0, 3 * 86400000L), "Offline · forecast from 3 days ago");
    }

    static void scanList() {
        java.util.List<String> stops = new java.util.ArrayList<>(java.util.Arrays.asList("98.3", "101.6", "91.1", "93.5", "98.3"));
        check("scan stops at round trip", Scan.round(stops));
        check("no round trip yet", !Scan.round(stops.subList(0, 4)));
        eq("tuner 10 kHz units", Tuner.stationText(9110), "91.1");
        eq("tuner 100 kHz units", Tuner.stationText(1043), "104.3");
        eq("tuner out of band", Tuner.stationText(500), "");
        eq("stock radio kHz", StockRadio.text("91100"), "91.1");
        eq("stock radio 10 kHz", StockRadio.text(" 10430 "), "104.3");
        eq("stock radio AM", StockRadio.text("531"), "");
        eq("stock radio junk", StockRadio.text("V"), "");
        eq("unit fyt 10 kHz", Unit.mhzText(10090), "100.9");
        eq("unit mtc kHz", Unit.mhzText(99900), "99.9");
        eq("unit mtc Hz", Unit.mhzText(91100000L), "91.1");
        eq("unit AM", Unit.mhzText(531), "");
        for (String sh : Car3D.SHAPES) {
            java.util.List<Car3D.Face> fs = Car3D.build(sh);
            check("3d " + sh + " faces", fs.size() > 60);
            boolean out = true;
            for (Car3D.Face f : fs) if (f.layer == 2 && f.c[1] > 0.5 && f.n[1] < -0.5) out = false;
            check("3d " + sh + " body tops face up", out);
        }
        Car3D.Cam cam = new Car3D.Cam();
        cam.setup(1000, 600);
        float[] mid = cam.project(cam.target);
        check("3d target in the middle", Math.abs(mid[0] - 500) < 0.5 && Math.abs(mid[1] - 300) < 0.5);
        check("3d nose nearer from the front", cam.project(Car3D.v(0, .7, 2))[2] < cam.project(Car3D.v(0, .7, -2))[2] || cam.yaw > Math.PI / 2);
        check("360 app names", View360.looks360("com.ts.avm", "Panoramic") && View360.looks360("com.x.y", "360 Safety") && !View360.looks360("com.tw.radio", "Radio"));
        eq("voice 360", "view360", String.valueOf(Voice.parse("show 360 view")));
        eq("tuner bad saved freq", Tuner.parse("abc"), 98.3f);
        eq("stations sorted, no repeats", Scan.stations(stops).toString(), "[91.1, 93.5, 98.3, 101.6]");
    }

    static void speechParts() {
        java.util.List<String> p = Speak.sentences("One. Two is here! Three?", 12);
        eq("sentences split", p.size(), 3);
        eq("short ones joined", Speak.sentences("One. Two.", 380).size(), 1);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 100; i++) b.append("word ");
        for (String x : Speak.sentences(b.toString(), 60)) check("piece fits", x.length() <= 60);
    }

    static void callList() {
        String h = Calls.remember("", "9876543210|Papa|in|1000000", 20);
        h = Calls.remember(h, "+91 98765 43210||out|1100000", 20);   // same number within 10 min: replaces
        eq("repeat replaces", h.split("\n").length, 1);
        h = Calls.remember(h, "9123456789|Office|out|5000000", 20);
        eq("newest first", h.split("\n")[0].startsWith("9123456789"), true);
        for (int i = 0; i < 30; i++) h = Calls.remember(h, (9000000000L + i) + "||in|" + (6000000L + i * 1000000L), 20);
        eq("kept to 20", h.split("\n").length, 20);
        java.util.List<String[]> all = new java.util.ArrayList<>();
        all.add(new String[]{"9876543210", "", "in", "1000000"});
        all.add(new String[]{"+919876543210", "Papa", "in", "1030000"});   // the same call from the call log
        all.add(new String[]{"9123456789", "", "out", "9000000"});
        java.util.List<String[]> m = Calls.merge(all, 5, null);
        eq("merged once", m.size(), 2);
        eq("merged newest first", m.get(0)[0], "9123456789");
    }

    static void cab() {
        Cab.Rates r = new Cab.Rates();   // ₹50 for 2 km, ₹16 a km, ₹2 a minute after 5 free, night +25%
        eq("fare inside base", Cab.fare(r, 1.5, 0, false), 50L);
        eq("fare 10 km", Cab.fare(r, 10, 0, false), 178L);
        eq("fare with waiting", Cab.fare(r, 10, 12, false), 192L);
        eq("fare at night", Cab.fare(r, 10, 0, true), 223L);
        check("night at 23", Cab.night(23));
        check("night at 4", Cab.night(4));
        check("day at 5", !Cab.night(5));
        long day = Cab.dayStart(System.currentTimeMillis());
        java.util.List<Cab.Entry> l = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Cab.Entry e = new Cab.Entry();
            e.t = day + 9 * 3600000L + i * 600000L;
            e.ride = true;
            e.rs = 200 + i * 100;
            e.app = i == 2 ? "Ola" : "Uber";
            e.pay = "Cash";
            e.lat = 28.6139 + i * 0.0005;
            e.lon = 77.2090;
            l.add(e);
        }
        Cab.Entry cost = new Cab.Entry();
        cost.t = day + 10 * 3600000L;
        cost.rs = 150;
        cost.what = "CNG";
        l.add(cost);
        Cab.Entry old = new Cab.Entry();
        old.t = day - 3600000L;
        old.ride = true;
        old.rs = 999;
        l.add(old);
        Cab.Sum s = Cab.sum(l, day, day + 86400000L);
        eq("rides today", s.rides, 3);
        eq("in today", Math.round(s.in), 900L);
        eq("net today", Math.round(s.net()), 750L);
        eq("top app", String.valueOf(Cab.by(l, day, day + 86400000L, false).get(0)[0]), "Uber");
        check("week starts by today", Cab.weekStart(day + 3600000L) <= day);
        java.util.List<double[]> hot = Cab.hotspots(l, 9, 3);
        eq("one hotspot", hot.size(), 1);
        eq("hotspot rides", Math.round(hot.get(0)[2]), 3L);
        eq("no hotspot at 15:00", Cab.hotspots(l, 15, 3).size(), 0);
        long now = new java.util.GregorianCalendar(2026, 9, 14, 15, 0).getTimeInMillis();
        eq("days to permit", Cab.daysTo("2026-10-24", now), 10L);
        eq("expired", Cab.daysTo("2026-10-12", now), -2L);
        eq("not set", Cab.daysTo("", now), Long.MIN_VALUE);
        eq("due text", Cab.dueText("Permit", 10), "Permit expires in 10 days");
        eq("expired text", Cab.dueText("PUC certificate", -1), "PUC certificate expired yesterday");
        eq("paper key shared", Cab.paperKey("puc"), "care:puc:due");
        eq("phrase hindi", Cab.phrase(1, "hi"), "कृपया अपना OTP बताइए।");
        eq("phrase english", Cab.phrase(6, "en"), "We have reached your destination.");
        for (String[] ph : Cab.PHRASES) eq("phrase has every language", ph.length, Cab.LANGS.length);
        eq("guess tamil", Cab.guess("நன்றி").getLanguage(), "ta");
        eq("guess hindi", Cab.guess("धन्यवाद").getLanguage(), "hi");
        eq("voice meter start", String.valueOf(Voice.parse("start the meter")), "meter:start");
        eq("voice meter stop", String.valueOf(Voice.parse("meter band karo")), "meter:stop");
        eq("voice earnings", String.valueOf(Voice.parse("aaj kitna kamaya")), "earnings");
        eq("kilometre is not the meter", String.valueOf(Voice.parse("how many kilometer")).startsWith("meter"), false);
    }

    static void news() {
        String xml = "<rss><channel><title>Top stories - Google News</title>"
                + "<item><title>Monsoon to reach Kerala early, says IMD - The Hindu</title><link>x</link>"
                + "<source url=\"https://www.thehindu.com\">The Hindu</source></item>"
                + "<item><title><![CDATA[Sensex &amp; Nifty hit record &#8216;highs&#8217;]]></title></item>"
                + "<item><title>India beat Australia by 6 wickets in Perth - ESPNcricinfo</title><source url=\"u\">ESPNcricinfo</source></item>"
                + "<item><title></title></item>"
                + "<item><title>Four</title></item><item><title>Five</title></item><item><title>Six</title></item>"
                + "</channel></rss>";
        java.util.List<News.Item> l = News.parse(xml, News.COUNT);
        eq("news count", 5, l.size());
        eq("news title", "Monsoon to reach Kerala early, says IMD", l.get(0).title);
        eq("news source", "The Hindu", l.get(0).source);
        eq("news cdata", "Sensex & Nifty hit record \u2018highs\u2019", l.get(1).title);
        eq("news no source", "", l.get(1).source);
        eq("news skips empty", "Four", l.get(3).title);
        eq("news empty feed", 0, News.parse("<html>blocked</html>", 5).size());
        eq("news null", 0, News.parse(null, 5).size());
        String r = News.read(l, "Top stories");
        check("news read start", r.startsWith("Here are the top headlines. One. Monsoon to reach Kerala early, says IMD. From The Hindu."));
        check("news read five", r.contains("Five. Five.") && !r.contains("Six"));
        eq("news read sport", "Here are the Sports headlines. One. Four.", News.read(java.util.Collections.singletonList(new News.Item("Four", "")), "Sports"));
        eq("news none", "There's no news to read yet.", News.read(new java.util.ArrayList<News.Item>(), "Top stories"));
        eq("news one", "India beat Australia by 6 wickets in Perth. From ESPNcricinfo.", News.read(l.get(2)));
        eq("news url top", "https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en", News.url("top"));
        eq("news url topic", "https://news.google.com/rss/headlines/section/topic/SPORTS?hl=en-IN&gl=IN&ceid=IN:en", News.url("SPORTS"));
        eq("news topic name", "India", News.topicName("NATION"));
        eq("voice news", "news", String.valueOf(Voice.parse("read the news")));
        eq("voice khabar", "news", String.valueOf(Voice.parse("aaj ki khabar sunao")));
        eq("voice samachar", "news", String.valueOf(Voice.parse("समाचार सुनाओ")));
        eq("voice headlines", "news", String.valueOf(Voice.parse("headlines")));
        eq("voice newspaper not news", "search:newspaper stand", String.valueOf(Voice.parse("newspaper stand")));
    }

    static void voice() {
        eq("fm freq", "fm:91.1", String.valueOf(Voice.parse("FM 91.1")));
        eq("fm point", "fm:98.3", String.valueOf(Voice.parse("radio 98 point 3 lagao")));
        eq("fm hindi", "fm:93.5", String.valueOf(Voice.parse("एफएम ९३.५")));
        eq("fm after", "fm:104.0", String.valueOf(Voice.parse("104.0 fm")));
        eq("radio on", "fm", String.valueOf(Voice.parse("radio chalao")));
        eq("call english", "call:mom", String.valueOf(Voice.parse("Call Mom")));
        eq("call hinglish", "call:papa", String.valueOf(Voice.parse("papa ko call karo")));
        eq("call hindi", "call:मम्मी", String.valueOf(Voice.parse("मम्मी को फोन लगाओ")));
        eq("home", "home", String.valueOf(Voice.parse("take me home")));
        eq("home hindi", "home", String.valueOf(Voice.parse("घर ले चलो")));
        eq("office", "office", String.valueOf(Voice.parse("office chalo")));
        eq("fuel", "fuel", String.valueOf(Voice.parse("nearest petrol pump")));
        eq("rest", "rest", String.valueOf(Voice.parse("koi dhaba dikhao")));
        eq("music", "music", String.valueOf(Voice.parse("gaana chalao")));
        eq("next", "next", String.valueOf(Voice.parse("अगला")));
        eq("pause", "pause", String.valueOf(Voice.parse("stop")));
        eq("volume up", "volUp", String.valueOf(Voice.parse("awaaz badhao")));
        eq("volume down", "volDown", String.valueOf(Voice.parse("volume kam karo")));
        eq("navigate", "search:india gate", String.valueOf(Voice.parse("navigate to India Gate")));
        eq("place le chalo", "search:connaught place", String.valueOf(Voice.parse("connaught place le chalo")));
        eq("plain place", "search:phoenix mall", String.valueOf(Voice.parse("Phoenix Mall")));
        check("same name mummy/Mom", Voice.sameName("Mom", "mummy"));
        check("same name मम्मी/Mom", Voice.sameName("Mom", "मम्मी"));
        check("same name papa/Dad", Voice.sameName("Dad", "papa"));
        check("different names", !Voice.sameName("Ravi", "mummy"));
        eq("spoken number", "9845012345", Voice.number("98450 12345"));
        eq("not a number", "", Voice.number("mom"));
    }

    static void coach() {
        Coach.Tracker t = new Coach.Tracker();
        long now = 0;
        check("no drive while still", t.feed(now, 0, 0, false, false) == null && t.cur == null);
        t.feed(now += 1000, 20, 0.005, false, false);   // moves off: drive starts
        check("drive started", t.cur != null);
        for (int i = 0; i < 60; i++) t.feed(now += 1000, 50, 0.014, i == 5, false);   // a minute at 50
        for (int i = 0; i < 120; i++) t.feed(now += 1000, 0, 0, false, false);        // two minutes at a signal
        for (int i = 0; i < 60; i++) t.feed(now += 1000, 100, 0.028, false, false);   // a minute at 100
        eq("idle counted after moving again", 120L, t.cur.idleSec);
        eq("fast seconds", 60L, t.cur.fastSec);
        eq("hard starts", 1, t.cur.starts);
        Coach.Trip done = null;
        for (int i = 0; i < 11 * 60 && done == null; i++) done = t.feed(now += 1000, 0, 0, false, false);
        check("ten-minute stop ends the drive", done != null && t.cur == null);
        eq("long stop isn't idling", 120L, done.idleSec);
        double[] w = Coach.waste(done, 18);
        near("idle litres", 120 / 3600.0 * Coach.IDLE_LPH, w[0], 1e-9);
        near("start litres", Coach.START_L, w[1], 1e-9);
        check("reasons name idling first " + Coach.reasons(done, w), Coach.reasons(done, w).startsWith("2 min idling") || w[2] > w[0]);
        eq("break clock", 0L, new Coach.Tracker().drivingMinutes(5));
    }

    static void batch2() {
        // distances and "ahead"
        near("1 km north", 1000, Geo.dist(28.6, 77.2, 28.6 + 1 / 111.195, 77.2), 2);
        near("bearing east", 90, Geo.bearing(28.6, 77.2, 28.6, 77.3), 0.5);
        check("ahead when heading at it", Geo.ahead(28.6, 77.2, 0, 28.603, 77.2, 700));
        check("not ahead when heading away", !Geo.ahead(28.6, 77.2, 180, 28.603, 77.2, 700));
        check("too far", !Geo.ahead(28.6, 77.2, 0, 28.62, 77.2, 700));

        // monsoon: only in rain, once per spot
        List<double[]> spots = Arrays.asList(new double[]{28.603, 77.2, 0});
        java.util.Set<String> told = new java.util.HashSet<>();
        eq("dry: no warning", null, Monsoon.warning(spots, told, false, 28.6, 77.2, 0, 40));
        String w = Monsoon.warning(spots, told, true, 28.6, 77.2, 0, 40);
        check("wet: warns " + w, w != null && w.contains("350 metres"));
        eq("only once", null, Monsoon.warning(spots, told, true, 28.6, 77.2, 0, 40));

        // quiet hours across midnight
        check("23 h quiet", Quiet.inside(23, 22, 6));
        check("3 h quiet", Quiet.inside(3, 22, 6));
        check("6 h not quiet", !Quiet.inside(6, 22, 6));
        check("12 h not quiet", !Quiet.inside(12, 22, 6));
        eq("10 pm", "10 pm", Quiet.hour(22));

        // petrol left: full at 1000 km, 300 km at 20 km/l = 15 l used of 37
        double[] gps = {1000, 1200}, litres = {30, 5};
        boolean[] full = {true, false};
        near("after a part fill", 37 - 10 + 5 - 5, FuelPlan.litresLeft(37, gps, litres, full, 1300, 20), 1e-9);
        near("no full fill", -1, FuelPlan.litresLeft(37, gps, litres, new boolean[]{false, false}, 1300, 20), 1e-9);
        near("never below empty", 0, FuelPlan.litresLeft(37, new double[]{0}, new double[]{37}, new boolean[]{true}, 5000, 20), 1e-9);

        // a drive remembers where it started and ended
        Coach.Tracker tr = new Coach.Tracker();
        tr.feed(0, 30, 0, false, false, 1, 2);
        tr.feed(1000, 30, 0.01, false, false, 1.1, 2.1);
        Coach.Trip c = tr.close(2000);
        check("start kept", c.sLat == 1 && c.sLon == 2);
        check("end kept", c.eLat == 1.1 && c.eLon == 2.1);

        // driver profiles keep personal settings only
        check("theme is personal", Profiles.personal("theme"));
        check("boxes are personal", Profiles.personal("p1slot3") && Profiles.personal("slot0"));
        check("fuel log is shared", !Profiles.personal("fuel"));
        check("valet PIN is shared", !Profiles.personal("valetPin"));

        // voice
        eq("where did I park", "park", Voice.parse("where did I park").kind);
        eq("paani bhara", "flood", Voice.parse("yahan paani bhara hai").kind);
        eq("service centre", "service", Voice.parse("nearest service centre").kind);
        eq("save clip", "clip", Voice.parse("save the clip").kind);

        // QR codes encode
        check("qr", org.openrath.qr.QrCode.encodeText(Geo.mapsLink(28.6, 77.2), org.openrath.qr.QrCode.Ecc.MEDIUM).size >= 21);
    }

    static void layouts() {
        eq("north", "N", Layouts.compass(0));
        eq("south-east", "SE", Layouts.compass(135));
        eq("wraps to north", "N", Layouts.compass(355));
        eq("negative is west", "W", Layouts.compass(-90));
        eq("colour", 0xFF3A8DFF, Layouts.color("#3A8DFF"));
        eq("bad colour is white", 0xFFFFFFFF, Layouts.color("blue"));
        eq("stat name", "Air pressure", Layouts.statName("pressure"));
        check("layout is per driver", Profiles.personal("layout") && Profiles.personal("lay:scenery:off"));
        eq("dock key", "media:radio", LayoutView.dockKey("radio"));
        eq("dock label", "Bluetooth", LayoutView.label("bt"));
    }

    static java.util.Calendar day(int y, int m, int d, int h, int min) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d, h, min);
        return c;
    }

    static void batch3() {
        // a road drawn north along 77.2 for 1 km
        List<double[]> north = Arrays.asList(new double[]{28.600, 77.2}, new double[]{28.605, 77.2}, new double[]{28.610, 77.2});
        double[] n = Ahead.nearest(north, 28.603, 77.2001);
        check("nearest is close " + n[0], n[0] < 15);
        near("road runs north", 0, n[1] % 360, 1);

        // a road north then turning east: the bend is ahead going north, none going south
        List<double[]> bend = Arrays.asList(new double[]{28.600, 77.2}, new double[]{28.602, 77.2}, new double[]{28.602, 77.203});
        double[] b = Ahead.nextBend(bend, 28.6005, 77.2, 0, 400);
        check("bend found", b != null);
        if (b != null) {
            near("bend about 167 m ahead", 167, b[0], 20);
            eq("bend to the right", 1.0, b[1]);
        }
        check("straight behind", Ahead.nextBend(bend, 28.6005, 77.2, 180, 400) == null);
        check("left when coming back", Ahead.nextBend(bend, 28.602, 77.2025, 270, 400)[1] < 0);

        check("school at 8 on Monday", Ahead.schoolHours(day(2026, 10, 12, 8, 0)));
        check("no school on Sunday", !Ahead.schoolHours(day(2026, 10, 11, 8, 0)));
        check("no school at 6 pm", !Ahead.schoolHours(day(2026, 10, 12, 18, 0)));
        eq("toll text", "Toll plaza in 2 kilometres, about 100 rupees. Keep to the FASTag lane.", Ahead.tollText(2050, 100, ""));
        eq("toll text near", "Toll plaza, Kherki Daula, in 600 metres. Keep to the FASTag lane.", Ahead.tollText(640, 0, "Kherki Daula"));

        // holidays
        check("upcoming starts with Dussehra", Holidays.upcoming(day(2026, 10, 13, 9, 0), 3).get(0).startsWith("Dussehra"));

        // documents, games, fog
        eq("rc photo", 0, Wallet.which("RC_front.jpg"));
        eq("insurance pdf ignored", -1, Wallet.which("insurance.pdf"));
        eq("licence", 3, Wallet.which("license.png"));
        eq("kids km far", "48", Kids.km(48400));
        eq("kids km near", "4.5", Kids.km(4460));
        eq("bend text", "Bend to the left in 150 m", Fog.bendText(152, -1));
        eq("no road known", "", Fog.bendText(-1, 0));

        // voice
        eq("documents", "docs", Voice.parse("show my documents").toString());
        eq("kagaz", "docs", Voice.parse("कागज दिखाओ").toString());
        eq("fog", "fog", Voice.parse("fog mode on").toString());
        eq("kids", "kids", Voice.parse("bachchon ke games").toString());
        eq("care alignment item", "alignment", Care.ITEMS[2]);
    }

    static void floaters() {
        float[] at = Floaters.place(0.5f, 0.5f, 1000, 600, 64);
        near("mic centred x", 468, at[0], 0.5);
        near("mic centred y", 268, at[1], 0.5);
        float[] edge = Floaters.place(1.2f, -0.1f, 1000, 600, 64);
        near("mic kept on screen x", 936, edge[0], 0.5);
        near("mic kept on screen y", 0, edge[1], 0.5);
        eq("cpu percent", 25, Floaters.percent(100, 1000, 200, 1400));
        eq("cpu no time", 0, Floaters.percent(100, 1000, 200, 1000));
    }

    static void fixes119() {
        eq("screen dims with lights", true, Lights.dims("system:screen_brightness", "200", "60"));
        eq("brighter is not dimming", false, Lights.dims("system:screen_brightness", "60", "200"));
        eq("not a brightness key", false, Lights.dims("prop:sys.ill", "0", "1"));
        String[] sig = {"file:/sys/class/backlight/bl/brightness", "200", "60"};
        eq("dimmed reads on", true, Lights.isOn("60", sig));
        eq("fading reads on", true, Lights.isOn("95", sig));
        eq("full reads off", false, Lights.isOn("200", sig));
        eq("near full reads off", false, Lights.isOn("180", sig));
        eq("text signal on", true, Lights.isOn("1", new String[]{"prop:x", "0", "1"}));
        eq("text signal off", false, Lights.isOn("0", new String[]{"prop:x", "0", "1"}));
        eq("missing reading", false, Lights.isOn(null, sig));
        boolean[][] m = Qr.modules(Geo.mapsLink(28.6139, 77.209));
        eq("parking code stays version 3", 29, m.length);
        eq("finder corner dark", true, m[0][0]);
    }

    static void batch4() {
        eq("aqi good", 25, Air.index(15, 20));
        eq("aqi pm25 poor", 251, Air.index(105, 80));
        eq("aqi pm10 wins", 151, Air.index(20, 175));
        eq("aqi band", "Very poor", Air.band(350));
        eq("aqi none", -1, Air.index(Double.NaN, Double.NaN));
        eq("aqi alert", true, Air.alert(260, 10 * 3600000L, 0) != null);
        eq("aqi alert not again soon", null, Air.alert(260, 10 * 3600000L, 9 * 3600000L));
        eq("aqi moderate quiet", null, Air.alert(180, 10 * 3600000L, 0));

        eq("share rounds up", 334, Errands.share(1000, 3));
        eq("share exact", 500, Errands.share(1000, 2));
        eq("upi link", "upi://pay?pa=tech@okaxis&pn=Tech%20B&am=334.00&cu=INR&tn=Trip%20share",
                Errands.upiLink("tech@okaxis", "Tech B", 334, "Trip share"));
        eq("upi id ok", true, Errands.validVpa("tech.b-1@okaxis"));
        eq("upi id bad", false, Errands.validVpa("tech okaxis"));
        String pl = Errands.parkingLink(0, 60);
        eq("parking link times", true, pl.contains("dates=19700101T005000Z/19700101T010000Z"));

        Highway hw = new Highway();
        eq("highway not yet", 0, hw.feed(90, 0));
        eq("highway on", 1, hw.feed(90, 121000));
        eq("highway stays at 60", 0, hw.feed(60, 200000));
        hw.feed(30, 300000);
        eq("highway off", -1, hw.feed(30, 421000));
        eq("highway end text", "Highway mode off. 2 messages came in while you were on the highway.", Highway.endText(2));

        eq("parked music engine running", "", ParkedMusic.decide(true, 60 * 60000L, 20, true, 800, 14.1f));
        eq("parked music warn", "warn", ParkedMusic.decide(true, 19 * 60000L + 5000, 20, true, 0, 12.4f));
        eq("parked music pause", "pause", ParkedMusic.decide(true, 21 * 60000L, 20, true, 0, 12.4f));
        eq("parked music low battery", "pause", ParkedMusic.decide(true, 60000L, 20, true, 0, 11.7f));
        eq("parked music no obd", "remind", ParkedMusic.decide(true, 31 * 60000L, 20, false, -1, -1));
        eq("parked music silent", "", ParkedMusic.decide(false, 99 * 60000L, 20, true, 0, 12.0f));

        eq("long press fires", "long", Keys.press(true, false, 800, false, true));
        eq("long press once", null, Keys.press(true, false, 900, true, true));
        eq("short press", "short", Keys.press(false, false, 200, false, true));
        eq("release after long", null, Keys.press(false, false, 900, true, true));
        eq("no long action", null, Keys.press(true, false, 900, false, false));

        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(2026, 9, 13, 8, 0);
        String sum = Errands.summary(c, "Tech", "24°", "Clear", "", 230, 312, "OIL CHANGE DUE IN 300 KM", "Diwali, Sun 8 Nov");
        eq("summary", "Good morning, Tech. It's 24 degrees and clear. The air is poor, A Q I 230. Petrol for about 310 kilometres. "
                + "Oil change due in 300 km. Coming up: Diwali, Sun 8 Nov.", sum);
        eq("voice split", "split", Voice.parse("split the cost").kind);
        eq("voice parking timer", "parkTimer", Voice.parse("parking timer").kind);
        eq("voice aqi", "aqi", Voice.parse("air quality").kind);
        eq("voice puc stays docs", "docs", Voice.parse("show the puc").kind);
    }

    /** 1.21: any car (the profile's helpers) and any screen (the design's scale). */
    static void generic() {
        eq("brand id", "maruti-suzuki", Car.idFor("Maruti Suzuki"));
        eq("brand id with accent", "citro-n", Car.idFor("Citroën"));
        eq("brand id spaces", "mercedes-benz", Car.idFor(" Mercedes-Benz "));
        eq("name from model", "GRAND VITARA", Car.nameFor("Grand Vitara"));
        eq("long name cut", 14, Car.nameFor("A Very Long Model Name Indeed").length());
        eq("maruti words", "maruti|suzuki|nexa|arena|true value", Car.searchWords("maruti-suzuki", "Maruti Suzuki"));
        eq("tata words", "tata", Car.searchWords("tata-motors", "Tata Motors"));
        eq("plain brand words", "hyundai", Car.searchWords("hyundai", "Hyundai"));
        eq("two-word brand", "land|rover", Car.searchWords("land-rover", "Land Rover"));
        eq("no brand", "", Car.searchWords("", ""));
        eq("service search", "Kia service centre", Car.serviceSearch("Kia"));
        eq("service search, no brand", "car service centre", Car.serviceSearch(""));
        eq("cng word", "CNG", Car.word("cng"));
        eq("ev stations", "charging stations", Car.stations("ev"));
        eq("diesel stations", "fuel pumps", Car.stations("diesel"));
        check("ev has no kmpl", Car.kmpl("ev") == 0);
        check("diesel goes further than petrol", Car.kmpl("diesel") > Car.kmpl("petrol"));
        near("1024x600 as designed", 1.0, Ui.scale(1024, 600, 1f), 1e-6);
        near("1280x720", 1.2, Ui.scale(1280, 720, 1f), 1e-6);
        near("1280x800 fits the width", 1.25, Ui.scale(1280, 800, 1f), 1e-6);
        near("1920x720 ultra-wide fits the height", 1.2, Ui.scale(1920, 720, 1f), 1e-6);
        near("portrait 768x1024 fits the width", 0.75, Ui.scale(768, 1024, 1f), 1e-6);
        near("owner's size", 1.1, Ui.scale(1024, 600, 1.1f), 1e-6);
        check("portrait", Ui.portrait(768, 1024));
        check("landscape", !Ui.portrait(1024, 600));
        check("square counts as landscape", !Ui.portrait(1000, 1000));
        eq("topway", "topway", Stereo.from(java.util.Arrays.asList("com.android.settings", "com.tw.service.xt")));
        eq("fyt", "fyt", Stereo.from(java.util.Arrays.asList("com.syu.ms")));
        eq("mtc", "mtc", Stereo.from(java.util.Arrays.asList("android.microntek.service")));
        eq("plain", "android", Stereo.from(java.util.Arrays.asList("com.android.settings")));
        check("esri tile", Tiles.isTile("https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/12/1700/2900"));
        check("osm tile", Tiles.isTile("https://tile.openstreetmap.org/12/2900/1700.png"));
        check("not a tile", !Tiles.isTile("https://photon.komoot.io/api/?q=x"));
        eq("tile file", "tile.openstreetmap.org/12/2900/1700.png", Tiles.key("https://tile.openstreetmap.org/12/2900/1700.png?x=1"));
        check("tile file stays inside", !Tiles.key("https://tile.openstreetmap.org/../../etc/x").contains(".."));
        eq("google offline", "google.navigation:q=28.613900,77.209000", OfflineNav.uri("com.google.android.apps.maps", 28.6139, 77.209, "x"));
        near("fm text", 91.1, Xt.mhz("91.10"), 1e-4);
        near("fm text with unit", 98.3, Xt.mhz(" 98.3 MHz"), 1e-4);
        near("fm text with comma", 104.8, Xt.mhz("104,8"), 1e-4);
        near("not fm", 0, Xt.mhz("12.5"), 1e-9);
        near("not a number", 0, Xt.mhz("Radio City"), 1e-9);
    }

    /** The boot logo BMP, the animation's desc.txt and stored zip, and its timeline. */
    static void bootArt() {
        int[] px = {0xFF102030, 0xFFFFFFFF, 0xFF000000, 0xFFABCDEF, 0xFF010203, 0xFF0A0B0C};   // 3 x 2
        byte[] b = BootArt.bmp(px, 3, 2);
        eq("bmp size", 54 + 12 * 2, b.length);
        eq("bmp magic", "BM", new String(b, 0, 2));
        eq("bmp width", 3, (b[18] & 255) | (b[19] & 255) << 8);
        eq("bmp height", 2, (b[22] & 255) | (b[23] & 255) << 8);
        eq("bmp 24-bit", 24, (int) b[28]);
        // bottom-up: the file's first row is the picture's last row, in blue-green-red order
        eq("bottom row first, blue", 0xEF, b[54] & 255);
        eq("bottom row first, red", 0xAB, b[56] & 255);
        eq("top row last", 0x30, b[54 + 12] & 255);
        eq("row padding", 0, (int) b[54 + 9]);
        eq("desc", "1024 600 25\np 1 0 part0\np 0 0 part1\n", BootArt.desc(1024, 600));
        near("starts dark", 0, BootArt.markAlpha(0), 1e-9);
        near("mark fully in", 1, BootArt.markAlpha(2), 1e-9);
        near("wheel stops turning", 0, BootArt.spin(BootArt.FRAMES / (float) BootArt.FPS), 1e-6);
        check("name last", BootArt.nameAlpha(2.5f) == 0 && BootArt.nameAlpha(BootArt.FRAMES / (float) BootArt.FPS) == 1);
        check("shine over by the end", BootArt.shine(BootArt.FRAMES / (float) BootArt.FPS) > 1);
        try {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(o);
            BootArt.stored(z, "desc.txt", "x".getBytes("US-ASCII"));
            BootArt.stored(z, "part0/000.png", new byte[]{1, 2, 3});
            z.close();
            java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(o.toByteArray()));
            java.util.zip.ZipEntry e = in.getNextEntry();
            eq("zip first", "desc.txt", e.getName());
            eq("zip stored", java.util.zip.ZipEntry.STORED, e.getMethod());
            e = in.getNextEntry();
            eq("zip frame", "part0/000.png", e.getName());
            eq("zip frame size", 3L, e.getSize());
        } catch (Exception x) {
            check("zip " + x, false);
        }
    }

    static void batch5() {
        // speed breakers: three drives at the same spot, heading east
        Spots b = new Spots(0.0003, 12, 400);
        for (int d = 1; d <= 3; d++) b.add(28.61000 + d * 0.00001, 77.20000, 90, d, 1);
        b.add(28.61000, 77.20000, 90, 3, 1);   // same drive twice counts once
        Spots.Test three = new Spots.Test() {
            @Override
            public boolean ok(Spots.Spot s) {
                return Spots.drives(s) >= 3;
            }
        };
        eq("breaker learnt", 1, b.count(three));
        // approaching from the west, heading east, 120 m before it
        eq("breaker ahead", true, b.ahead(28.61000, 77.19877, 90, 180, three) != null);
        eq("breaker behind", true, b.ahead(28.61000, 77.20123, 90, 180, three) == null);
        eq("breaker other direction", true, b.ahead(28.61000, 77.20123, 270, 180, three) == null);
        Spots back = new Spots(0.0003, 12, 400);
        back.load(b.save());
        eq("breakers saved and loaded", 1, back.count(three));

        java.util.List<Long> w = new java.util.ArrayList<>();
        w.add(30L);
        w.add(150L);
        w.add(90L);
        eq("median wait", 90L, Spots.median(w));
        eq("wait text minute", "At the stop ahead you usually wait about a minute.", Errands.waitText(70));
        eq("wait text half", "At the stop ahead you usually wait about 2 and a half minutes.", Errands.waitText(150));
        eq("wait text whole", "At the stop ahead you usually wait about 3 minutes.", Errands.waitText(185));

        double[] km = {1000, 1500, 2000, 2500, 3000, 3300};
        double[] l = {30, 28, 28, 28, 28, 28};
        boolean[] full = {true, true, true, true, true, true};
        java.util.List<Double> k = Errands.fullTankKmpl(km, l, full);
        eq("full tank stretches", 5, k.size());
        near("kmpl", 17.86, k.get(0), 0.01);
        eq("fuel drop warned", true, Errands.fuelDrop(k) != null);
        double[] km2 = {1000, 1500, 2000, 2500, 3000};
        eq("fuel normal", null, Errands.fuelDrop(Errands.fullTankKmpl(km2, new double[]{30, 28, 28, 28, 28}, new boolean[]{true, true, true, true, true})));
        java.util.List<Double> part = Errands.fullTankKmpl(new double[]{0, 300, 600}, new double[]{30, 10, 20}, new boolean[]{true, false, true});
        near("partial fill counted", 20.0, part.get(0), 0.01);

        eq("lights reminder off", "It's dark and your headlights look off. Please switch them on.", Errands.lightsReminder(true, 40, true, false, false));
        eq("lights on quiet", null, Errands.lightsReminder(true, 40, true, true, false));
        eq("lights unknown", "It's getting dark. Remember your headlights.", Errands.lightsReminder(true, 40, false, false, false));
        eq("lights daytime", null, Errands.lightsReminder(false, 40, true, false, false));
        eq("lights told", null, Errands.lightsReminder(true, 40, true, false, true));

        eq("sound highway", "highway", Errands.soundProfile(true, true));
        eq("sound night", "night", Errands.soundProfile(false, true));
        eq("sound city", "city", Errands.soundProfile(false, false));

        String rep = Errands.weekly(240, 5 * 3600 + 30 * 60, 1800, 230, 86, "Tue, 12 km: smooth drive", "");
        eq("weekly", "Driven: 240 km in 5 h 30 min (average 44 km/h)\nPetrol: ₹1,800 (₹7.5 a km)\nTolls: ₹230\nDriving score: 86 out of 100\n"
                + "Best drive: Tue, 12 km: smooth drive", rep);
        eq("wifi qr", "WIFI:T:WPA;S:Dzire\\;Car;P:pa:ss;;".replace("pa:ss", "pa\\:ss"), Errands.wifiQr("Dzire;Car", "pa:ss"));
        eq("wifi open", "WIFI:T:nopass;S:Dzire;;", Errands.wifiQr("Dzire", ""));
        eq("door steps", 3, Lights.steps("door").length);
        eq("voice hotspot", "hotspot", Voice.parse("turn on hotspot").kind);
        eq("voice weekly", "weekly", Voice.parse("weekly report").kind);
    }
}
