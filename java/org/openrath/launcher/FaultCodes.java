package org.openrath.launcher;

/**
 * Engine fault codes in plain words, with how urgent each is. A built-in table of the codes Indian
 * cars throw most, then the code's family for the rest. Works offline; a mechanic confirms the cause.
 */
final class FaultCodes {

    static final int SOON = 0, WEEK = 1, STOP = 2;
    static final String[] URGENCY = {
            "Safe to drive; get it checked at the next service",
            "Get it checked this week",
            "Stop when it is safe: driving on can damage the engine"};

    // code, what it means, what to check, urgency
    private static final Object[][] KNOWN = {
            {"P0010", "Camshaft timing actuator circuit", "Oil level and the cam timing solenoid", WEEK},
            {"P0011", "Camshaft timing too far ahead", "Oil level and grade; the cam timing solenoid", WEEK},
            {"P0016", "Crankshaft and camshaft out of step", "Timing chain or belt", STOP},
            {"P0087", "Fuel pressure too low (diesel rail)", "Fuel filter and pump", WEEK},
            {"P0088", "Fuel pressure too high (diesel rail)", "Fuel pressure regulator", WEEK},
            {"P0100", "Air flow sensor circuit", "Air flow sensor plug and wiring", WEEK},
            {"P0101", "Air flow sensor reading out of range", "Air filter, air flow sensor, leaks after it", SOON},
            {"P0102", "Air flow sensor reading low", "Air flow sensor plug; clean or replace the sensor", WEEK},
            {"P0106", "Manifold pressure sensor reading out of range", "MAP sensor and its vacuum pipe", SOON},
            {"P0110", "Intake air temperature sensor circuit", "Sensor plug and wiring", SOON},
            {"P0115", "Coolant temperature sensor circuit", "Sensor plug and wiring", WEEK},
            {"P0117", "Coolant temperature sensor reading low", "Sensor and wiring", WEEK},
            {"P0118", "Coolant temperature sensor reading high", "Sensor and wiring; check the coolant level", WEEK},
            {"P0120", "Throttle position sensor circuit", "Throttle body and its plug", WEEK},
            {"P0121", "Throttle position sensor out of range", "Throttle body; clean it", WEEK},
            {"P0128", "Engine warms up too slowly", "Thermostat stuck open", SOON},
            {"P0130", "Oxygen sensor circuit (front)", "Front oxygen sensor and wiring", SOON},
            {"P0133", "Oxygen sensor slow (front)", "Front oxygen sensor", SOON},
            {"P0134", "No signal from the front oxygen sensor", "Sensor heater fuse and wiring", SOON},
            {"P0135", "Oxygen sensor heater (front)", "Sensor heater fuse; replace the sensor", SOON},
            {"P0141", "Oxygen sensor heater (rear)", "Rear oxygen sensor", SOON},
            {"P0171", "Engine running lean (too much air)", "Air leaks, dirty air flow sensor, weak fuel pump", WEEK},
            {"P0172", "Engine running rich (too much fuel)", "Air filter, leaking injector, oxygen sensor", WEEK},
            {"P0190", "Fuel rail pressure sensor circuit", "Rail pressure sensor and plug", WEEK},
            {"P0201", "Injector circuit, cylinder 1", "Injector 1 plug and wiring", WEEK},
            {"P0202", "Injector circuit, cylinder 2", "Injector 2 plug and wiring", WEEK},
            {"P0203", "Injector circuit, cylinder 3", "Injector 3 plug and wiring", WEEK},
            {"P0204", "Injector circuit, cylinder 4", "Injector 4 plug and wiring", WEEK},
            {"P0217", "Engine overheating", "Coolant level, radiator fan, water pump", STOP},
            {"P0218", "Gearbox overheating", "Gearbox oil level and cooler", STOP},
            {"P0219", "Engine over-revved", "Shift earlier", SOON},
            {"P0234", "Turbo boost too high", "Turbo wastegate and boost sensor", STOP},
            {"P0299", "Turbo boost too low", "Turbo hoses for leaks, the intercooler", WEEK},
            {"P0300", "Random misfire", "Spark plugs, coils, fuel quality", WEEK},
            {"P0301", "Misfire, cylinder 1", "Spark plug and coil on cylinder 1", WEEK},
            {"P0302", "Misfire, cylinder 2", "Spark plug and coil on cylinder 2", WEEK},
            {"P0303", "Misfire, cylinder 3", "Spark plug and coil on cylinder 3", WEEK},
            {"P0304", "Misfire, cylinder 4", "Spark plug and coil on cylinder 4", WEEK},
            {"P0325", "Knock sensor circuit", "Knock sensor plug and wiring", SOON},
            {"P0327", "Knock sensor signal low", "Knock sensor and wiring", SOON},
            {"P0335", "Crankshaft position sensor", "Crank sensor; the engine may cut out", STOP},
            {"P0340", "Camshaft position sensor", "Cam sensor and wiring", WEEK},
            {"P0351", "Ignition coil circuit, cylinder 1", "Coil 1 and its plug", WEEK},
            {"P0352", "Ignition coil circuit, cylinder 2", "Coil 2 and its plug", WEEK},
            {"P0380", "Glow plug circuit (diesel)", "Glow plugs and relay", SOON},
            {"P0400", "Exhaust gas recirculation (EGR) flow", "Clean the EGR valve", SOON},
            {"P0401", "EGR flow too low", "Blocked EGR valve or pipe", SOON},
            {"P0402", "EGR flow too high", "EGR valve stuck open", WEEK},
            {"P0420", "Catalytic converter efficiency low", "Catalytic converter; oxygen sensors", SOON},
            {"P0421", "Catalytic converter warm-up efficiency low", "Catalytic converter", SOON},
            {"P0440", "Fuel vapour (EVAP) system", "Fuel cap, EVAP pipes", SOON},
            {"P0441", "Fuel vapour purge flow", "Purge valve", SOON},
            {"P0442", "Small fuel vapour (EVAP) leak", "Fuel cap seal, EVAP pipes", SOON},
            {"P0443", "Fuel vapour purge valve circuit", "Purge valve and plug", SOON},
            {"P0446", "Fuel vapour vent control", "Vent valve and canister", SOON},
            {"P0455", "Large fuel vapour (EVAP) leak", "Check the fuel cap is tight", SOON},
            {"P0456", "Very small fuel vapour (EVAP) leak", "Fuel cap seal", SOON},
            {"P0480", "Radiator fan circuit", "Fan fuse and relay; watch the temperature", WEEK},
            {"P0500", "Vehicle speed sensor", "Speed sensor; the speedometer may be wrong", WEEK},
            {"P0505", "Idle speed control", "Clean the throttle body", SOON},
            {"P0506", "Idle speed too low", "Throttle body, air leaks", SOON},
            {"P0507", "Idle speed too high", "Air leaks, throttle body", SOON},
            {"P0520", "Oil pressure sensor circuit", "Check the oil level now", STOP},
            {"P0524", "Oil pressure too low", "Check the oil level now", STOP},
            {"P0560", "System voltage", "Battery and alternator", WEEK},
            {"P0562", "Battery voltage low", "Battery, alternator belt", WEEK},
            {"P0563", "Battery voltage high", "Alternator regulator", WEEK},
            {"P0601", "Engine computer memory error", "Engine computer", WEEK},
            {"P0606", "Engine computer processor fault", "Engine computer", WEEK},
            {"P0700", "Gearbox control fault", "Read the gearbox computer's own codes", WEEK},
            {"P0705", "Gear selector sensor", "Gear position switch", WEEK},
            {"P0715", "Gearbox input speed sensor", "Gearbox speed sensor", WEEK},
            {"P0720", "Gearbox output speed sensor", "Gearbox speed sensor", WEEK},
            {"P0740", "Torque converter clutch", "Gearbox oil and solenoid", WEEK},
            {"P0741", "Torque converter clutch stuck off", "Gearbox oil and solenoid", WEEK},
            {"P2002", "Diesel particulate filter efficiency low", "DPF; a long highway drive may clean it", SOON},
            {"P2135", "Throttle sensors disagree", "Throttle body and accelerator pedal", STOP},
            {"P2138", "Accelerator pedal sensors disagree", "Accelerator pedal sensor", STOP},
            {"P242F", "Diesel particulate filter blocked with ash", "DPF service", WEEK},
            {"P2463", "Diesel particulate filter soot build-up", "A long highway drive, or a forced regeneration", WEEK},
            {"U0100", "Lost contact with the engine computer", "Wiring and the engine computer", WEEK},
            {"U0101", "Lost contact with the gearbox computer", "Wiring and the gearbox computer", WEEK},
            {"U0121", "Lost contact with the ABS computer", "ABS fuse and wiring", WEEK},
            {"U0140", "Lost contact with the body computer", "Body computer fuse and wiring", SOON},
            {"C0035", "Front left wheel speed sensor", "ABS sensor; ABS may be off", WEEK},
            {"C0040", "Front right wheel speed sensor", "ABS sensor; ABS may be off", WEEK},
            {"C0045", "Rear left wheel speed sensor", "ABS sensor; ABS may be off", WEEK},
            {"C0050", "Rear right wheel speed sensor", "ABS sensor; ABS may be off", WEEK},
            {"B0001", "Driver airbag circuit", "Airbag system; it may not open in a crash", WEEK},
            {"B0002", "Passenger airbag circuit", "Airbag system; it may not open in a crash", WEEK},
    };

