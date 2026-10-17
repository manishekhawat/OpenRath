# OpenRath on F-Droid

Notes for getting OpenRath into the F-Droid main repository. Nothing here has been submitted yet.

## What is ready

- The store listing is in `fastlane/metadata/android/en-US/`: `title.txt`, `short_description.txt`, `full_description.txt`, `changelogs/<version code>.txt` and `images/phoneScreenshots/`. F-Droid reads these from the tagged commit, so a new `changelogs/<version code>.txt` goes in with every release (see `RELEASING.md`).
- The screenshots show no car makers' badges or names. Keep it that way when adding new ones.
- The source is GPL-3.0-or-later, and the app uses no libraries beyond the Android framework. The map library (Leaflet), the QR code library and the Inter font are bundled as source with their licences (see `NOTICE.md`).

## The build is not Gradle

F-Droid's usual recipe runs Gradle. OpenRath is built by `build.py` instead (javac, then d8, then apktool, then a signer), so the recipe needs its own build commands:

- Debian packages for the artwork: `fonts-inter`, `python3-pil` and `python3-numpy`.
- `android.jar` (API 34) and `d8.jar` (build-tools 34.0.0) from the build server's Android SDK, copied into `$THEME_TOOLS`.
- apktool 2.9.3, built from source.
- `OPENRATH_PUBLIC=1`, so no brand packs are ever built in. Brand packs are not in the source anyway.

### Things to change before submitting

1. **An unsigned APK.** F-Droid signs the APK itself (or, for a reproducible build, copies OpenRath's own signature onto its build). `OPENRATH_UNSIGNED=1` makes `build.py` stop after apktool and leave `openrath-unsigned.apk`. Done.
2. **No downloaded binaries.** `tools/fetch_tools.sh` downloads `apktool.jar` and `signer.jar` from GitHub. F-Droid doesn't allow prebuilt jars, so apktool comes from a `srclib` built from source, and the signer isn't needed once the APK is unsigned.
3. **aapt2 from the SDK.** The apktool jar carries its own prebuilt `aapt2`. `AAPT2=<path>` makes `build.py` pass it to apktool, so the build server's `build-tools;34.0.0/aapt2` is used instead. Done.
4. **A public repository.** F-Droid can only build from a public git repository with version tags.

### Draft recipe

This would go in `metadata/org.openrath.launcher.yml` in the fdroiddata repository. Check it with `fdroid lint` and `fdroid build -v -l org.openrath.launcher` before opening the merge request.

```yaml
Categories:
  - Navigation
  - Theming
License: GPL-3.0-or-later
AuthorName: Manish Shekhawat
SourceCode: https://github.com/manishekhawat/OpenRath
IssueTracker: https://github.com/manishekhawat/OpenRath/issues
Changelog: https://github.com/manishekhawat/OpenRath/blob/HEAD/CHANGELOG.md

AutoName: OpenRath

RepoType: git
Repo: https://github.com/manishekhawat/OpenRath.git

Builds:
  - versionName: '1.32'
    versionCode: 40
    commit: v1.32
    sudo:
      - apt-get update
      - apt-get install -y fonts-inter python3-pil python3-numpy openjdk-17-jdk-headless
    srclibs:
      - Apktool@v2.9.3
    prebuild:
      - mkdir -p tools/.tools/framework
      - cp $$SDK$$/platforms/android-34/android.jar tools/.tools/
      - cp $$SDK$$/build-tools/34.0.0/lib/d8.jar tools/.tools/
    build:
      - pushd $$Apktool$$ && ./gradlew build shadowJar -x test && popd
      - cp $$Apktool$$/brut.apktool/apktool-cli/build/libs/apktool-cli-all.jar tools/.tools/apktool.jar
      - OPENRATH_PUBLIC=1 OPENRATH_UNSIGNED=1 AAPT2=$$SDK$$/build-tools/34.0.0/aapt2 python3 build.py out
    output: out/openrath-unsigned.apk

AutoUpdateMode: Version
UpdateCheckMode: Tags
UpdateCheckData: build.py|VERSION_CODE, VERSION_NAME = (\d+),|build.py|VERSION_NAME = \d+, '([^']+)'
CurrentVersion: '1.32'
CurrentVersionCode: 40
```

Notes on the recipe:

- `output` names the unsigned APK the new option would write. Change it to whatever name `build.py` uses.
- The Apktool `srclib` needs a matching `srclibs/Apktool.yml` in fdroiddata (one may already exist). The path to the built jar can change between apktool versions.
- For a reproducible build that keeps OpenRath's own signature, add `Binaries: https://github.com/manishekhawat/OpenRath/releases/download/v%v/openrath-v%v.apk` and `AllowedAPKSigningKeys:` with the release certificate's SHA-256 (see `RELEASING.md`). F-Droid then publishes the release APK only when its own build matches it.

## Anti-features

F-Droid marks apps that rely on services or software that aren't free. These apply to OpenRath:

- **NonFreeNet** (uses non-free network services):
  - The News box reads Google News RSS feeds (news.google.com).
  - Voice search and voice commands use the unit's speech recogniser, which on most units is Google's.
  - The map's default dark tiles come from Esri (server.arcgisonline.com). OpenStreetMap tiles are used when Esri's fail.
  - The place name in the weather box comes from BigDataCloud when the unit has no built-in geocoder.
- **Possibly NonFreeAdd** (promotes non-free add-ons): when the unit has no text-to-speech voice, OpenRath offers to open "Speech Services by Google" in the Play Store, and guidance can be handed to Google Maps. Both are optional. F-Droid reviewers may or may not count them.

These are free services and are not anti-features: Open-Meteo (weather, rain, air quality), OSRM (routes), Overpass API and OpenStreetMap tiles (speed limits, cameras, toll plazas, map), Photon and Nominatim (place search) and radio-browser.info (online radio list).

Each of these features can be switched off in Settings, so an owner can turn them all off.

### Stereo makers' add-ons

The Topway, FYT and MTCD/Joying add-ons (`TopwayUnit.java`, `FytUnit.java`, `MtcUnit.java`) talk to services already installed on those stereos. They use only standard Android calls (bound services, binder calls and broadcasts) and reflection. No stereo maker's library or code is part of OpenRath, so they add no non-free dependency. They load only on that make and only while the "Stereo maker's services" switch (developer mode) is on. If F-Droid reviewers still want a flag for apps that talk to a proprietary system service, NonFreeDep is the closest one, but nothing non-free is needed to build or run OpenRath.
