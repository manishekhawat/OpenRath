package org.openrath.launcher;

/**
 * How much the screen moves: Standard, Reduced (half-length animations, fewer frames for the
 * moving road) or Off (no animations, and the road and the 360 car stand still). For slow stereos
 * that stutter, and for drivers who find motion distracting. Settings > Home screen.
 */
final class Motion {

    static final String[] LEVELS = {"standard", "reduced", "off"};
    static final String[] NAMES = {"Standard", "Reduced", "Off"};

    /** The owner's choice, read again on each dashboard build. */
    static String level = "standard";

    static void load(Prefs p) {
        level = pick(p.str("motion", "standard"));
    }

    static String pick(String v) {
        for (String l : LEVELS) if (l.equals(v)) return l;
        return "standard";
    }

    /** An animation's length at this level. */
    static long ms(long standard) {
        return ms(level, standard);
    }

    static long ms(String level, long standard) {
        if ("off".equals(level)) return 0;
        return "reduced".equals(level) ? standard / 2 : standard;
    }

    /** Time between frames of something that keeps moving, or -1 for not at all. */
    static long frame() {
        return frame(level);
    }

    static long frame(String level) {
        if ("off".equals(level)) return -1;
        return "reduced".equals(level) ? 100 : 33;
    }

    private Motion() {
    }
}
