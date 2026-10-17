# Changelog

Newest first.

## 1.33: Ready for Android 14, fleets and translators

- **Android 10 and newer, built for Android 14.** OpenRath now asks for the permissions each Android version names (media, notifications and nearby devices on newer ones), and asks once for access to the OpenRath folder on Android 11 and newer. Units on Android 9 or older stay on 1.32: Android won't offer them this update.
- **Crash and usage reports, off unless you turn them on** (Settings > Backup and about). They count how often OpenRath starts and which settings get opened, kept on the unit with any crash reports for you to read and share under Settings > Backup and about > Usage report. Nothing about where you drive, who you call or what you type. Turning them off forgets everything.
- **Managed settings for fleets:** a device-management console can pick and lock the level, turn features on or off and lock them, hide developer mode, and collect usage reports at the fleet's own https address. Locked switches say "Set by your organisation". See docs/MANAGED.md.
- **Settings text in Android string files** (res/values/ui.xml, and res/values-hi/ui.xml for Hindi), so translators can use the usual tools. The Hindi still needs a native speaker's check.
- Online radio stations that only stream over plain http keep playing.
- For contributors: on-device tests (`build.py androidtest`), GitHub runs them on Android 10 and 14 emulators; tagged releases with checksums; issue and pull request forms; F-Droid listing text; NOTICE and an updated privacy page.

## 1.32: Own release key, Hindi settings, voice in two taps

- **Signed with OpenRath's own release key** instead of the old Dzire theme key. Android won't install it over 1.31 or earlier: back up (Settings > Backup and about > Back up now), uninstall the old one, install 1.32, and say yes to bringing the backup back.
- **Settings in Hindi:** Settings > OpenRath level > Language. Every section, heading, setting and switch; anything not yet translated stays in English. The words are in assets/hi.tsv for anyone to correct.
- **Spoken voice in two taps:** when the stereo has no text-to-speech voice, OpenRath offers once to open "Speech Services by Google" in the Play Store, and Settings > Map and navigation > Spoken voice does it any time. Android doesn't let an app install another app by itself, and Google's voice can't be shipped inside OpenRath.

## 1.31: Simple, Full and Custom

- **OpenRath level** (Settings > OpenRath level, and asked once at first start, with Simple ticked):
  - **Simple:** the must-haves (home screen, weather and news, map, parking spot, radio, music, phone, ticker, reverse lines, steering-wheel buttons, indicator arrows, night-drive, speed and headlight alerts, backup).
  - **Full:** Simple plus the helpers (voice, road warnings, highway trips, driving score, car care, OBD and 360 view, family and cab tools).
  - **Custom:** Full plus making it your own (moving road, widgets, extra pages, screensaver, performance box, boot logo maker, equalizer, sound profiles, speed volume, driver profiles).
- Picking a level puts its switches back to their usual setting and turns the rest off; each one can still be changed under "Turn on or off" in its section. Settings shows only the switches of your level. Cab tools, OBD-II, TPMS and the dashcam stay off until you turn them on. "Not now" keeps everything as it was.
- **Developer mode:** tap Version (Settings > Backup and about) seven times. It shows every switch whatever the level, the stereo maker's services switch, diagnostics, unit and crash reports, the FM tests and the headlight deep search. Turn it off under OpenRath level.

## 1.30: 360 view of your car

- **360 view of your car** (Settings > Car, or say "360"): OpenRath's own 3D model of your car in its shape (hatchback, saloon, SUV or MUV), colour and number plate, with the blue IND strip, and a yellow plate when the cab features are on. Turn it with a finger, or tap Top, Front, Left, Right or Rear. It turns slowly by itself when left alone. Free, with no unlock codes.
- **The stereo's 360 camera app:** OpenRath finds it (360, AVM, panoramic or surround view apps) and opens it from the 360 view, from the voice command, or (new switch, off by default) when you indicate below 15 km/h. Settings > Car lets you pick it if it isn't found. OpenRath can't read the four cameras itself: on these stereos only the maker's own app can.
- The car setup asks for one of four shapes instead of two.
- Badges with holes (rings, letters) now keep their holes in the logo, the car picture and the boot logo. `OPENRATH_PUBLIC=1` builds an APK with no brand packs, for sharing.

