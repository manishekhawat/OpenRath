# Notice

OpenRath, a free home screen for Android car stereos.

Copyright (C) Manish Shekhawat and the OpenRath contributors.

OpenRath is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See `LICENSE` for the full text (SPDX: GPL-3.0-or-later).

## Additional term: credit

Under section 7(b) of the GNU GPL version 3, anyone who conveys OpenRath or a modified version of it must keep the author attribution "Based on OpenRath by Manish Shekhawat", with a link to https://github.com/manishekhawat/OpenRath, in its About or credits screen, or in its documentation if it has no such screen. This is the only additional term; everything else is the plain GPL.

## Third-party parts

These are bundled in the source or in the APK. Each keeps its own licence, and the licence texts are in `LICENSES/` (and beside the files in `assets/map/`).

| Part | Where | Copyright | Licence |
| --- | --- | --- | --- |
| Leaflet 1.9.4, the map library | `assets/map/leaflet.js`, `leaflet.css` | Volodymyr Agafonkin, CloudMade | BSD-2-Clause (`LICENSES/Leaflet-BSD-2-Clause.txt`, `assets/map/LEAFLET-LICENSE`) |
| leaflet-rotate, map turning for Leaflet | `assets/map/leaflet-rotate.js` | Iván Sánchez Ortega (author), Raruto, Fabien Nicollet and Zsolt Ero (github.com/Raruto/leaflet-rotate) | GPL-3.0 (`LICENSES/leaflet-rotate-GPL-3.0.txt`, `assets/map/LEAFLET-ROTATE-LICENSE`) |
| QR code generator for JavaScript, for the QR codes on the map page | `assets/map/qrcode.js` | Kazuhiko Arase | MIT (`LICENSES/qrcode-generator-MIT.txt`) |
| QR Code generator library (Java), moved to the package `org.openrath.qr` | `java/org/openrath/qr/` | Project Nayuki | MIT (at the top of each file) |
| Android Auto receiver, derived from Headunit Revived / Open Headunit (github.com/andreknieriem/headunit-revived), itself based on headunit (github.com/mikereidis/headunit) | `java/org/openrath/launcher/Aa*.java`, `res/xml/aa_usb.xml` | Michael A. Reid (headunit, 2011-2015), Andre Rinas (Headunit Revived / Open Headunit), and the OpenRath contributors | AGPL-3.0-or-later (`LICENSES/AGPL-3.0.txt`, and at the top of each file); see "Android Auto and the AGPL" below |
| Inter and Inter Display fonts, used to draw the artwork and copied into the APK for the welcome line | Taken from the `fonts-inter` package at build time | The Inter Project Authors | SIL Open Font License 1.1 (`LICENSES/Inter-OFL-1.1.txt`, and `assets/fonts/INTER-LICENSE` in the APK) |

"QR Code" is a registered trademark of DENSO WAVE INCORPORATED.

### Android Auto and the AGPL

OpenRath's built-in Android Auto receiver (the `Aa*.java` files) is written in Java from the Kotlin and C++ of Headunit Revived / Open Headunit, which comes from Michael Reid's headunit. Those projects are under the GNU Affero General Public License, version 3 or later, so these files are too. GPLv3 section 13 lets them be combined with the rest of OpenRath (GPL-3.0-or-later): each part keeps its own licence, and the AGPL's terms also apply to the combined app. In practice: if you convey OpenRath, or let people use a modified version over a network, offer them the source of the whole app, as both licences ask.

No certificate or private key from those projects is in OpenRath's source or APK. Android Auto on the unit borrows the one inside an installed Headunit Revived app, or reads two files the owner copies into `OpenRath/androidauto/` (`cert.pem` and `privkey.pem`).

"Android Auto" is a trademark of Google LLC. OpenRath is not affiliated with or endorsed by Google.

### Map data and online services

Map pictures, places and roads come from online services while the app runs. They are not part of the source or the APK.

- Map data © OpenStreetMap contributors, under the Open Database License (ODbL). Used through tile.openstreetmap.org, Overpass API, Photon, Nominatim and OSRM.
- The dark map tiles are © Esri and its data providers (Esri, HERE, Garmin, OpenStreetMap contributors).
- Weather and air quality by Open-Meteo.com (CC BY 4.0).
- Online radio stations are listed by radio-browser.info.

The map page shows the attributions these services ask for. See `PRIVACY.md` for what each service receives.

## Trademarks

Car makers' names, badges and logos, and stereo makers' names, belong to their owners. They are used in OpenRath only to say which car or stereo something is for. OpenRath is not affiliated with, endorsed by or sponsored by any car maker or stereo maker.

Car makers' badges are never part of OpenRath's source or of the APKs it publishes. They can only be added through a private brand pack, which an owner builds for their own use (see `brands/README.md`). Public builds are made with `OPENRATH_PUBLIC=1`, which leaves every brand pack out.

"OpenRath" and the chariot-wheel mark are the project's own name and logo.
