# Privacy

OpenRath has no account, no ads, no analytics and no tracking. It never sends anything to the people who make it.

## What stays on the unit

Trips, saved places, driving score, recorded routes (GPX), fuel and toll logs, the parking spot, service reminders, phone favourites, learnt car signals and steering-wheel buttons, OBD-II and tyre readings, crash reports, settings and backups.

- These are in the `OpenRath` folder on the unit's shared storage, where anyone with the unit, and apps allowed to read files, can read them:
  - backups, in `OpenRath/backup` (they leave out the valet PIN and the hotspot password);
  - routes exported as GPX, in `OpenRath/gpx`;
  - dashcam clips, in `OpenRath/dashcam`, when the dashcam is on;
  - map tiles kept by versions before 1.43, in `OpenRath/maps/tiles` (read until cleared; new ones are kept in the app's private storage);
  - themes, and pictures of items left in a cab.
- Crash reports are kept in the app's private storage, with web addresses and position-like numbers removed, and are only shared if the owner taps **Share**.
- Messages shown by the notification ticker are read on the unit and never stored.
- Android's own cloud backup is turned off for OpenRath, so its data isn't copied to a Google account.

## Opt-in reports

Crash and usage reports are off by default (Settings > Backup and about, "Crash and usage reports"). When turned on, OpenRath counts how often it starts and which settings are opened, and keeps that with any crash reports on the unit until the owner shares them from Settings > Backup and about > Usage report. On a unit managed by a fleet, the fleet's managed settings can also send the report once a day to the fleet's own https address ([docs/MANAGED.md](docs/MANAGED.md)). A report never holds a position, a name, a number or anything typed. Turning reports off forgets everything counted.

## What is sent, and only when the feature is used

Each of these can be switched off under **Turn on or off** in its Settings section.

| Feature | Sent to | What it receives |
| --- | --- | --- |
| Weather and rain | Open-Meteo (api.open-meteo.com) | Position rounded to about 100 m, or points along a planned route |
| News box | Google News (news.google.com RSS) | Nothing about you or the car; only the topic chosen |
| Air quality | Open-Meteo (air-quality-api.open-meteo.com) | Position rounded to about 100 m |
| Cab SOS (only when you trigger it) | Your two SOS contacts, by SMS from the stereo's SIM | Your name, vehicle number, a Google Maps link to where the car is, and the time |
| Place name for the weather box | BigDataCloud (api.bigdatacloud.net), only when the unit has no built-in geocoder | Position rounded to about 10 m |
| Speed limits, cameras, toll plazas, petrol pumps | Overpass API (overpass-api.de, OpenStreetMap) | The map area around the car or the route |
| Map | CARTO (basemaps.cartocdn.com) for the light and night maps, Esri (server.arcgisonline.com) if CARTO's tiles don't load, then tile.openstreetmap.org | The map tiles on screen, which shows the area being looked at |
| Routes | OSRM demo server (router.project-osrm.org), and when it is busy or down the OSM Germany route server (routing.openstreetmap.de) | Start and destination |
| Place search | Photon (photon.komoot.io), and Nominatim (nominatim.openstreetmap.org) if Photon doesn't answer | What was typed or said, and the map centre (Photon only) |
| Voice search | The unit's speech recogniser, usually Google | The spoken words |
| Online radio | radio-browser.info, then the radio station itself | The station list request, and the stream |
| App updates from GitHub (off until switched on) | GitHub (github.com, and its download servers) | Nothing about you or the car; only the request for update.json and the APK, once a day |

Every request goes directly from the unit to that service, so each service also sees the unit's internet address.

### Handed to other apps

Some things open another app, which then follows its own privacy policy. OpenRath sends nothing itself.

- **Guidance in another app:** Google Maps, Organic Maps or OsmAnd receive the destination when guidance is handed to them, and the saved place you tap in Saved places.
- **Spoken voice:** when the unit has no text-to-speech voice, OpenRath offers to open "Speech Services by Google" in the Play Store.
- **QR codes** (parking spot, calendar reminder, UPI payment, SOS text) are drawn on the unit. Nothing is sent until a phone scans one and opens the link.

## Data safety summary

- **Data collected:** none. Nothing is sent to the people who make OpenRath, and there is no server of ours to send it to.
- **Data shared:** only with the services in the table above, only when that feature is used, and only what the table lists.
- **Encryption in transit:** every service above is reached over HTTPS. The one exception can be online radio: some stations only stream over plain HTTP, and that stream is not encrypted. The station list itself comes over HTTPS.
- **Data deletion:** uninstalling OpenRath, or clearing its data in Android's settings, removes everything it kept. Files in the `OpenRath` folder (backups in `OpenRath/backup`, dashcam clips, GPX files, themes, older map tiles) belong to the owner and stay until the owner deletes them.

## Permissions

Android asks the owner for most of these. Saying no turns off only the features that need it.

| Permission | Why |
| --- | --- |
| Location | Speed, trips, weather, speed limits, the map. It is also used while the home screen is in the background, during a drive, with a notification showing. |
| Storage (Android 12 and older) | Theme packs, backups, GPX export, crash reports, music files |
| Music and audio, Photos and images (Android 13 and newer) | Music files, theme pictures and your own logo picture |
| All files access (Android 11 and newer) | The `OpenRath` folder on the unit's storage, which holds themes, backups, music and dashcam clips |
| Notifications (Android 13 and newer) | The notification Android needs while music plays or a drive is being recorded |
| Nearby devices (Android 12 and newer) | Bluetooth: the OBD-II adapter, tyre sensors and an LED light strip you choose. It is never used to find your location. |
| Bluetooth (Android 11 and older) | The OBD-II adapter, tyre sensors and an LED light strip you choose |
| Phone | Calling, answering and ending calls from the phone box, and knowing when a call is ringing |
| Call logs | The last calls in the phone box |
| SMS | Cab SOS, only when you trigger it |
| Camera | The dashcam, and photos of items left in the car |
| Microphone (optional) | Listening for a voice command in a small bar on the dashboard. The speech service on the unit turns it into words; OpenRath keeps no recording. Without it, voice uses the speech app's own screen. |
| Contacts (optional) | Finding someone to call by name in the phone box, and saying who is calling. Read on the unit and never stored or sent. |
| Calendar (optional) | The calendar box: today's and the next few days' events, read on the unit and never stored |
| Videos (Android 13 and newer) | The video box |
| Wi-Fi state | The Wi-Fi icon in the dock |
| Install and remove apps | Installing a downloaded OpenRath update after you confirm, and Uninstall in the all-apps screen. Android asks you each time. |
| Display over other apps (optional) | The floating mic button, the mini strip with the next turn and music, and the call screen, on top of other apps |
| See all apps | The app drawer and picking apps for buttons, which is what a home screen needs |
| Internet | The services above |
| Notification access (optional) | What's playing in other apps, the message ticker and replies to messages, and the next turn from your navigation app |
| Accessibility (optional) | Steering-wheel buttons while another app is on screen. Only key presses are read. |
| Start at boot | Resuming the radio or music after switch-on |
