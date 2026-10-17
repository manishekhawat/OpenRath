# Translating OpenRath

The words of the Settings screens are Android string resources, so any tool that edits Android strings (Weblate, Android Studio, or a plain text editor) works.

- `res/values/ui.xml` holds the English, exactly as the code uses it, and the `ui_strings` list that ties each name to its English.
- `res/values-hi/ui.xml` holds the Hindi under the same names.
- `res/values/strings.xml` holds the app's name, the steering-wheel service and the managed settings a fleet's console shows.

Anything left out of a translation stays in English, so a new setting never shows blank. OpenRath starts in the stereo's own language when it has a translation, and the owner can switch in Settings > OpenRath level > Language.

## Checking the Hindi

The Hindi was drafted by machine and **still needs a native speaker to check it**. Please read it on a unit, fix anything that sounds wrong or too formal, and keep it short enough to fit the Settings rows. Car and app words that drivers say in English (FM, Bluetooth, OBD, GPS, UPI, FASTag) can stay in English.

## Adding a language

1. Copy `res/values-hi/ui.xml` to `res/values-<code>/ui.xml` (for example `values-ta` for Tamil) and translate the text between the tags. Keep each `name` as it is.
2. Run `python3 build.py test`; it checks that every name in a translation exists in the English file.
3. Open a pull request. The Language setting offering more than English and Hindi is still to do, so say which language you added and the code will follow.

## Adding a setting

Write the English in the code as usual, then add it to `res/values/ui.xml` (a `ui_` name and the English) and to the `ui_strings` list, and its Hindi to `res/values-hi/ui.xml` if you can. The tests check that every feature and section has a Hindi line.

Other screens (the dashboard, dialogs and spoken prompts) are still in the Java code in English. Moving them to resources is planned, section by section.
