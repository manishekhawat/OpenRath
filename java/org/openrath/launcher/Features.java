package org.openrath.launcher;

/**
 * Every optional feature, each with its own on/off switch in Settings > Features. Code checks
 * Features.on(prefs, id) before doing the work, so a switched-off feature costs nothing.
 */
final class Features {

    static final class F {
        final String id, name, group, about;
        final boolean def;
        /** The level it belongs to: SIMPLE, FULL or CUSTOM, or DEV for switches only developer mode shows. */
        int level = FULL;

        F(String id, String name, String group, boolean def, String about) {
            this.id = id;
            this.name = name;
            this.group = group;
            this.def = def;
            this.about = about;
        }
    }

    // ---- levels: how much OpenRath shows. Each takes in the one before it; DEV is the hidden developer mode.
    static final int SIMPLE = 0, FULL = 1, CUSTOM = 2, DEV = 3;
    static final String[] LEVELS = {"simple", "full", "custom"};
    static final String[] LEVEL_NAMES = {"Simple", "Full", "Custom"};
    static final String[] LEVEL_ABOUT = {
            "The must-haves: home screen, map, radio, music, phone, reverse lines, speed alerts",
            "Simple plus the helpers: voice, road warnings, highway trips, car care, driving score, cab tools",
            "Full plus making it your own: widgets, extra pages, boot logo, equalizer, driver profiles"};
    /** Off whatever the level until the owner turns them on: they need hardware, or change the home screen. */
    private static final java.util.Set<String> ASK_FIRST = new java.util.HashSet<>(java.util.Arrays.asList("cab", "obd", "dashcam", "tpms", "reports", "androidAuto"));

    private static final String[] SIMPLE_IDS = {"arrows", "navTurns", "parkedSettings", "weather", "news", "ticker", "nightDrive", "speedAlerts", "parking", "reverse", "lightsReminder", "duck", "keys", "longKeys", "resume", "offlineMap", "backup", "reports"};
    private static final String[] CUSTOM_IDS = {"pages", "widgets", "saver", "perf", "bootArt", "soundProfiles", "speedVolume", "eq", "profiles"};
    private static final String[] DEV_IDS = {"maker", "perf"};

    /**
     * Switches that became part of a bigger one: each old id follows its new switch, so code, backups
     * and fleet consoles that use the old ids keep working.
     */
    static final String[][] PARTS = {
            {"parkLink", "parking"}, {"parkPhoto", "parking"}, {"parkTimer", "parking"},
            {"tollAhead", "tolls"},
            {"coach", "fuelPrice"}, {"fuelPlan", "fuelPrice"}, {"fuelCheck", "fuelPrice"},
            {"serviceFinder", "care"}, {"serviceBooking", "care"},
            {"monsoon", "rain"}, {"aqi", "rain"},
            {"waits", "bumps"},
            {"weekly", "startSummary"},
            {"chimes", "lightsReminder"},
            {"offlineVoice", "offlineMap"},
            {"floatMic", "voice"}, {"micOverApps", "voice"}};

    static int levelOf(String name) {
        int i = java.util.Arrays.asList(LEVELS).indexOf(name);
        return i < 0 ? CUSTOM : i;
    }

    /** Whether a feature is part of a level (DEV features never are). */
    static boolean inLevel(F f, int level) {
        return f.level <= level && f.level != DEV;
    }

    /** The switch a feature gets when the owner picks a level. */
    static boolean onAt(F f, int level, boolean now) {
        if (f.level == DEV) return now;
        if (!inLevel(f, level)) return false;
        return ASK_FIRST.contains(f.id) ? now : f.def;
    }

    /** The owner's level; Custom (everything, as before levels) until one is picked. */
    static int level(Prefs p) {
        return levelOf(p.str("level", "custom"));
    }

    static boolean levelPicked(Prefs p) {
        return !p.str("level", "").isEmpty();
    }

