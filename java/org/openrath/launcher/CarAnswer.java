package org.openrath.launcher;

import java.util.Locale;

/**
 * Answers to "ask the car" voice questions, from the live engine data and the trip: "how much
 * fuel?", "how's the engine?", "any faults?", "how's the battery?", "how far today?". Plain values
 * in, a sentence out, so the tests can check every answer.
 */
final class CarAnswer {

    static final class Car {
        boolean connected;
        int fuel = -1, coolant = -999, rpm = -1, faults = -1, worst = -1, hot = 105;
        float volts = -1;
        double todayKm;
        long todaySec;
    }

    /** The question's topic from what was heard, or null when it isn't a question about the car. */
    static String topic(String s) {
        if (has(s, "(how much (fuel|petrol|diesel)|fuel level|tank level|kitna (petrol|diesel|fuel)|(petrol|diesel|fuel) kitna|कितना पेट्रोल|पेट्रोल कितना|टंकी)")) return "fuel";
        if (has(s, "(fault|error code|check engine|engine light|कोई खराबी|kharabi|खराबी)")) return "faults";
        if (has(s, "(how('s| is) the engine|engine (ok|kaisa|kaise|temperature|temp)|engine status|इंजन कैसा|इंजन ठीक|coolant)")) return "engine";
        if (has(s, "(how('s| is) the battery|battery (ok|kaisi|status|voltage|level)|बैटरी)")) return "battery";
        if (has(s, "(how far (have i|did i|today)|aaj kitna chal|आज कितना|km today|kilometres today|kilometers today)")) return "trip";
        return null;
    }

    private static boolean has(String s, String re) {
        return java.util.regex.Pattern.compile(re).matcher(s).find();
    }

    /** The spoken answer, already in the chosen language. */
    static String answer(String topic, Car c) {
        if ("trip".equals(topic)) {
            return Hindi.f("Today you have driven %1$s km in %2$s minutes.", String.format(Locale.US, "%.1f", c.todayKm), c.todaySec / 60);
        }
        if (!c.connected) return Hindi.t("The engine adapter isn't connected, so I can't see the engine.");
        if ("fuel".equals(topic)) {
            if (c.fuel < 0) return Hindi.t("This car doesn't report its fuel level.");
            return c.fuel <= 15 ? Hindi.f("Fuel is low, %1$s percent. Time to fill up.", c.fuel)
                    : Hindi.f("Fuel is at %1$s percent.", c.fuel);
        }
        if ("battery".equals(topic)) {
            if (c.volts <= 0) return Hindi.t("I can't read the battery voltage.");
            String v = String.format(Locale.US, "%.1f", c.volts);
            if (c.volts < 11.8f) return Hindi.f("The battery is weak, %1$s volts. Get it checked.", v);
            if (c.rpm > 0 && c.volts < 13.2f) return Hindi.f("The battery is %1$s volts with the engine running, which is low; the alternator may not be charging.", v);
            return Hindi.f("The battery is fine, %1$s volts.", v);
        }
        if ("faults".equals(topic)) {
            if (c.faults < 0) return Hindi.t("I haven't read the fault codes yet. Open the engine box and tap Read fault codes.");
            if (c.faults == 0) return Hindi.t("No fault codes are stored.");
            return Hindi.f("%1$s fault codes are stored. %2$s", c.faults, Hindi.t(FaultCodes.URGENCY[Math.max(0, c.worst)]));
        }
        // engine
        if (c.coolant > -999 && c.coolant >= c.hot) return Hindi.f("The engine is hot, %1$s degrees. Stop when it is safe and let it cool.", c.coolant);
        String temp = c.coolant > -999 ? Hindi.f("Coolant is at %1$s degrees.", c.coolant) : "";
        String faults = c.faults > 0 ? " " + Hindi.f("%1$s fault codes are stored.", c.faults) : "";
        return (Hindi.t("The engine looks fine.") + " " + temp + faults).trim();
    }

    private CarAnswer() {
    }
}
