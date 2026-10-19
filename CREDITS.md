# Credits

OpenRath is written by Manish Shekhawat. It stands on other people's work, and this page thanks them. Code and assets bundled in the app, with their licences, are listed in [NOTICE.md](NOTICE.md); the online services it uses are in [PRIVACY.md](PRIVACY.md) and in the app under Help > About.

Nothing from the projects below is copied into OpenRath unless NOTICE.md says so: they are where ideas came from, or where we learnt how something works.

## Open-source projects we learnt from

| Project | What it taught us |
| --- | --- |
| fyt-dialer | How FYT stereos' toolkit service (com.syu.ms) answers for the radio, Bluetooth calls and reverse gear, used by the FYT add-on (`FytUnit.java`) |
| honda-customizer | More of the same FYT toolkit service, including the headlight wire |
| MTCD-E_Radio-app | The FM radio on MTCD/Joying stereos through CarManager, used by the MTCD add-on (`MtcUnit.java`) |
| MtcDialer | Bluetooth calls on MTCD stereos through the android.microntek.mtcser service |
| mtccanbus-decompiled (github.com/xbenjii/mtccanbus-decompiled) | How MTC stereos talk to the car |
| Femto Car Launcher (github.com/seijikohara/femto-car-launcher, Apache-2.0) | The app drawer, the status in the dock, saved places, smoothed speed, the calendar box, video when parked, hourly weather and updates from GitHub |

## Apps that inspired features

| App | What it inspired |
| --- | --- |
| Car Nebula, by Ahmed Khaled (carnebula.com) | Fault codes in plain words, asking the car by voice, the theme changing by itself, the LED strip and the driving badges |
| Mini AA (zhuwubx-blip.github.io/mini-aa) | Waking with the screen, the music box taking the album art's colour, seeking in a song, the summary when you stop, and feedback on taps |
| Android Auto | The Wide map layout and the Auto theme |

The Mission control, Electric and Roadster looks are drawn from scratch by OpenRath's own art script. They are in the spirit of launch consoles, electric cars' screens and sports cars, and use no one's logos or artwork.

## Standards and community knowledge

- **OBD-II and the ELM327 adapter:** the SAE J1979 service and PID list, and the ELM327 command set, as documented by its maker and the community.
- **ELK-BLEDOM LED strips:** the 9-byte command format, worked out and shared by people who reverse-engineered the "duoCo Strip" and "Lotus Lantern" apps.
- **Stereo families and screen sizes:** the stores, product pages, XDA forum threads and guides listed under Sources in [docs/STEREOS.md](docs/STEREOS.md).
- **Code of conduct:** adapted from the Contributor Covenant, with Mozilla's enforcement ladder (see [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)).

## Data

- Maps and places: © OpenStreetMap contributors (ODbL).
- Weather and air quality: Open-Meteo.com (CC BY 4.0).
- Online radio stations: the radio-browser.info community directory.

If we've used your work and missed you here, please open an issue and we'll add you.