    /** Pick a level: its features go back to their usual switch, the rest off. Each can still be changed. */
    static void setLevel(Prefs p, int level) {
        p.put("level", LEVELS[level]);
        for (F f : ALL) {
            if (f.level == DEV) continue;
            set(p, f.id, onAt(f, level, on(p, f.id)));
        }
    }

    static boolean dev(Prefs p) {
        return !Managed.devBlocked(p) && "on".equals(p.str("devMode", "off"));
    }

    /** Whether Settings shows this feature's switch: those of the owner's level, or every one in developer mode. */
    static boolean shown(Prefs p, F f) {
        return dev(p) || inLevel(f, level(p));
    }

    static final F[] ALL = {
            // dashboard
            new F("road", "Moving road under the car", "Dashboard", true, "Dashes move with your speed"),
            new F("arrows", "Turn indicator arrows", "Dashboard", true, "Needs the indicator setup in Car signals"),
            new F("weather", "Weather", "Dashboard", true, "Open-Meteo, every 15 minutes"),
            new F("news", "News box", "Dashboard", true, "Top 5 headlines from Google News, read aloud on a tap"),
            new F("rain", "Weather alerts", "Dashboard", true, "Rain here or on your route, waterlogging spots you've marked, and poor air"),
            new F("pages", "Extra dashboard pages", "Dashboard", false, "Swipe sideways for a second and third set of boxes"),
            new F("widgets", "Widgets from other apps", "Dashboard", true, "Put another app's widget in a box"),
            new F("saver", "Clock screensaver", "Dashboard", false, "Big clock after a few idle minutes while parked"),
            new F("ticker", "Notification ticker", "Dashboard", true, "Messages slide in along the top"),
            new F("navTurns", "Next turn from your navigation app", "Driving", true, "The next turn from Google Maps, Waze and others, in a card on the dashboard"),
            new F("overStrip", "Mini strip over other apps", "Driving", false, "The next turn and what's playing, along the bottom of other apps"),
            new F("readAloud", "Read messages aloud", "Dashboard", false, "Says who sent a message, or the whole message"),
            new F("nightDrive", "Night-drive mode", "Dashboard", true, "After sunset while driving: big red speed, dim red map, nothing else"),
            new F("voice", "Voice commands (Hindi and English)", "Dashboard", true, "\"FM 91.1\", \"call Mom\", \"घर ले चलो\" from the mic"),
            new F("parkedSettings", "Settings only when stopped", "Driving", true, "Settings and their keyboard open when the car is stopped; a passenger can still open them"),
            new F("smoothSpeed", "Smooth speed number", "Driving", true, "The speed eases between GPS readings and shows 0 when standing still, without flicker"),
            new F("places", "Saved places for any navigation app", "Driving", true, "Hold Navigation for Home, Office, the parked car and your own places"),
            new F("tripShare", "Share the trip by QR code", "Driving", true, "A code family scan to see where you are and the route; nothing is sent online"),
            new F("appDrawer", "Better all-apps screen", "Dashboard", true, "Search, A to Z or most used, pinned and recent apps, and a long press to pin, hide or uninstall"),
            new F("dockStatus", "Status icons in the dock", "Dashboard", true, "Wi-Fi, mobile signal, Bluetooth, GPS and battery, for stereos that hide Android's status bar"),
            new F("hourlyWeather", "Hourly weather", "Dashboard", true, "The next 24 hours' temperature and rain in a tall weather box, kept for offline use"),
            new F("calendar", "Calendar box", "Dashboard", true, "Today's and the next few days' events from the unit's calendar"),
            new F("artColour", "Music box colours from the album cover", "Dashboard", true, "The music box takes its colour from the song's cover"),
            new F("seek", "Drag to seek in the music box", "Dashboard", true, "A bar with the time played; drag along it to move in the song (music apps that allow it)"),
            new F("stopSummary", "Drive summary when stopped", "Dashboard", true, "Stopped for 5 seconds: distance, time, average and top speed of this drive in the car box"),
            new F("setup", "Setup screens on first open", "Dashboard", true, "After installing: language, permissions, your car, the look, how much to show, your apps and the home app, step by step"),
            new F("intro", "OpenRath logo when the app opens", "Dashboard", true, "The OpenRath wheel rolls in and the name appears, for two seconds; a tap skips it"),
            new F("autoTheme", "Theme changes by itself", "Dashboard", false, "A theme for the night, and one for when a chosen phone connects"),
            new F("tapFeedback", "Tap sound and buzz", "Dashboard", false, "A short tick and a buzz on each box and dock button"),
            new F("perf", "Performance box", "Dashboard", false, "Frames per second, CPU use, temperature and free memory in a corner"),
            // driving
            new F("speedAlerts", "Speed limit and camera alerts", "Driving", true, "Speaks and beeps near cameras and over the limit"),
            new F("parking", "Parking", "Driving", true, "Saves where you parked, with a photo and a paid-parking reminder"),
            new F("score", "Driving score", "Driving", true, "Harsh braking, hard starts and speeding"),
            new F("achievements", "Badges for good driving", "Driving", true, "Smooth-driving streaks, weeks within the limit and distance milestones"),
            new F("accel", "0–100 km/h timer", "Driving", true, "Times every standing start"),
            new F("gpx", "Record routes (GPX export)", "Driving", true, "Kept 60 days on the unit only"),
            new F("tolls", "Tolls", "Driving", true, "Toll plazas and costs on a planned route, and a countdown (with a FASTag lane reminder in India)"),
            new F("reverse", "Reverse guide lines", "Driving", true, "Shown when reverse gear is engaged"),
            new F("fuelPrice", "Fuel and mileage", "Driving", true, "Trip cost, how far the tank goes, and a leak or theft check"),
            new F("restStop", "Break reminder and rest stops", "Driving", true, "After 2 hours at the wheel, dhabas and pumps ahead"),
            new F("dashcam", "Dashcam", "Driving", false, "Records a USB camera in 1-minute clips; a jolt keeps the clip"),
            new F("zones", "School and hospital alerts", "Driving", true, "Slow down near schools at school times; no horn near hospitals"),
            new F("highway", "Highway mode", "Driving", true, "Above 75 km/h for two minutes: message pop-ups wait, a slim highway strip shows"),
            new F("parkedMusic", "Battery saver for parked music", "Driving", true, "Engine off and music on: pauses it after 20 minutes (reminds once without OBD)"),
            new F("split", "Split the cost by UPI", "Driving", true, "A UPI QR code for each person's share of the petrol or tolls"),
            new F("startSummary", "Daily and weekly summary", "Dashboard", true, "Each morning: weather, petrol and service due. On Sundays: the week's driving"),
            new F("lightsReminder", "Car-signal reminders", "Driving", true, "Headlights after sunset, doors and seat belts, by voice (needs the setup in Car signals)"),
            new F("bumps", "Road learning", "Driving", true, "Learns speed-breakers and how long you wait at signals and tolls, and tells you next time"),
            new F("duck", "Music dips for voice prompts", "Driving", true, "Music lowers while the car speaks a turn or a warning"),
            new F("callScreen", "Call screen over other apps", "Phone", true, "Who is calling, with big Answer and End buttons, on top of whatever app is open"),
            new F("hotspot", "Guest Wi-Fi", "Phone", true, "A QR code passengers scan to join the unit's hotspot"),
            new F("maker", "Stereo maker's services", "Radio and music", true, "Uses your stereo maker's own services for FM, calls, reverse and lights; off: plain Android only"),
            new F("projection", "Phone projection app", "Radio and music", true, "A button in the music box opens your wireless CarPlay or Android Auto app (ZLink, AutoKit and others)"),
            new F("projectionAuto", "Open the projection app when the phone connects", "Radio and music", false, "Your CarPlay or Android Auto app opens by itself when a phone connects by Bluetooth"),
            new F("androidAuto", "Android Auto built in", "Radio and music", false, "Plug in your phone with its cable and Android Auto shows on this screen; needs Headunit Revived installed for its certificate"),
            new F("radioText", "Big frequency from any radio app", "Radio and music", true, "Shows \"FM 98.3\" and the station name from the radio app's text"),
            new F("soundboard", "Soundboard", "Radio and music", false, "A box of fun sounds (horn, fart, applause and more) that plays only when the car is stopped"),
            new F("video", "Video window", "Radio and music", false, "Plays a video from the unit or a USB stick in a box; the picture shows only when the car is stopped"),
            new F("soundProfiles", "Sound profiles", "Radio and music", false, "City, highway and night: equalizer and volume switch by themselves"),
            new F("fog", "Fog mode", "Driving", true, "In thick fog: big speed, a slow guide and the next bend ahead"),
            // car
            new F("view360", "360 view of your car", "Car", true, "Your car in 3D in its shape, colour and number plate, from any side; opens the stereo's 360 camera app when it has one"),
            new F("cam360Turn", "360 camera on turn signals", "Car", false, "Below 15 km/h the indicator opens the stereo's 360 camera app (needs the indicators set up in Car signals)"),
            new F("care", "Car service", "Car", true, "Oil, tyres, service, insurance and PUC reminders, the nearest workshops, and a call to book"),
            new F("obd", "OBD-II engine data", "Car", true, "Needs a small plug-in OBD adapter (Bluetooth or Wi-Fi, often sold as ELM327)"),
            new F("faultWords", "Fault codes in plain words", "Car", true, "Each fault code with what it means, what to check and how urgent it is, from a table kept on the unit"),
            new F("askCar", "Ask the car by voice", "Car", true, "\"How's the engine?\", \"how much fuel?\", \"any faults?\" answered from the engine data"),
            new F("ledStrip", "LED light strip", "Car", false, "A Bluetooth ELK-BLEDOM strip takes the theme's colour, dims at night and turns red for a warning"),
            new F("tpms", "Tyre pressure sensors (TPMS)", "Car", false, "Bluetooth valve-cap sensors"),
            new F("keys", "Steering-wheel buttons", "Car", true, "Your own action for each button"),
            new F("longKeys", "Hold a steering-wheel button", "Car", true, "Each button can do a second action when held down"),
            new F("wallet", "Car documents wallet", "Car", true, "Photos of the RC, insurance, PUC and licence, shown offline"),
            // family
            new F("childLock", "Child-lock for apps", "Family", false, "Chosen apps can't be opened while moving"),
            new F("profiles", "Driver profiles", "Family", false, "Each driver's own boxes, theme, volume and alerts"),
            new F("kids", "Trip games for children", "Family", true, "Spot the colour, number plates, and how far to go"),
            // sound
            new F("wake", "Recover after deep sleep", "Sound", true, "When the stereo wakes from sleep with the ignition, the GPS, music link and map start again"),
            new F("resume", "Resume after switch-on", "Sound", true, "Plays what was on, at the same volume"),
            new F("sourceVolume", "Volume for each music app", "Sound", false, "Each music app, FM and online radio come back at the volume they last played at"),
            new F("speedVolume", "Volume rises with speed", "Sound", false, "Louder on the highway, back down in town"),
            new F("eq", "Equalizer", "Sound", false, "Bass, treble and presets for the app's own sound"),
            new F("quiet", "Quiet hours", "Sound", false, "Late at night: music turned down, no alert beeps"),
            // data
            new F("offlineMap", "Without internet", "Driving", true, "Keeps the map of places you've seen, and uses the offline speech pack, for when there's no Wi-Fi"),
            new F("bootArt", "Boot logo and animation maker", "Dashboard", true, "Your car's badge and name for the stereo's start-up screens"),
            new F("backup", "Daily automatic backup", "Data", true, "Settings, fuel log and trips, kept on the unit"),
            new F("updates", "App updates from GitHub", "Data", false, "Checks once a day for a new OpenRath and offers it when parked"),
            new F("reports", "Crash and usage reports", "Data", false, "Which settings you open and any crashes, kept on the unit for you to share"),
            // cab drivers: one switch for all, then one each
            new F("cab", "Cab driver tools", "Cab", false, "Earnings, fare meter, SOS and more; adds the Cab box and the Cab menu"),
            new F("cabEarnings", "Earnings log", "Cab", true, "Rides and costs; today's and this week's net in the Cab box"),
            new F("cabMeter", "Fare meter", "Cab", true, "Distance, waiting and night charge for the passenger, with a UPI QR to pay"),
            new F("cabApps", "Ride app switch", "Cab", true, "Uber, Ola, Rapido, inDrive and Namma Yatri one tap away"),
            new F("cabPapers", "Permit and badge reminders", "Cab", true, "Permit, fitness, badge, road tax, insurance and PUC, 15 days ahead"),
            new F("cabCng", "CNG finder and fill log", "Cab", true, "Nearest CNG stations, and km per kg from your fills"),
            new F("cabSpots", "Pickup hotspots", "Cab", true, "Where you got the most rides at this hour"),
            new F("cabTarget", "Daily target and duty hours", "Cab", true, "A target bar, and a break reminder after long duty"),
            new F("cabSos", "One-tap SOS", "Cab", true, "Texts your location to two contacts (or calls, without a SIM)"),
            new F("cabQuiet", "Quiet ride", "Cab", true, "Passenger in: music down, messages not read aloud"),
            new F("cabNotes", "Ride notes and lost items", "Cab", true, "Quick notes, and a photo of anything left in the car"),
            new F("cabPhrases", "Phrases for passengers", "Cab", true, "Seat belt, OTP, AC, payment: spoken in Hindi, English or your language"),
    };

