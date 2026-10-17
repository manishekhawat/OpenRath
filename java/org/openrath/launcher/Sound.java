package org.openrath.launcher;

/**
 * Ambient sound profiles: city, highway and night, each with its own equalizer preset and a
 * volume step, switched by highway mode and by sunset. The owner's own equalizer stays the city
 * sound unless changed.
 */
final class Sound {

    static final String[] NAMES = {"City", "Highway", "Night"};

    /** Volume steps added for a profile, by default: louder over road noise, softer at night. */
    static int vol(String profile) {
        return "highway".equals(profile) ? 2 : "night".equals(profile) ? -2 : 0;
    }

    /** The equalizer preset for a profile; "" keeps the owner's own setting. */
    static String eq(Prefs p, String profile) {
        String def = "highway".equals(profile) ? "Rock" : "night".equals(profile) ? "Vocal" : "";
        return p.str("sndEq:" + profile, def);
    }

    private Sound() {
    }
}
