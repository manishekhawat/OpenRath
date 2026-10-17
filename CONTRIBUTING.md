# Contributing to OpenRath

Thank you for helping. Bug reports from real head units are the most useful contribution of all, because every unit is a little different.

Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Reporting a bug

Use the issue forms on GitHub: a bug report, a feature request, or a stereo report to tell us how OpenRath runs on your unit. Include these:

- The unit's make and model, and its Android version. OpenRath needs Android 10 or newer.
- What you did, what you expected, and what happened.
- A photo of **Settings → Troubleshooting → Diagnostics**. If Troubleshooting isn't shown, tap **Version** in **Settings → Backup and about** seven times to turn on developer mode.
- If the app crashed, the report from **Settings → Troubleshooting → Crash reports** (use **Share**).

Security problems are reported privately instead (see `SECURITY.md`).

## Building

You need:

- Java 11 or newer (releases are built with Java 17).
- Python 3 with Pillow and numpy, and the Inter font (`fonts-inter` on Debian and Ubuntu).
- From the Android SDK: `android.jar` for API 34 (`platforms;android-34`) and `d8.jar` from build-tools 34 (`build-tools;34.0.0`, in its `lib` folder). An old `dx.jar` still works as a fallback when there is no `d8.jar`.
- `apktool.jar` and `signer.jar`, fetched by `sh tools/fetch_tools.sh`.

Put them all in one folder and point `$THEME_TOOLS` at it (or use `tools/.tools`). The artwork is drawn by `art/lib`. Car makers' badges are not part of the source (see `brands/README.md`).

```
THEME_TOOLS=/path/to/tools python3 build.py package   # builds package/openrath.apk
THEME_TOOLS=/path/to/tools python3 build.py test      # unit tests
THEME_TOOLS=/path/to/tools python3 build.py androidtest out   # out/openrath-test.apk, the on-device tests
```

To run the on-device tests, install both APKs on a unit or emulator and run `adb shell am instrument -w org.openrath.launcher.test/org.openrath.launcher.Smoke`. GitHub runs them on Android 10 and 14 emulators for every pull request.

The argument is the output folder, so `python3 build.py out` builds `out/openrath.apk`. Without `signing.json` the APK is signed with a throwaway debug key. Add `OPENRATH_PUBLIC=1` to leave out any brand packs, for an APK to share.

The APK installs on Android 10 (API 29) and newer, and targets Android 14 (API 34). How releases are made and signed is in [RELEASING.md](RELEASING.md). Settings text lives in Android string files ([docs/TRANSLATING.md](docs/TRANSLATING.md)), and fleets can lock settings ([docs/MANAGED.md](docs/MANAGED.md)).

## Code style

- Java 8 language level with no lambdas, so the code still builds with the `dx` fallback, which only reads older class files. Use anonymous classes.
- No libraries beyond the Android framework. Everything the app ships is in this repository.
- Stereo makers' services are reached only through standard Android calls (bound services, binder calls, broadcasts) and reflection, from the add-on for that make. Never add a maker's own library.
- Sizes are in units of a 600 px tall screen (`Ui.px`). Colours come from `Ui` so themes apply.
- Every optional feature has a switch in `Features.java`, and its code checks `Features.on(prefs, id)`.
- Comments explain why, not what. User-facing text is plain English: short sentences and no jargon.
- Logic that doesn't need the screen, such as parsing or arithmetic, goes in static methods with a test in `tests/`.

## Pull requests

Keep each pull request to one change, run `build.py test`, and say which unit you tried it on. New features need a switch in `Features.java` (shown under "Turn on or off" in its Settings section) and a line in `CHANGELOG.md`. The pull request form asks for each of these.

## Licence

By contributing you agree that your work is released under the GNU General Public License, version 3 or later (see `LICENSE`).