    /** {meaning, what to check, urgency} for a code; never null. */
    static Object[] look(String code) {
        String c = code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
        for (Object[] k : KNOWN) if (k[0].equals(c)) return new Object[]{k[1], k[2], k[3]};
        return new Object[]{family(c), "Ask a mechanic to read this code for your car", c.startsWith("B") ? SOON : WEEK};
    }

    /** What part of the car a code points at, from its letter and first digits. */
    static String family(String c) {
        if (c.length() < 5) return "Unknown code";
        char sys = c.charAt(0);
        if (sys == 'C') return "Chassis: brakes, ABS or steering";
        if (sys == 'B') return "Body: airbags, seats, lights or locks";
        if (sys == 'U') return "Network: the car's computers can't talk to each other";
        if (sys != 'P') return "Unknown code";
        if (c.charAt(1) != '0') return "Maker's own engine or gearbox code";
        switch (c.charAt(2)) {
            case '0': case '1': case '2': return "Fuel and air mixture";
            case '3': return "Ignition or misfire";
            case '4': return "Emissions control";
            case '5': return "Speed, idle or battery";
            case '6': return "Engine computer";
            case '7': case '8': case '9': return "Gearbox";
            default: return "Engine";
        }
    }

    /** "P0301  Misfire, cylinder 1\n   Check: … · Get it checked this week", translated. */
    static String explain(String code) {
        Object[] l = look(code);
        return code + "  " + Hindi.t((String) l[0]) + "\n   " + Hindi.f("Check: %1$s", Hindi.t((String) l[1]))
                + "\n   " + Hindi.t(URGENCY[(Integer) l[2]]);
    }

    /** The most urgent of the stored codes, or -1 when there are none. */
    static int worst(java.util.List<String> codes) {
        int w = -1;
        for (String c : codes) w = Math.max(w, (Integer) look(c)[2]);
        return w;
    }

    private FaultCodes() {
    }
}