## 1.29: FYT and MTCD/Joying add-ons

- **FYT (SYU) units:** FM in the radio box (frequency, RDS name, play and stop, seek, tuning by voice), Bluetooth calls (connected, ringing and talking, the caller's number, call, answer, end or reject), reverse gear and the headlight wire, all through the stereo's own toolkit service (com.syu.ms).
- **MTCD / MTCE units (Joying, Pumpkin, Xtrons, Dasaita):** FM in the radio box through the system's CarManager (frequency, station name, play and stop, seek, tuning), and Bluetooth calls through the stereo's Bluetooth service (call, answer, end, the caller's number). Reverse gear and headlights on these units still use OpenRath's own signal setup.
- Both load only on their make, under the same "Stereo maker's services" switch; with it off, or when the service doesn't answer, OpenRath falls back to plain Android. The codes come from the makers' own apps and open-source projects, and haven't been tried on these units yet.

## 1.28: Any Android stereo

- **Topway is now an optional add-on.** Everything the home screen needs from the stereo (FM in the radio box, calls, reverse gear) goes through one adapter. The plain Android one works on any stereo: the radio box follows the radio app's own media controls, and calls, answer and end go through the system phone (Bluetooth hands-free on most units), with the last calls from Android's call log. On Topway units the Topway add-on takes over these, as before.
- New switch, Settings > Features > **Stereo maker's services** (on by default). Off, OpenRath uses only plain Android, even on a Topway unit, and doesn't touch Topway's apps or services at all.
- Diagnostics says which one is in use.
- OpenRath asks for the phone permissions once (place calls, call state, answer calls) for the plain Android phone.

## 1.27: SCAN finds stations

- **SCAN works with the stock radio:** on units whose radio service never says which station is playing, SCAN now reads the station from the file the stock radio app keeps, and lists the stations the stock radio's own scan found. If OpenRath's own stepping finds nothing, it uses that list. Picking a station asks the radio for it directly before stepping along the band.
- The radio box's frequency follows the stock radio the same way, so it changes when the station does.
- Read aloud tries the voice again a minute after it failed, so installing "Speech Services by Google" works without restarting the stereo.

## 1.26: News aloud over FM, bigger buttons

- **Read aloud works while FM plays:** on Topway units the MCU keeps app sound off the speakers while FM has them, so the news was silent. Reading the news now hands the speakers back for the reading and gives them to FM again after; it is read a sentence at a time, and a message says so if the stereo has no working voice.
- **No radio flash after the first play:** stop no longer closes the stock radio app, and once OpenRath has started it, play and stop only move the sound. Pressing play again within 15 s (if it was silent because the radio app had closed) opens it once more. The first play after switching on still opens it, unless the built-in FM tuner works on the unit.
- Bigger **Read aloud** and **SCAN** buttons.
- More space above the welcome name in the car box.

## 1.25: Own FM tuner, heading-up map

- **Built-in FM tuner (try it):** Settings > Radio and music > Built-in FM tuner drives the unit's FM chip directly, with the same driver calls as the stock radio app, so FM can play with no stock radio app and no screen opening. The test reports each step; if you hear FM, OpenRath keeps using it (play, next and previous seek, SCAN asks the chip for every station, the RDS name). Units that keep the FM driver to system apps say so and stay on the stock radio.
- **Map turns with the car:** the arrow always points up and the map turns around it, navigating or not. The compass button switches to north up and back.
- **Headlight deep search** opens the MCU link in groups of 10 codes (it opened nothing on some units) and says why when it can't. Steps skipped after a crash get another try after each update.
- The radio box no longer shows "Silent? Run Settings > Radio and music > FM radio test".
- Bigger car name and welcome name in the car box.
- **Last 5 calls in the phone box** while a phone is connected: received ↙, dialled ↗ and missed ✕, with how long ago; tap one to call back. The stock Bluetooth app keeps its call list where other apps can't read it, so OpenRath keeps its own from the calls it sees, merged with Android's call log where the unit has one. A small box shows as many as fit.

## 1.24: Cab driver tools

One switch turns them all on (Settings > Cab driver > Cab driver tools, off by default), and each has its own switch below it. Everything stays on the unit. Long-press any box and pick **Cab driver** for the Cab box: today's earnings, rides and net, the target or the running meter. Tap it for the Cab menu.

- **Earnings log:** add a ride (fare, app, cash / UPI / in-app) or a cost (fuel, toll, parking, food, commission, repair). Today, yesterday, this week and the last 30 days, by app and by payment. Ask "aaj kitna kamaya" or "how much did I earn".
- **Fare meter:** distance from the GPS, waiting time while standing, and a night charge from 11 pm to 5 am, on a big screen for the passenger. Stop shows the fare, saves it, and can show a UPI QR code with the amount filled in. Rates are yours to set (default ₹50 for 2 km, then ₹16 a km, ₹2 a minute after 5 minutes' waiting, night +25%). Say "start the meter" or "meter band karo", or put it on a steering-wheel button.
- **Ride app switch:** Uber Driver, Ola Driver, Rapido Captain, inDrive, Namma Yatri and others found on the unit, the last used first.
- **Permit and badge reminders:** permit, fitness certificate, driver badge, road tax, insurance and PUC (the last two shared with the service reminders), said once a day from 15 days before.
- **CNG:** nearest CNG stations in your map app, and a CNG fill log with km per kg and ₹ per km; each fill is also a cost in the earnings.
- **Pickup hotspots:** from where your rides started, the places with the most rides within an hour of now, with the distance; tap to drive there.
- **Daily target and duty hours:** the box shows what's left to the target and says when it's reached; duty starts with the first drive of the day and a break reminder comes after 10 hours (8 to 12, or off).
- **One-tap SOS:** five seconds to cancel, then a text with a Google Maps link to two contacts. Stereos without a SIM call the first contact through the paired phone and show a QR code that sends the text from your phone. Also on a steering-wheel button.
- **Quiet ride:** passenger in: music turns down (30% by default) and messages aren't read aloud; it can start and end with the meter.
- **Ride notes and lost items:** quick notes, and a camera photo of anything left in the car with a note of whose it is.
- **Phrases for passengers:** seat belt, OTP, AC, route, traffic, arrived, payment, belongings and thank you, spoken in Hindi, English, Marathi, Kannada, Tamil, Telugu or Bengali (if the unit has that voice), plus your own.

## 1.23: Radio scan and offline weather

- **No flash when playing FM:** once the stock radio app has answered the radio box, play and stop switch the sound to and from FM with the same MCU messages the radio app sends itself (source 0x0A, release 0x8A), so the radio screen no longer opens. The very first play after switching the stereo on still opens it once, because only the radio app can power up the tuner.
- **SCAN on the radio box:** goes once round the FM band with the radio's own "search next", keeps the stations it stops on, and lists them; tap one to tune it. The list is kept for next time.
- **Weather without internet:** the last 5-day forecast is kept, hour by hour. Offline, the weather box shows the hour that is now from it (temperature, sky, rain this hour), marked "Offline · forecast from 3 h ago", and the day forecasts drop the days gone by.

## 1.22: News

- **News box:** a new box (long-press any box > News) showing the top 5 headlines from Google News India, refreshed every 30 minutes on internet and kept for offline. **Read aloud** reads all five (tap again to stop); tapping a headline reads just that one. Choose top stories, India, world, business, sports, technology or entertainment in Settings > Dashboard > News box shows. Switch: Features > News box.
- **FM frequency, properly:** the stock radio app (com.tw.radio) answers a "radioinfo" question through the radio service with the frequency it is tuned to. The radio box now asks after every next/previous and every 3 seconds, so the number follows the radio whatever changed the station. The box no longer swaps the number for "Seeking…", and the dashboard comes back sooner after the radio app opens to start FM, which shortens the flash.
- **Headlights between set times** (Settings > Car signals > Car picture headlights), like the stock launcher's own "Start and end time" choice. The headlight setup now also listens to every MCU message the unit's command service opens (14 more codes), in case the light switch is one of them, and asks the MCU for its backlight state every 1.5 s the way the stock Settings app does (message 0x102, whose "Auto" brightness mode follows the headlights). Run Settings > Car signals > Headlight setup, deep search, to try it.
- **Copy the stock launcher** (Settings > Help): copies the stock launcher's and radio service's files to Download/OpenRath stock apps and to a USB stick, so how they read the headlights can be checked.
- **"Read the news"** by voice ("aaj ki khabar", "समाचार सुनाओ", "headlines") or from a steering-wheel button.

## 1.21: OpenRath

Dzire Home is now **OpenRath**, a free home screen for any car and any Android car stereo.

- **New name and package** (org.openrath.launcher). It installs as a new app beside Dzire Home. On the first start it offers to bring back the old app's latest backup, and renames the DzireHome folder to OpenRath so themes, dashcam clips, documents and backups come along.
- **Car setup** on the first start, and any time in Settings > Car: brand and model (from a list of 20 brands sold in India, or type your own), fuel (petrol, diesel, CNG, hybrid, electric) and tank size. The name beside the logo, the service centre finder, the fuel words in spoken warnings and the mileage expected before the fuel log knows yours all follow it. Electric switches off the fuel features.
- **Logo:** the OpenRath wheel by default; the Maruti Suzuki badge is still built in and shows when the car is a Maruti Suzuki.
- **Any screen:** sizes fit both the width and the height, so 1280 x 800, ultra-wide 1920 x 720 and portrait screens no longer spill over. Portrait screens stack the boxes with the apps along the bottom; picture layouts are centred. New in Settings > Dashboard: **Size of text and boxes** (80 to 130%) and **Screen way up** (auto, landscape, portrait).
- Backups, crash reports, GPX files and the unit reports are now named openrath-….
- **Works without internet** (each with a switch in Features). With Wi-Fi everything is online as before; without it:
  - **Map:** the map keeps the pictures of places you've seen (up to 400 MB, in OpenRath/maps), so they still show. Settings > Map and navigation shows the size and can clear it.
  - **Search:** finds Home, Office and places you went to before.
  - **Guidance:** Go hands the trip to an offline map app: Organic Maps (free, offline maps of India) if installed, else OsmAnd or Google Maps with an offline area. Pick another in Settings > Map and navigation.
  - **Voice:** asks for Google's offline speech pack, so commands work without internet once English (India) and Hindi are downloaded (Settings > Map and navigation > Voice without internet).
- **Spoken greetings:** the start-up summary (greeting, weather, holidays) and "Welcome back, carrying on where you left off" are spoken as well as shown. One voice engine now serves the whole home screen; messages said before it was ready used to be lost.
- **Car shape:** the car setup asks hatchback or saloon, or SUV or MUV, and the car box shows a matching picture (a new SUV drawing with roof rails and a bigger grille). The emblem on the grille is a plain oval unless a brand badge is built in.
- **Stereo detection:** Diagnostics names the stereo (Topway, FYT, MTCD/MTCE or plain Android). Topway-only settings (FM radio test, FM start frequency, How the stock launcher does it, Unit report) are hidden on other stereos.
- Diagnostics no longer shows the QR test line.
- **Source:** builds on its own (the artwork code now lives in art/lib), signs with a key kept outside the source, and keeps car makers' badges in a private brand pack (brands/README.md) so the public code holds no trademarks.
- **FM frequency after next or previous:** the box kept showing the old frequency after a seek. It now asks the radio service for the new station for a few seconds afterwards, shows "Seeking…" meanwhile, understands more ways units report the frequency (as text, in a list, in a bundle, or as Topway's MCU message), and clears the old station name. If the unit never says where it stopped, the box shows "FM radio" rather than a wrong number.
- **Boot logo and animation maker** (Settings > Themes, with its own switch in Features): makes a still boot logo and a 3.6 s boot animation with your car's badge (or your own logo picture, or the OpenRath wheel rolling in), the brand and the car's name, for this screen or 1024 x 600, 1280 x 720, 1280 x 800 or 1920 x 720. Saved to Download and onto a USB stick when one is plugged in, with the steps for the stereo's factory settings and boot animation app. Android doesn't let apps install them; see docs/BOOT.md.

## 1.20

Fix:

- **QR codes, for real this time.** Decoding your photo showed the code on screen held no text at all. The app's QR library is fine on its own, so the unit's firmware most likely carries its own copy of the same library, which Android used in its place. The library now lives under the app's own name so that can't happen. Settings > Help > Diagnostics has a new "QR test" line: it should say 29 squares.

Batch 5, each with its own switch in Settings > Features:

- **Headlight reminder:** after sunset, once a drive, if the headlights look off. Without a headlight signal it simply reminds you once at dusk.
- **Door and seat-belt reminders:** a voice says "A door is open" or "Please fasten your seat belt" while moving. Set them up in Settings > Car signals > Door setup and Seat-belt setup. Most units only know about doors and belts through a CAN bus decoder box.
- **Fuel leak or theft check:** after a full-tank fill-up, if the mileage since the last full tank is under 75% of your usual, it tells you.
- **Wait-time memory:** learns how long you wait at signals and toll plazas (by time of day), and says "At the stop ahead you usually wait about 2 minutes".
- **Speed-breaker learning:** a spot where you brake hard on three different drives, in the same direction, gets a beep and "Speed breaker ahead" next time. Settings > Driving shows how many it has learnt.
- **Music dips for voice prompts:** music lowers while the car speaks a turn or a warning (players that follow Android's rules; the unit's own radio may not).
- **Parking photo:** with a camera on the unit, a photo is taken when you park, from the dashcam's last minute or a single picture. Say "parking photo" or see Settings > Driving.
- **Weekly report:** on Sundays, km, time at the wheel, petrol and toll spend, average score, and your smoothest and most wasteful drive. Say "weekly report" any time.
- **Guest Wi-Fi:** a QR code passengers point their camera at to join the unit's hotspot. Android doesn't let apps switch the hotspot on, so it opens the hotspot settings first. Say "hotspot".
- **Sound profiles:** city, highway and night sound, each with its own equalizer and volume step, switching by themselves. Off by default; set them in Settings > Radio and music.

## 1.19

Fixes:

- **QR codes now scan.** The codes were correct but small, slightly blurred and shown on a screen dimmed for the night, which phone cameras struggle with. Codes now open full screen at full brightness, with bigger squares, sharp edges and the wide white border scanners expect. Tap the code on the map's parking panel or the accident screen to make it big.
- **Headlights from the screen dimming.** On units that dim the screen with the headlights, the headlight setup now picks the screen brightness (shown as "Screen dimming"). A brightness counts as headlights on when it is nearer the dimmed level, so a fade or a level a step off still reads right. It also reads the backlight's actual level.

New:

- **Mic over other apps:** the floating mic stays on top of maps, music and the radio. Android asks once for "Display over other apps". Tap it and the home screen comes forward to listen.
- **Share the trip by QR code:** say "share my trip", or use Settings > Driving. With a destination on the map, the code opens Google Maps on the phone with the route from where the car is now. Without one, it opens the car's spot. Nothing is sent over the internet.

Batch 4, each with its own switch in Settings > Features:

- **Hill (ghat) road tips:** on a long steep climb or descent (6% or more), a voice gives the hill tips once: a lower gear, engine braking going down, and that traffic climbing up has the right of way.
- **Overtaking warning:** on a two-way road with one lane each way, above 45 km/h, a voice says "Bend ahead. Don't overtake here" when a bend within 250 m hides oncoming traffic.
- **Air quality alerts:** the Indian AQI (CPCB scale, from Open-Meteo) shows next to the place in the weather box. When the air turns Poor or worse, a voice suggests closing the windows and setting the AC to recirculate, at most every three hours. Say "air quality" any time.
- **Car wash reminder:** every 14 days (changeable), only when no rain is forecast for two days. Tap "Car washed today" in Settings > Driving after a wash.
- **Split the cost by UPI:** enter the total (the last fill-up is filled in) and how many people. A UPI QR code shows each person's share; any UPI app scans it with the amount filled in. Your UPI id is asked the first time. Say "split the cost".
- **Paid parking reminder:** choose how long you paid for. A QR code adds a calendar reminder to your phone 10 minutes before it runs out, since the car's screen goes off with the engine. Say "parking timer".
- **Battery saver for parked music:** with an OBD adapter, when the car is parked with the engine off and music playing, it warns and then pauses after 20 minutes (changeable), or at once if the battery is low. Without an adapter it only reminds once, because idling with the AC on looks the same.
- **Start-up summary:** once a day, a short spoken greeting with the weather, rain, poor air, petrol range, the next service due and a holiday within the week. It can be text only.
- **Highway mode:** after two minutes above 75 km/h, message pop-ups and read-aloud wait, and a slim strip shows the average speed and time at the wheel. When you slow down, it says how many messages came in.
- **Long-press steering-wheel buttons:** each learnt button can do a second thing when held down. Set it in Settings > Steering-wheel buttons. Share trip, split the cost and parking reminder are new button actions too.

## 1.18

- **Layout gallery:** Settings > Look now shows a picture of every layout, two to a row like the themes. Tap one to use it.
- **Wide map layout:** like Android Auto. A large map on the left, two cards on the right (the clock and the music or radio by default), and a bar along the bottom with the time, your apps, a mic, all apps and settings. Pick it in Settings > Look > Screen layout. Long-press an app in the bar to change it; the faded button after them adds one (up to six). The map is box 4 and the cards are boxes 2 and 5, so what they show can be changed like any box.
- **Floating mic:** a round mic button on every layout. Drag it anywhere; it stays where you leave it. Tap it for a voice command. On by default; Settings > Home screen switches it off.
- **Performance box:** a new box like the Tyres box, with frames per second, CPU use, the hottest temperature sensor and the slowest frame in the last second. Long-press any box and choose "Show performance here". Numbers turn amber or red when the unit struggles or runs hot. The same figures can also float in the top-left corner of any layout (Settings > Home screen > Performance box, off by default). Where Android hides the whole unit's CPU figure, the app's own is shown.

## 1.17

Ten more features, each with its own switch in Settings:

- **Toll plaza countdown:** about 2 km before a toll plaza, a voice says how far it is and the expected toll (the per-plaza amount in Settings > Driving), with a reminder to keep to the FASTag lane.
- **Wrong-way warning:** if you drive against a one-way street for a few seconds, a beep and a voice warn you. Uses OpenStreetMap's one-way roads.
- **School and hospital alerts:** near a school at school times (7 to 9:30 am and 12:30 to 3:30 pm, not Sundays) above 25 km/h, a voice asks you to slow down. Near a hospital, it asks you not to use the horn.
- **Fog mode:** when the weather says fog (or you say "fog mode", or switch it on in Settings > Driving), the screen shows a big speed, a slow guide speed (40 km/h, changeable), and an arrow for the next bend on this road. A voice says each bend. Tap to close it for the drive.
- **Service booking reminder:** when a service or oil change is within 300 km, and the car is parked, it offers once a day to call your service centre. In the service centre list on the map, tap Mine next to yours, or type its number in Settings > Car.
- **Wheel alignment and balancing** is now a service reminder (every 5,000 km), next to tyre rotation.
- **Accident help:** after a crash-like stop (from 40 km/h or more to nearly still in about a second), the screen asks "Are you OK?" with a 30-second countdown. If nobody taps "I'm OK", it calls your emergency contact through the paired phone, and shows where the car is with a QR code to read out or scan. Set the contact and try it (without calling) in Settings > Driving. Off until switched on.
- **Car documents wallet:** photos of the RC, insurance, PUC and licence, kept inside the app so they show with no network. Add them in Settings > Car > Car documents, and say "documents" or "kagaz dikhao" to show them.
- **Holiday traffic warning:** on festival days and long weekends (and the day before, or two days before Diwali), the first time you reach highway speed a voice warns that highways and toll plazas will be busy. Settings > Driving lists the coming holidays.
- **Trip games for children:** "Spot the colour" for two players, a number-plate hunt for 25 states, and a big "km to go" countdown to where the map is taking you (which you can name, like "Nani's house"). Say "games", or open it from Settings > Family.

Also: fixed the settings text shrinking while you type in the search box. The new voice commands and steering-wheel actions are documents, fog mode and games.

## 1.16

Ten picture layouts, as an alternative to the five boxes. Pick one in Settings > Look > Screen layout. Boxes stays the default, and picking it again goes back.

- **The layouts:** Bubbles, Scenery, Columns, Picture and clock, Strip, Twin dials, Room, Big clock, Lyrics, and Cards and apps. Each is a full-screen picture with round, pill or card parts showing the time, speed, weather, music or radio, and buttons for navigation, apps, radio, Bluetooth and phone. The pictures are drawn by the app's own code, so they're free to share.
- **Show on this layout:** each layout's parts (speed, time, clock, weather, the next days' weather, music, compass, details, driver's name, the car, today's distance) can be switched off one by one.
- **Details list:** on Scenery and Room, choose which details show (temperature, altitude, direction, air pressure, latitude and longitude, tyre pressures from the tyre sensors) and which comes first.
- **Moving road lines:** on Scenery and Twin dials the road's centre line runs at the car's speed while driving. On by default.
- **Background movement:** the picture drifts gently while driving and stays still when parked. Off by default.
- **Phone mirroring button:** adds a button to each layout's bar that opens your mirroring app (chosen the first time). Off by default.
- **Background picture:** the layout's own, your picture from DzireHome/wallpapers, or taking turns every hour.
- **Taps:** the music parts have previous, play/pause and next; tapping the title opens the music or radio app. Dock buttons open their apps, and a long-press chooses the app. A long-press anywhere else opens Settings. Cards and apps has six tiles for your favourite apps.
- Weather now also fetches humidity, wind, air pressure and the next four days for the layouts that show them. The chosen layout belongs to the driver profile.

## 1.15

Nine new features, each with its own switch in Settings:

- **Dashcam:** records a USB camera that the unit lists as a camera, in one-minute clips to DzireHome/dashcam. The oldest clips are deleted when the space you set (2–32 GB) is used up. A harsh brake, the "save clip" steering-wheel button or saying "save the clip" keeps the minute before and the one being recorded in DzireHome/dashcam/saved. Off until switched on.
- **Parking spot to your phone:** Settings > Driving, or say "where did I park", for a QR code that opens the spot in Google Maps. The map's parking panel shows it too.
- **Monsoon mode:** mark the spots that flood from Settings, a steering-wheel button, or by saying "paani bhara" or "waterlogging". When it's raining, or rain is due within the hour (here or on the route), a voice warns 700 m before each one.
- **Quiet hours:** from 10 pm to 6 am (changeable), the music is turned down once to 40 % (changeable) and alert beeps are left out. Spoken warnings still play. Off until switched on.
- **Fuel-low planner:** from your last full tank, the litres added since, the distance driven and your mileage, Settings > Driving shows how much petrol is left. Below 60 km (changeable) it says so, and the map lists the petrol pumps ahead. The tank size is set to 37 litres for the Dzire.
- **Service centre finder:** Settings > Car or say "service centre" for the nearest Maruti Arena and Nexa workshops within 30 km, nearest first, with a Call button when OpenStreetMap has their number.
- **Commute insights:** drives now remember where they started and ended. Settings > Driving > Commute insights finds your usual drives (three or more between the same two places) and shows how long each takes by the half hour you left, with the quickest time. Home and Office saved on the map name the places.
- **Child-lock for apps:** a new Family section. Chosen apps can't be opened while the car is moving, from the app drawer or the dashboard. With the steering-wheel accessibility service on, the lock also sends the screen home if one is opened any other way. Off until switched on.
- **Driver profiles:** each driver keeps their own boxes, theme, volume, sound, alert speed, voice language and message settings. Switch in Settings > Family > Who's driving, or with a steering-wheel button. The fuel log, drives and car setup are shared. Off until switched on.

Also: the kids or valet mode moved to the new Family section, and the mileage coach keeps the last 120 drives.

## 1.14

Six new features, each with its own switch in Settings:

- **Petrol cost of trips:** the petrol price is taken from your latest fill-up in the city you're in (or your own figure), and the trip box shows what today's driving cost. A fill-up with no amount entered is worked out from the litres.
- **Mileage coach:** every drive is recorded (idling, hard starts, time above 90 km/h). Settings > Driving > Mileage coach lists the drives that used the most extra fuel, with the reasons, the cost and a tip.
- **Kids or valet mode:** a PIN-locked mode that caps the volume (30–70 %), warns above a set speed (60–100 km/h), and locks Settings and the box menus. A red badge at the top shows it's on; tap it to end with the PIN.
- **Break reminder and rest stops:** after 2 hours at the wheel (1.5–3 h, adjustable) without a ten-minute stop, a spoken reminder, and the map lists dhabas, cafes, rest areas and petrol pumps up to 25 km ahead (along the route when navigating).
- **Night-drive mode:** after sunset during a drive, everything but the map goes black, the map gets a dim red tint, and the speed is shown very large in deep red. Tap the black area to leave it for that drive. There's a 15-second preview in Settings > Home screen.
- **Voice commands in Hindi and English:** from the map's mic or a steering-wheel button: "FM 91.1", "radio chalao", "call Mom", "papa ko call karo", "take me home", "घर ले चलो", "petrol pump", "dhaba", "gaana chalao", "next", "awaaz kam karo". Anything else is searched on the map. Calls go to your favourites, matching "mummy" or "मम्मी" to "Mom".


- **Friendlier settings:** 16 groups became 8 sections (Home screen, Look, Radio and music, Phone and messages, Driving, Car, Backup and about, Troubleshooting), each with a one-line summary. Every feature's on/off switch now sits at the top of its own section as a real switch, instead of a separate Features list. Each setting shows its current value under its name, and a search box finds any setting by name. Fault-finding reports moved to Troubleshooting.

## 1.12.2

- **Phone box:** shows the last call (name, or number) and when it was. Tap it to call back. The call is picked up from Topway's Bluetooth service, and from Android's call log when allowed.
- **FM radio:** starting FM opens the stock radio app (which is what plays FM) and brings the dashboard straight back to the front, so the radio keeps playing behind it. The FM test also tries the radio app's own services.
- **Headlights:** this unit's radio service has no headlight reading (its light commands only change the screen), and the MCU sends apps nothing, so with no learnt signal the lights now count as on from sunset to sunrise at the car's position, the way the stock apps work out night. A learnt signal still wins.
- **Phone events:** call and connection events from the radio service's command callback (`onBtCallStatus`, `onBtConnectedStatus`) are now read too.
- **Stock app report:** now also covers the Bluetooth app and every home-screen app, and lists their services, receivers and providers.

## 1.12

**Ten features that other car launchers have:**

- **Resume after switch-on:** the last source (FM station, online radio, music files or music app) and the volume come back after a boot or after more than a minute of sleep.
- **Steering-wheel buttons:** learn any button, then pick from 15 actions. Buttons arrive as key presses, or as Topway radio-service messages. An optional accessibility service makes them work when another app is on screen.
- **OBD-II engine data:** works over a Bluetooth ELM327 adapter. Shows RPM, coolant, battery voltage and engine load, and can read and clear fault codes. The engine box warns, out loud, when the engine runs hot, and the car's own speed is used when available.
- **Backup and restore:** settings, logs, history and pictures go to `DzireHome/backup`, with a daily automatic copy (the last 7 are kept).
- **Widgets and pages:** other apps' widgets can go in a box (two widget boxes), and up to three dashboard pages can be switched by swiping.
- **Louder with speed:** the volume goes up one step for every 30, 20 or 15 km/h above 40 km/h, and back down when slower.
- **Notification ticker:** messages from chosen apps slide in along the top, and can be read aloud (only who it's from, or the whole message).
- **Clock screensaver and screen-off mode:** a big clock after idle minutes while parked; the screen can be turned off with the music still playing.
- **Tyre pressure (TPMS):** for Bluetooth valve-cap sensors of the common "TPMS1_" kind, with low-pressure and high-temperature warnings.
- **Equalizer, 0–100 timer and GPX export:** a 5-band equalizer with presets and bass boost; 0–60 and 0–100 km/h times that are spoken, with the best time kept; routes recorded for 60 days and exported as GPX.

**Other changes:**

- **Settings → Features:** an on/off switch for each of 24 features.
- **Open-source groundwork:** GPL-3.0 `LICENSE`, `LICENSES/` for third-party parts, About and licences, Privacy, crash reports saved to `DzireHome/logs` with a Share button, `PRIVACY.md`, `CONTRIBUTING.md`, `SECURITY.md`, and unit tests (`build.py test`).

## 1.11

- Settings → Themes gallery with previews, and five built-in themes: Magma Grey, Light, Minimal, Auto and Gen Z.
