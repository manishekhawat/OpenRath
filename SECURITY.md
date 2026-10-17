# Security

## Reporting a problem

Please report security problems privately to the maintainer rather than in a public issue. Include the version (Settings → Help → Version), the unit, and steps to reproduce. You can expect a reply within a week.

## What the app does to stay safe

- **Theme and backup .zip files** are unpacked only as flat files. Paths, `..` and files over 8 MB are skipped. A backup restores settings and `.png` pictures only.
- **The map** runs in a WebView that loads only the app's own `assets/map` page. The JavaScript bridge exposes a fixed set of methods: navigate, speak, park, toll rate and rain text.
- **The accessibility service** is optional and off until the owner turns it on. It reads key presses only, keeps only the ones the owner has given an action, and lets everything else through.
- **The notification listener** reads messages from the apps the owner picked, only to show or speak them, and stores nothing.
- **The Topway radio service** is called through reflection with fixed method names. Steps that ever crashed the app are recorded and skipped afterwards (`Crash.java`).
- There is no server, account or remote configuration.

## Release signing

Signing keys are never in the repository: `signing.json` and keystores are ignored by git, and without them the build signs with a throwaway debug key, so such APKs must not be published as releases. Public releases must be signed with a separate key that is kept offline, with checksums published next to each APK.
