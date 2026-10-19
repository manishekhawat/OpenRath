# Security

## Reporting a problem

Please report security problems privately to the maintainer rather than in a public issue. Include the version (Settings → Help → Version), the unit, and steps to reproduce. You can expect a reply within a week.

## What the app does to stay safe

- **Theme and backup .zip files** are unpacked only as flat files. Paths, `..` and files over 8 MB are skipped, counting the bytes actually unpacked (a theme is at most 32 MB in all). A backup restores settings and `.png` pictures only.
- **Backups** leave out the valet PIN, the hotspot password and the key-action token. A restore never changes those, valet mode, the update and report addresses or a fleet's managed settings, and it asks first, naming the file and its date.
- **The map** runs in a WebView that loads only the app's own `assets/map` page, served from the made-up address `https://openrath.local`, with a Content-Security-Policy that allows only the map, search, routing, places and weather servers it uses. File access is off; a tapped link to anywhere else opens in the browser. The JavaScript bridge answers only that page, and exposes a fixed set of methods: navigate, navigateOffline, offlineNavName, navMode, setNavMode, online, speak, routeRain, tollRate, parkHere, call (the driver confirms first), saveService, destination, savedPlace, parkLink, bigQr, carBrand, brandWords, serviceFinder and voice.
- **Steering-wheel actions** reach the dashboard only with a random token made once per install, so another app can't answer a call, start an SOS or turn on the hotspot. OpenRath's own broadcasts are sent and received with a signature-level permission.
- **Updates** are installed only when the downloaded APK's SHA-256 matches, its package is OpenRath, it is newer, and it is signed with the installed app's key.
- **Network:** everything OpenRath fetches itself is https only. Plain http is allowed only for online radio streams (many stations have no https), and only http and https station addresses are played.
- **The accessibility service** is optional and off until the owner turns it on. It reads key presses only, keeps only the ones the owner has given an action, and lets everything else through.
- **The notification listener** reads messages from the apps the owner picked, only to show or speak them, and stores nothing.
- **The Topway radio service** is called through reflection with fixed method names. Its code is loaded only when it is part of the firmware (a system app), never from an app installed under the same name; makers are recognised the same way. Steps that ever crashed the app are recorded and skipped afterwards (`Crash.java`).
- There is no server, account or remote configuration.

## Release signing

Signing keys are never in the repository: `signing.json` and keystores are ignored by git, and without them the build signs with a throwaway debug key, so such APKs must not be published as releases. Public releases must be signed with a separate key that is kept offline, with checksums published next to each APK.