    /** Each id, and each old id in PARTS, to its switch; looked up many times a second, so indexed once. */
    private static final java.util.Map<String, F> BY_ID = new java.util.HashMap<>();
    private static final java.util.Map<String, String> PARENT = new java.util.HashMap<>();

    static {
        for (String[] pp : PARTS) PARENT.put(pp[0], pp[1]);
        for (F f : ALL) BY_ID.put(f.id, f);
        for (String[] pp : PARTS) BY_ID.put(pp[0], BY_ID.get(pp[1]));
    }

    static {
        for (String id : SIMPLE_IDS) get(id).level = SIMPLE;
        for (String id : CUSTOM_IDS) get(id).level = CUSTOM;
        for (String id : DEV_IDS) get(id).level = DEV;
    }

    /** The switch that decides `id`: itself, or the bigger switch it became part of. */
    static String parent(String id) {
        String p = PARENT.get(id);
        return p == null ? id : p;
    }

    static F get(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    /** Where the voice mic sits: "map" (only the map's), "float" (a floating button) or "apps" (floating, also over other apps). */
    static String micPlace(Prefs p) {
        String m = p.str("micPlace", "");
        if (!m.isEmpty()) return m;
        // before there was one setting: what the two old switches said
        if ("off".equals(p.str("feat:floatMic", "on"))) return "map";
        return "off".equals(p.str("feat:micOverApps", "on")) ? "float" : "apps";
    }

    static boolean on(Prefs p, String id) {
        if ("floatMic".equals(id)) return on(p, "voice") && !"map".equals(micPlace(p));
        if ("micOverApps".equals(id)) return on(p, "voice") && "apps".equals(micPlace(p));
        id = parent(id);
        String locked = Managed.lockedTo(p, id);
        if (locked != null) return "on".equals(locked);
        if ("speedAlerts".equals(id)) return p.alertsOn();   // kept in the older switch
        F f = get(id);
        if (f == null) return false;   // a misspelt or retired id is off, never quietly on
        return "on".equals(p.str("feat:" + id, f.def ? "on" : "off"));
    }

    static void set(Prefs p, String id, boolean on) {
        id = parent(id);
        if (Managed.lockedTo(p, id) != null) return;   // a fleet's managed settings decide this one
        p.put("feat:" + id, on ? "on" : "off");
        if ("speedAlerts".equals(id)) p.setAlertsOn(on);   // the older switch, kept in step
    }

    private Features() {
    }
}
