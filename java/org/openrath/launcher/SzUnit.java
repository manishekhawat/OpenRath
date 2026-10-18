package org.openrath.launcher;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.Locale;

/**
 * The szchoiceway add-on (a common Chinese head-unit platform sold under many brands): the radio's
 * band and frequency from Settings.Global "SYS_MEDIA_INFO_JSON", and seek, start, stop and direct
 * tuning through the MCU broadcast the stock apps use. Calls, reverse gear and the headlight wire
 * stay with plain Android and OpenRath's own signal setup. Not yet tried on a szchoiceway unit.
 */
final class SzUnit extends Unit {

    static final String JSON = "SYS_MEDIA_INFO_JSON";
    static final String CMD = "com.szchoiceway.eventcenter.EventUtils.ACTION_MCU_CMD_EVENT", DATA = "EventUtils.MCU_CMD_DATA";

    private final Hub h;
    private volatile String band = "", freq = "";
    private Boolean fmOn;
    private ContentObserver watch;

    SzUnit(Context c, Hub hub) {
        super(c);
        h = hub;
    }

    @Override
    String name() {
        return "szchoiceway";
    }

    @Override
    void start() {
        read();
        try {
            watch = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean self) {
                    read();
                    h.notifyIfChanged(true);
                }
            };
            ctx.getContentResolver().registerContentObserver(Settings.Global.getUriFor(JSON), false, watch);
        } catch (Throwable e) {
            watch = null;
        }
    }

    @Override
    void stop() {
        if (watch != null) try {
            ctx.getContentResolver().unregisterContentObserver(watch);
        } catch (Throwable ignored) {
        }
        watch = null;
    }

    private void read() {
        String[] r = null;
        try {
            r = parse(Settings.Global.getString(ctx.getContentResolver(), JSON));
        } catch (Throwable ignored) {
        }
        band = r == null ? "" : r[0];
        freq = r == null ? "" : r[1];
    }

    /** {band, frequency} from the media JSON ({"mediaTitle":"FM1 90.10"}), or null when the radio isn't on it. */
    static String[] parse(String json) {
        if (json == null || json.isEmpty()) return null;
        String t;
        try {
            t = new org.json.JSONObject(json).optString("mediaTitle", "").trim();
        } catch (Exception e) {
            return null;
        }
        return fromTitle(t);
    }

    /** {band, frequency} from the radio's title, "FM1 90.10" or "AM 1040"; null for anything else. */
    static String[] fromTitle(String t) {
        String[] parts = t == null ? new String[0] : t.trim().split("\\s+");
        if (parts.length < 2) return null;
        String b = parts[0].toUpperCase(Locale.US);
        if (!b.startsWith("FM") && !b.startsWith("AM")) return null;
        try {
            double f = Double.parseDouble(parts[1].replace(',', '.'));
            String shown = b.startsWith("AM") ? String.format(Locale.US, "%.0f", f) : String.format(Locale.US, "%.1f", f);
            return new String[]{b, shown};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    String stamp() {
        return band + freq + fmOn;
    }

    private boolean send(byte[] b) {
        try {
            ctx.sendBroadcast(new Intent(CMD).putExtra(DATA, b));
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * The MCU frame that tunes a station: 0d 08, then 5a a5 0d 91, the band (FM3 03, AM 04, else 01),
     * the bank ("01" or "02"), zeros, the frequency as text, and the low byte of the sum of the frame.
     */
    static byte[] tuneFrame(String band, double f) {
        String b = band == null ? "FM1" : band.toUpperCase(Locale.US);
        boolean am = b.startsWith("AM");
        byte bandByte = (byte) ("FM3".equals(b) ? 0x03 : am ? 0x04 : 0x01);
        String bank = "FM2".equals(b) ? "02" : "01";
        String fs = am ? String.format(Locale.US, "%.0f", f) : String.format(Locale.US, "%.1f", f);
        int pad = Math.max(0, 9 - fs.length());
        byte[] frame = new byte[3 + 4 + pad + fs.length()];
        int i = 0;
        frame[i++] = 0x5a;
        frame[i++] = (byte) 0xa5;
        frame[i++] = 0x0d;
        frame[i++] = (byte) 0x91;
        frame[i++] = bandByte;
        frame[i++] = (byte) bank.charAt(0);
        frame[i++] = (byte) bank.charAt(1);
        i += pad;
        for (int k = 0; k < fs.length(); k++) frame[i++] = (byte) fs.charAt(k);
        int sum = 0;
        for (byte x : frame) sum += x & 0xff;
        byte[] out = new byte[2 + frame.length + 1];
        out[0] = 0x0d;
        out[1] = 0x08;
        System.arraycopy(frame, 0, out, 2, frame.length);
        out[out.length - 1] = (byte) (sum & 0xff);
        return out;
    }

    // ------------------------------------------------------------------ FM

    @Override
    boolean fm(Prefs p) {
        return true;
    }

    @Override
    boolean fmFirst(Prefs p) {
        return !freq.isEmpty();
    }

    @Override
    String fmPlay(Context c, Prefs p, boolean on) {
        boolean ok = send(on ? new byte[]{0x01, 0x01} : new byte[]{0x01, 0x63});
        if (ok) fmOn = on;
        return ok ? null : Hindi.t("the stereo's radio didn't answer");
    }

    @Override
    void fmTune(Prefs p, String f) {
        try {
            double v = Double.parseDouble(f);
            send(tuneFrame(v >= 520 ? "AM" : band.startsWith("FM") ? band : "FM1", v));
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    boolean fmSeek(Prefs p, boolean up) {
        return send(new byte[]{0x02, (byte) (up ? 0x0f : 0x0e)});
    }

    @Override
    void fmQuiet() {
        if (fmPlaying()) fmPlay(ctx, null, false);
    }

    @Override
    Show fmShow(Prefs p, String note, boolean localOn) {
        Show s = new Show();
        boolean am = band.startsWith("AM");
        if (!freq.isEmpty() && !am && !freq.equals(p.fmFreq())) p.setFmFreq(freq);
        s.title = (freq.isEmpty() ? p.fmFreq() : freq) + "  " + (band.isEmpty() ? "FM" : band);
        s.playing = fmOn;
        boolean on = s.playing != null ? s.playing : localOn;
        s.sub = !note.isEmpty() && on ? note : on ? Hindi.t("FM radio") : Hindi.t("Tap play to listen");
        return s;
    }

    @Override
    boolean fmPlaying() {
        return fmOn != null && fmOn;
    }
}
