# Privacy

OpenRath has no account, no ads, no analytics and no tracking. It never sends anything to the people who make it.

## What stays on the unit

Trips, driving score, recorded routes (GPX), fuel and toll logs, the parking spot, service reminders, phone favourites, learnt car signals and steering-wheel buttons, OBD-II and tyre readings, crash reports, settings and backups.

- Backups are written to `OpenRath/backup` on the unit's own storage. Anyone with the unit can read them.
- Crash reports are written to `OpenRath/logs` and are only shared if the owner taps **Share**.
- Messages shown by the notification ticker are read on the unit and never stored.

## What is sent, and only when the feature is used

Each of these can be switched off in **Settings → Features**.

| Feature | Sent to | What it receives |
| --- | --- | --- |
| Weather and rain | Open-Meteo (api.open-meteo.com) | Position rounded to about 100 m, or points along a planned route |
| News box | Google News (news.google.com RSS) | Nothing about you or the car; only the topic chosen |
| Air quality | Open-Meteo (air-quality-api.open-meteo.com) | Position rounded to about 100 m |
| Cab SOS (only when you trigger it) | Your two SOS contacts, by SMS from the stereo's SIM | Your name, vehicle number, a Google Maps link to where the car is, and the time |
| Place name for the weather box | BigDataCloud, only when the unit has no built-in geocoder | Position rounded to about 10 m |
| Speed limits, cameras, toll plazas, petrol pumps | Overpass API (OpenStreetMap) | The map area around the car or the route |
| Map | tile.openstreetmap.org | The map tiles on screen, which shows the area being looked at |
| Routes | OSRM demo server (router.project-osrm.org) | Start and destination |
| Place search | Photon (photon.komoot.io) | What was typed or said, and the map centre |
| Voice search | The unit's speech recogniser, usually Google | The spoken words |
| Online radio | radio-browser.info, then the radio station itself | The station list request, and the stream |

Every request goes directly from the unit to that service, so each service also sees the unit's internet address.

## Permissions

| Permission | Why |
| --- | --- |
| Location | Speed, trips, weather, speed limits, the map |
| Storage | Theme packs, backups, GPX export, crash reports, music files |
| Internet | The services above |
| Bluetooth | The OBD-II adapter and tyre sensors |
| Notification access (optional) | What's playing in other apps, and the message ticker |
| Accessibility (optional) | Steering-wheel buttons while another app is on screen. Only key presses are read. |
| Start at boot | Resuming the radio or music after switch-on |
