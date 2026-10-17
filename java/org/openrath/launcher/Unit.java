package org.openrath.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * What the home screen needs from the stereo itself: FM in the radio box, Bluetooth calls, reverse
 * gear. OpenRath talks to the stereo only through this, so the rest of the app runs on any Android.
 * The plain Android version (this class) uses only standard Android: the radio app's own media
 * controls, the system phone (Bluetooth hands-free calls on most units), Android's call log. A
 * maker's add-on (TopwayUnit) replaces parts of it with that maker's own services; it loads only on
 * that make, and only while Settings > Features > "Stereo maker's services" is on.
 */
class Unit {

    /** What the radio box shows for FM driven by the unit itself. */
    static final class Show {
        String title = Hindi.t("FM radio"), sub = Hindi.t("Tap play to listen");
        /** null: the unit can't tell; the box keeps its own guess. */
        Boolean playing;
    }

    final Context ctx;

    Unit(Context c) {
        ctx = c.getApplicationContext();
    }

    /** The add-on for this stereo, or plain Android. */
    static Unit pick(Context c, Prefs p, Hub h) {
        if (!Features.on(p, "maker")) return new Unit(c);
        String k = Stereo.kind(c);
        if (Stereo.TOPWAY.equals(k)) return new TopwayUnit(c, h);
        if (Stereo.FYT.equals(k)) return new FytUnit(c, h);
        if (Stereo.MTC.equals(k)) return new MtcUnit(c, h);
        return new Unit(c);
    }

    String name() {
        return "Plain Android";
    }

    void start() {
    }

    void stop() {
    }

    /** Once a second from the hub. */
    void tick(Prefs p, Hub h) {
        h.btConnected = Hub.androidBt();
        h.phoneStatus = inCall() ? 1 : 0;
        h.lastCall = Calls.last(ctx, p);
    }

    /** Text that changes whenever anything this unit shows changes. */
    String stamp() {
        return "";
    }

    /** Headlights (the illumination wire): null when this unit can't tell. */
    Boolean lights() {
        return null;
    }

    /** "88.0" .. "108.0" from a frequency in any unit the stereos use (MHz x 100, kHz, Hz). */
    static String mhzText(long v) {
        float f = v >= 87500000L && v <= 108000000L ? v / 1000000f : v >= 87500 && v <= 108000 ? v / 1000f
                : v >= 8750 && v <= 10800 ? v / 100f : v >= 875 && v <= 1080 ? v / 10f : 0;
        return f > 0 ? String.format(java.util.Locale.US, "%.1f", f) : "";
    }

    /** Reverse gear: null when this unit can't tell. */
    Boolean reverse() {
        return null;
    }

    // ------------------------------------------------------------------ phone

    /** Why calls can't be placed, or null when they can. */
    String phoneDown() {
        return null;
    }

    /** Ring a number; null when the call started, else why not. */
    String call(Context c, String number) {
        String n = number == null ? "" : number.replaceAll("[^0-9+*#]", "");
        if (n.isEmpty()) return Hindi.t("no number");
        boolean direct = c.checkCallingOrSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
        try {
            c.startActivity(new Intent(direct ? Intent.ACTION_CALL : Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + n.replace("#", "%23")))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return null;
        } catch (Exception e) {
            return Hindi.t("the stereo has no phone app for calls");
        }
    }

    void answer(Context c) {
        telecom(c, "acceptRingingCall");
    }

    void hangUp(Context c) {
        telecom(c, "endCall");
    }

    /** Who is on the line, as the unit says it (beyond the call log); "" when unknown. */
    String caller() {
        return "";
    }

    boolean inCall() {
        Object r = telecom(ctx, "isInCall");
        return r instanceof Boolean && (Boolean) r;
    }

    /** A TelecomManager call by name (newer Android only; the unit may not allow it). */
    private static Object telecom(Context c, String method) {
        try {
            Object t = c.getSystemService("telecom");
            return t == null ? null : t.getClass().getMethod(method).invoke(t);
        } catch (Throwable e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ FM

    /**
     * True when the unit drives FM itself and the radio box shows it. Plain Android: false, and the
     * radio box follows the radio app's own media controls instead.
     */
    boolean fm(Prefs p) {
        return false;
    }

    /** True when FM through the unit wins over a radio app's media session. */
    boolean fmFirst(Prefs p) {
        return false;
    }

    /** Start or stop FM; null when it worked, else why not. */
    String fmPlay(Context c, Prefs p, boolean on) {
        return Hindi.t("this stereo's radio app is controlled from its own screen");
    }

    /** Tune a station, "91.1" (voice commands). */
    void fmTune(Prefs p, String freq) {
    }

    /** Next or previous station; false when the box should use the radio app's controls. */
    boolean fmSeek(Prefs p, boolean up) {
        return false;
    }

    /** The music box is starting: FM through the unit goes quiet. */
    void fmQuiet() {
    }

    Show fmShow(Prefs p, String note, boolean localOn) {
        return new Show();
    }

    boolean canScan(Prefs p) {
        return false;
    }

    void scan(Activity a, Prefs p, Runnable changed) {
    }

    /** Ask the radio for its state now (after play). */
    void fmPoll() {
    }

    /** FM is playing through the unit (for "pause everything"). */
    boolean fmPlaying() {
        return false;
    }
}
