package org.openrath.launcher;

import android.app.Activity;
import android.content.Context;

/**
 * The Topway add-on: FM in the radio box, Bluetooth calls and reverse gear through Topway's own
 * services (the radio service com.tw.service.xt, the MCU link, the stock radio app). Loaded only on
 * Topway units with "Stereo maker's services" on; anything its services can't do falls back to
 * plain Android.
 */
final class TopwayUnit extends Unit {

    private final Hub h;

    TopwayUnit(Context c, Hub hub) {
        super(c);
        h = hub;
    }

    @Override
    String name() {
        return "Topway";
    }

    private Xt xt() {
        return h.xt != null && h.xt.ready() ? h.xt : null;
    }

    @Override
    void tick(Prefs p, Hub hub) {
        Xt xt = xt();
        if (xt == null) {
            super.tick(p, hub);
            return;
        }
        long now = System.currentTimeMillis();
        hub.btRaw = xt.value("btGetConnectedStatus");
        hub.btConnected = Xt.truthy(hub.btRaw) || xt.btLink == 1 || Hub.androidBt();
        Object ps = xt.value("btGetPhoneStatus");
        hub.phoneStatus = ps instanceof Number ? ((Number) ps).intValue() : ps instanceof Boolean && (Boolean) ps ? 1 : 0;
        // the last call: from the Bluetooth events, and from the unit's getters while a call is on
        if (xt.callAt > 0) Calls.note(p, xt.callNumber, xt.callName, xt.callDir, xt.callAt);
        if (hub.phoneStatus != 0) {
            String[] who = xt.caller();
            if (!who[0].isEmpty()) Calls.note(p, who[0], who[1], "", now);
        }
        hub.lastCall = Calls.last(ctx, p);
    }

    @Override
    String stamp() {
        Xt xt = h.xt;
        return (h.fm == null ? "" : h.fm.freq + h.fm.name + h.fm.on) + (xt == null ? "" : xt.freqText + xt.name + xt.playing + xt.btText + xt.btEvent)
                + Tuner.on + Tuner.mhz + Tuner.name + StockRadio.now();
    }

    @Override
    Boolean reverse() {
        Xt xt = xt();
        return xt != null && xt.has("getReverseStatus") ? xt.on("getReverseStatus") : null;
    }

    // ------------------------------------------------------------------ phone

    @Override
    String phoneDown() {
        return xt() == null ? Hindi.t("Topway's phone service isn't connected") : null;
    }

    @Override
    String call(Context c, String number) {
        Xt xt = xt();
        String n = number == null ? "" : number.replaceAll("[^0-9+]", "");
        if (xt == null || n.isEmpty()) return super.call(c, number);
        return xt.run("btCall:s:" + n);
    }

    @Override
    void answer(Context c) {
        Xt xt = xt();
        if (xt != null) xt.run("btAccept");
        else super.answer(c);
    }

    @Override
    void hangUp(Context c) {
        Xt xt = xt();
        if (xt != null) xt.run("btHangUP");
        else super.hangUp(c);
    }

    @Override
    String caller() {
        Xt xt = xt();
        return xt != null && System.currentTimeMillis() - xt.btAt < 120000 ? xt.btText : "";
    }

    // ------------------------------------------------------------------ FM

    @Override
    boolean fm(Prefs p) {
        return Tuner.chosen(p) || xt() != null || h.fm != null;
    }

    @Override
    boolean fmFirst(Prefs p) {
        return Tuner.chosen(p);
    }

    @Override
    String fmPlay(Context c, Prefs p, boolean on) {
        return Fm.play(p, xt(), h.fm, on);
    }

    @Override
    void fmTune(Prefs p, String freq) {
        if (Tuner.chosen(p)) {
            Tuner.tune(p, Tuner.parse(freq));
            return;
        }
        Xt xt = xt();
        if (xt != null) xt.run("radioOpenChannel:s:" + freq);
    }

    @Override
    boolean fmSeek(Prefs p, boolean up) {
        if (Tuner.chosen(p)) {
            Tuner.seek(p, up);
            return true;
        }
        Xt xt = xt();
        if (xt != null && (up ? xt.next() : xt.prev()) != null) return true;
        if (h.fm != null) {
            if (up) h.fm.next();
            else h.fm.prev();
            return true;
        }
        return false;
    }

    @Override
    void fmQuiet() {
        if (h.fm != null && h.fm.on) h.fm.play(false);
    }

    @Override
    Show fmShow(Prefs p, String note, boolean localOn) {
        Show s = new Show();
        if (Tuner.chosen(p)) {
            // OpenRath's own tuner: no stock radio app involved
            Tuner.readName();
            s.title = (Tuner.freqText().isEmpty() ? p.fmFreq() : Tuner.freqText()) + "  FM";
            s.playing = Tuner.on;
            s.sub = !note.isEmpty() && Tuner.on ? note : !Tuner.name.isEmpty() ? Tuner.name : Tuner.on ? Hindi.t("FM radio") : Hindi.t("Tap play to listen");
            return s;
        }
        Xt xt = xt();
        Tw.Radio fm = h.fm;
        if (xt != null) {
            // the radio may change station by itself (its own screen, the wheel buttons): ask now and then
            if (System.currentTimeMillis() - xt.askedAt > 3000) xt.askRadio();
            String now = Scan.freq(xt);
            if (!now.isEmpty() && !now.equals(p.fmFreq())) p.setFmFreq(now);
            String shown = xt.display(p.fmFreq());
            s.title = shown.isEmpty() ? Hindi.t("FM radio") : shown.startsWith("Seek") ? shown : shown + "  FM";
            s.playing = xt.playing;
            boolean on = xt.playing != null ? xt.playing : localOn;
            s.sub = !note.isEmpty() && on ? note : !xt.name.isEmpty() ? xt.name : on ? Hindi.t("FM radio") : Hindi.t("Tap play to listen");
            return s;
        }
        if (fm == null) {
            s.sub = !note.isEmpty() ? note : Hindi.t("Tap play to start the radio");
            return s;
        }
        String f = fm.frequency();
        s.title = f.isEmpty() ? Hindi.t("FM radio") : f + (fm.band == 2 ? "" : "  FM");
        s.sub = !note.isEmpty() && !fm.seen ? note : !fm.seen ? Hindi.t("Tap play to switch the tuner on") : !fm.name.isEmpty() ? fm.name
                : fm.on ? Hindi.t("Playing") : Hindi.t("Tap play to listen");
        s.playing = fm.on;
        return s;
    }

    @Override
    boolean canScan(Prefs p) {
        return Tuner.chosen(p) || xt() != null;
    }

    @Override
    void scan(Activity a, Prefs p, Runnable changed) {
        Scan.menu(a, p, xt(), changed);
    }

    @Override
    void fmPoll() {
        Xt xt = xt();
        if (xt != null) xt.poll();
    }

    @Override
    boolean fmPlaying() {
        Xt xt = xt();
        return Tuner.on || xt != null && xt.playing != null && xt.playing;
    }
}
