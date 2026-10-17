package org.openrath.launcher;

/**
 * Battery saver for music while parked: with the car standing and the engine off (from the OBD
 * adapter: no rpm, or the battery not charging), music is paused after a while so the battery
 * still starts the car. Without an adapter the engine can't be told apart from idling with the AC
 * on, so it only reminds once per stop. No Android types, so it is tested.
 */
final class ParkedMusic {

    /** What to do now: "" nothing, "warn" a minute before pausing, "pause", or "remind" (no adapter). */
    static String decide(boolean playing, long stillMs, int limitMin, boolean obd, int rpm, float volts) {
        if (!playing) return "";
        if (obd) {
            boolean engineOff = rpm == 0 || (volts > 0 && volts < 12.9f);
            if (!engineOff) return "";
            if (volts > 0 && volts < 11.9f) return "pause";   // the battery is already low
            long limit = limitMin * 60000L;
            if (stillMs >= limit) return "pause";
            if (stillMs >= limit - 60000) return "warn";
            return "";
        }
        return stillMs >= Math.max(30, limitMin) * 60000L ? "remind" : "";
    }

    private ParkedMusic() {
    }
}
