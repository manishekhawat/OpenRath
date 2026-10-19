package org.openrath.launcher;

import java.util.Locale;

/** km/h or mph for the speed, °C or °F for the weather. Distances stay in km. Settings > Home screen. */
final class Units {

    static boolean mph, fahrenheit;

    static void load(Prefs p) {
        mph = "mph".equals(p.str("speedUnit", "kmh"));
        fahrenheit = "f".equals(p.str("tempUnit", "c"));
    }

    /** A km/h figure in the chosen unit, rounded. */
    static int speed(float kmh, boolean mph) {
        return Math.round(mph ? kmh / 1.609344f : kmh);
    }

    static String speedLabel() {
        return mph ? "mph" : "km/h";
    }

    static double temp(double c, boolean f) {
        return f ? c * 9 / 5 + 32 : c;
    }

    /** "31°" or "88°". */
    static String temp(double c) {
        return String.format(Locale.US, "%d°", Math.round(temp(c, fahrenheit)));
    }

    private Units() {
    }
}
