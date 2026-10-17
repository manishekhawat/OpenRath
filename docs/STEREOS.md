# Android car stereos and OpenRath support

Checked on 14 Oct 2026 against the OpenRath 1.21 code, not on the real units. Only a Topway unit (Tech's) has run the app. Items marked (u) could not be confirmed from a public page.

## How OpenRath fits a screen

- **Size:** OpenRath scales everything by `min(height/600, width/1024)`, and the user can adjust it from 80% to 130% with the "Size of text and boxes" setting.
- **Tall screens:** when the screen is taller than it is wide, the dashboard boxes stack.
- **Android versions:** the app installs on Android 8.0 and newer (minSdk 26, targetSdk 34).
- **Works on every Android stereo:** dashboard, map with offline tiles, music, voice (with Google speech services installed), car setup for any brand, backups, trip QR and boot logo files.
- **Topway only:** FM tuner box, reverse camera hook and steering keys through `com.tw.service.xt`. On other stereos these rows are hidden and the stereo's own radio app is used.

## The list

| Stereo family | Sizes | Resolution | Android | Works with OpenRath? |
|---|---|---|---|---|
| Topway TS7 / TS10 / TS18 (`com.tw.*`, seen on Tech's unit) | 7, 9, 10.1, 12.3" | 1024x600, 1280x720, 2000x1200 (u) | 8.1 to 14 | **Yes, fully.** FM, reverse and keys included |
| FYT: Joying, Dasaita, Junsun, AliExpress units (`com.syu.ms`) | 7, 9, 10.1, 12.3" | 1024x600, 1280x720, 2000x1200, 1920x720 | 10 to 13 | **Yes.** No FM box yet, which needs a FYT adapter |
| Teyes CC3 / CC3 2K / CC4 Pro | 9, 10.2, 10.36" (CC3 2K) | 1280x720, 2000x1200, 2K AMOLED (CC4 Pro) | 10 to 13 | **Yes.** No FM box |
| MTCD / MTCE (PX5, PX6, PX30): Xtrons, Eonon, Pumpkin, older units (`android.microntek.*`) | 7, 9, 10.1" | 1024x600, 1280x720 (u) | 6 to 10 | **Yes.** No FM box; an MTC adapter is possible later |
| ATOTO S8 Gen2 | 7, 10.1" | 1280x720 (u) | 10 | **Yes.** No FM box |
| ATOTO X10 | 7, 10, 12.9" | 1280x720 (7") | 13 | **Yes.** No FM box |
| Tesla-style vertical screens | 9.7 to 13.6" | 768x1024, 1080x1920 | 10 to 13 | **Yes**, using the stacked portrait layout |
| 12.3" ultrawide (FYT, Topway, Road Top) | 12.3" | 1920x720 | 13 | **Yes.** The boxes stretch to fill the extra width |
| Woodman India: Nebula, X9 Pro, Spectra; older models | 9, 10" | 2000x1200; older 1280x720 | 13; older 10 | **Yes.** No FM box |
| Other Indian rebrands: Blaupunkt India, Modorwy, myTVS, Sansui, DHC, JXL, etc. | 9, 10" | 1024x600, 1280x720 | 10 to 13 | **Yes**, as long as the unit runs Android. FM box only if the board is Topway |
| Sony XAV-AX, Pioneer DMH, Kenwood | 6.2 to 7" | 800x480 | Not Android | **No.** These units only mirror a phone (Android Auto / CarPlay), so apps can't be installed on them |

## Caveats

- A "2K" or "Full HD" label on a cheap unit sometimes turns out to be 1280x720. OpenRath scales either way.
- Voice commands need Google speech services (the Google app) on the stereo. Some no-name ROMs ship without them.
- Very old units on Android 5 or 6 with 1 GB of RAM will run the app, but the map may be slow.

## Sources

- teyesglobal.com/products/cc4-pro
- atotodirect.com (ATOTO X10 product page)
- deviceatlas.com (ATOTO S8G2A78U)
- hiwoodman.com (Woodman Nebula)
- androidcarstereo.in/collections/woodman
- android-headunits.com (which-mtc-is-my-android-headunit, double-din-tesla-style, uis7870-processor)
- github.com/xbenjii/mtccanbus-decompiled
- XDA forum threads: FYT UIS7862 Q&A, Topway TS10 Q&A, Hal9k MTC ROM, modded syu.ms
- team-bhp.com Android head unit buying guide
- accio.com (Topway TS18)
- Amazon listings for the Teyes CC3 2K and ATOTO X10
