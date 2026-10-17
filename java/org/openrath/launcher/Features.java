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
    private static final java.util.Set<String> ASK_FIRST = new java.util.HashSet<>(java.util.Arrays.asList("cab", "obd", "dashcam", "tpms"));

    private static final String[] SIMPLE_IDS = {"arrows", "weather", "news", "ticker", "nightDrive", "speedAlerts", "parking", "reverse", "lightsReminder", "duck", "keys", "resume", "offlineMap", "backup"};
    private static final String[] CUSTOM_IDS = {"road", "pages", "widgets", "saver", "perf", "bootArt", "soundProfiles", "speedVolume", "eq", "profiles"};
    private static final String[] DEV_IDS = {"maker"};

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
        return "on".equals(p.str("devMode", "off"));
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
            new F("rain", "Rain warnings", "Dashboard", true, "Rain here, or along your route, in the weather box"),
            new F("pages", "Extra dashboard pages", "Dashboard", false, "Swipe sideways for a second and third set of boxes"),
            new F("widgets", "Widgets from other apps", "Dashboard", true, "Put another app's widget in a box"),
            new F("saver", "Clock screensaver", "Dashboard", false, "Big clock after a few idle minutes while parked"),
            new F("ticker", "Notification ticker", "Dashboard", true, "Messages slide in along the top"),
            new F("readAloud", "Read messages aloud", "Dashboard", false, "Says who sent a message, or the whole message"),
            new F("nightDrive", "Night-drive mode", "Dashboard", true, "After sunset while driving: big red speed, dim red map, nothing else"),
            new F("voice", "Voice commands (Hindi and English)", "Dashboard", true, "\"FM 91.1\", \"call Mom\", \"घर ले चलो\" from the map's mic"),
            new F("floatMic", "Floating mic button", "Dashboard", true, "Drag it anywhere on the home screen; tap for a voice command"),
            new F("tripShare", "Share the trip by QR code", "Driving", true, "A code family scan to see where you are and the route; nothing is sent online"),
            new F("micOverApps", "Mic over other apps", "Dashboard", true, "The floating mic stays on top of maps and music; needs the \"Display over other apps\" permission"),
            new F("perf", "Performance box", "Dashboard", false, "Frames per second, CPU use and temperature in a corner"),
            // driving
            new F("speedAlerts", "Speed limit and camera alerts", "Driving", true, "Speaks and beeps near cameras and over the limit"),
            new F("parking", "Remember where you parked", "Driving", true, "Saves the spot after 90 seconds standing still"),
            new F("score", "Driving score", "Driving", true, "Harsh braking, hard starts and speeding"),
            new F("accel", "0–100 km/h timer", "Driving", true, "Times every standing start"),
            new F("gpx", "Record routes (GPX export)", "Driving", true, "Kept 60 days on the unit only"),
            new F("tolls", "Toll estimates on routes", "Driving", true, "Toll plazas on a planned route, in rupees"),
            new F("reverse", "Reverse guide lines", "Driving", true, "Shown when reverse gear is engaged"),
            new F("fuelPrice", "Petrol cost of trips", "Driving", true, "Today's cost from your city's petrol price"),
            new F("coach", "Mileage coach", "Driving", true, "Which drives used extra fuel, and why"),
            new F("restStop", "Break reminder and rest stops", "Driving", true, "After 2 hours at the wheel, dhabas and pumps ahead"),
            new F("dashcam", "Dashcam", "Driving", false, "Records a USB camera in 1-minute clips; a jolt keeps the clip"),
            new F("parkLink", "Parking spot to your phone", "Driving", true, "A QR code that opens the spot in Google Maps"),
            new F("monsoon", "Monsoon mode", "Driving", true, "In rain, warns before the waterlogging spots you've marked"),
            new F("fuelPlan", "Fuel-low planner", "Driving", true, "How far the petrol will go; pumps ahead when it's low"),
            new F("commute", "Commute insights", "Driving", true, "The quickest time to leave for your usual drives"),
            new F("tollAhead", "Toll plaza countdown", "Driving", true, "\"Toll in 2 km, about ₹100\", with a FASTag lane reminder"),
            new F("wrongWay", "Wrong-way warning", "Driving", true, "Warns if you seem to drive against a one-way street"),
            new F("zones", "School and hospital alerts", "Driving", true, "Slow down near schools at school times; no horn near hospitals"),
            new F("hill", "Hill (ghat) road tips", "Driving", true, "On a long steep climb or descent: lower gear, engine braking, who gives way"),
            new F("overtake", "Overtaking warning", "Driving", true, "On two-way roads, says when a bend just ahead hides oncoming traffic"),
            new F("aqi", "Air quality alerts", "Driving", true, "Open-Meteo; when the air is poor, a reminder to set the AC to recirculate"),
            new F("highway", "Highway mode", "Driving", true, "Above 75 km/h for two minutes: message pop-ups wait, a slim highway strip shows"),
            new F("parkedMusic", "Battery saver for parked music", "Driving", true, "Engine off and music on: pauses it after 20 minutes (reminds once without OBD)"),
            new F("split", "Split the cost by UPI", "Driving", true, "A UPI QR code for each person's share of the petrol or tolls"),
            new F("parkTimer", "Paid parking reminder", "Driving", true, "A QR code that puts a reminder on your phone before the parking runs out"),
            new F("startSummary", "Start-up summary", "Dashboard", true, "Once a day: weather, air, petrol range, service due and holidays"),
            new F("lightsReminder", "Headlight reminder", "Driving", true, "After sunset, once a drive, if the headlights look off (or can't be seen)"),
            new F("chimes", "Door and seat-belt reminders", "Driving", true, "A voice instead of beeps; needs the door or belt setup in Car signals"),
            new F("waits", "Wait-time memory", "Driving", true, "Learns how long you wait at signals and toll plazas, and tells you as you approach"),
            new F("bumps", "Speed-breaker learning", "Driving", true, "A spot where you brake hard on three drives gets a warning next time"),
            new F("duck", "Music dips for voice prompts", "Driving", true, "Music lowers while the car speaks a turn or a warning"),
            new F("parkPhoto", "Parking photo", "Driving", true, "A camera photo when you park, to find the car in a big lot (needs a camera)"),
            new F("weekly", "Weekly report", "Driving", true, "On Sundays: km, time, petrol, tolls, score, best and worst drive"),
            new F("hotspot", "Guest Wi-Fi", "Phone", true, "A QR code passengers scan to join the unit's hotspot"),
            new F("maker", "Stereo maker's services", "Radio and music", true, "Topway, FYT and MTCD/Joying units: FM in the radio box, Bluetooth calls, reverse gear and lights through the maker's own services. Off, or on other stereos: plain Android only (the radio app's controls, the system phone)"),
            new F("soundProfiles", "Sound profiles", "Radio and music", false, "City, highway and night: equalizer and volume switch by themselves"),
            new F("fog", "Fog mode", "Driving", true, "In thick fog: big speed, a slow guide and the next bend ahead"),
            new F("accidentHelp", "Accident help", "Driving", false, "After a crash-like stop, calls your emergency contact unless you tap I'm OK"),
            new F("holidayTraffic", "Holiday traffic warning", "Driving", true, "Before festival and long-weekend drives, warns of busy highways"),
            // car
            new F("view360", "360 view of your car", "Car", true, "Your car in 3D in its shape, colour and number plate, from any side; opens the stereo's 360 camera app when it has one"),
            new F("cam360Turn", "360 camera on turn signals", "Car", false, "Below 15 km/h the indicator opens the stereo's 360 camera app (needs the indicators set up in Car signals)"),
            new F("care", "Service reminders", "Car", true, "Oil, tyres, service, insurance and PUC"),
            new F("fuelCheck", "Fuel leak or theft check", "Car", true, "After a full tank: warns if the mileage dropped sharply"),
            new F("wash", "Car wash reminder", "Car", true, "Every two weeks or as you set, only when no rain is due"),
            new F("obd", "OBD-II engine data", "Car", true, "Needs a Bluetooth ELM327 adapter"),
            new F("tpms", "Tyre pressure sensors (TPMS)", "Car", false, "Bluetooth valve-cap sensors"),
            new F("keys", "Steering-wheel buttons", "Car", true, "Your own action for each button"),
            new F("serviceFinder", "Service centre finder", "Car", true, "Nearest workshops for your car's brand, with a call button"),
            new F("serviceBooking", "Service booking reminder", "Car", true, "When a service is near, offers to call your service centre"),
            new F("wallet", "Car documents wallet", "Car", true, "Photos of the RC, insurance, PUC and licence, shown offline"),
            // family
            new F("childLock", "Child-lock for apps", "Family", false, "Chosen apps can't be opened while moving"),
            new F("profiles", "Driver profiles", "Family", false, "Each driver's own boxes, theme, volume and alerts"),
            new F("kids", "Trip games for children", "Family", true, "Spot the colour, number plates, and how far to go"),
            // sound
            new F("resume", "Resume after switch-on", "Sound", true, "Plays what was on, at the same volume"),
            new F("speedVolume", "Volume rises with speed", "Sound", false, "Louder on the highway, back down in town"),
            new F("eq", "Equalizer", "Sound", false, "Bass, treble and presets for the app's own sound"),
            new F("quiet", "Quiet hours", "Sound", false, "Late at night: music turned down, no alert beeps"),
            // data
            new F("offlineMap", "Map without internet", "Driving", true, "Keeps the map of places you've seen, for when there's no Wi-Fi"),
            new F("offlineVoice", "Voice without internet", "Driving", true, "Uses the offline speech pack when there's no Wi-Fi"),
            new F("bootArt", "Boot logo and animation maker", "Dashboard", true, "Your car's badge and name for the stereo's start-up screens"),
            new F("backup", "Daily automatic backup", "Data", true, "Settings, fuel log and trips to OpenRath/backup"),
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

    static {
        for (String id : SIMPLE_IDS) get(id).level = SIMPLE;
        for (String id : CUSTOM_IDS) get(id).level = CUSTOM;
        for (String id : DEV_IDS) get(id).level = DEV;
    }

    static F get(String id) {
        for (F f : ALL) if (f.id.equals(id)) return f;
        return null;
    }

    static boolean on(Prefs p, String id) {
        if ("speedAlerts".equals(id)) return p.alertsOn();   // kept in the older switch
        F f = get(id);
        return "on".equals(p.str("feat:" + id, f == null || f.def ? "on" : "off"));
    }

    static void set(Prefs p, String id, boolean on) {
        p.put("feat:" + id, on ? "on" : "off");
        if ("speedAlerts".equals(id)) p.setAlertsOn(on);   // the older switch, kept in step
    }

    private Features() {
    }
}
