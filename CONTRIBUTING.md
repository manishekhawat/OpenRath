# Contributing to OpenRath

Thank you for helping. Bug reports from real head units are the most useful contribution of all, because every unit is a little different.

## Reporting a bug

Include these:

- The unit's make and model, and its Android version.
- What you did, what you expected, and what happened.
- A photo of **Settings → Help → Diagnostics**.
- If the app crashed, the report from **Settings → Help → Crash reports** (use **Share**).

## Building

You need Java 11 or newer, Python 3 with Pillow, the Inter font (`fonts-inter` on Debian and Ubuntu), numpy, and the tools listed in `build.py`: `android.jar` and `dx.jar` from the Android SDK, and `apktool.jar` and `signer.jar` (`sh tools/fetch_tools.sh`), all in `$THEME_TOOLS`. The artwork is drawn by `art/lib`. Car makers' badges are not part of the source (see `brands/README.md`).

```
THEME_TOOLS=/path/to/tools python3 build.py package   # builds package/dzire-home.apk
THEME_TOOLS=/path/to/tools python3 build.py test      # unit tests
```

## Code style

- Java 8 language level with no lambdas, because `dx` reads only older class files. Use anonymous classes.
- No libraries beyond the Android framework. Everything the app ships is in this repository.
- Sizes are in units of a 600 px tall screen (`Ui.px`). Colours come from `Ui` so themes apply.
- Every optional feature has a switch in `Features.java`, and its code checks `Features.on(prefs, id)`.
- Comments explain why, not what. User-facing text is plain English: short sentences and no jargon.
- Logic that doesn't need the screen, such as parsing or arithmetic, goes in static methods with a test in `tests/`.

## Pull requests

Keep each pull request to one change, run `build.py test`, and say which unit you tried it on. New features need a switch in Settings → Features and a line in `CHANGELOG.md`.

## Licence

By contributing you agree that your work is released under the GNU General Public License, version 3 or later (see `LICENSE`).
