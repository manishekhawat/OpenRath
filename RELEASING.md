# Releasing OpenRath

Public releases are built by GitHub Actions (`.github/workflows/release.yml`) when a version tag is pushed. If the release key is set up on GitHub the job signs and publishes the release; if not, the job passes with a note and the APK is signed offline on the computer that holds the key ("Signing offline" below). No release is ever signed with the debug key.

## Making a release

1. In `build.py`, raise `VERSION_CODE` by one and set `VERSION_NAME`, for example `VERSION_CODE, VERSION_NAME = 41, '1.33'`. Android only installs an update with a higher version code.
2. Add an entry at the top of `CHANGELOG.md` headed `## 1.33: what is new`. The release notes are taken from this section.
3. Add `fastlane/metadata/android/en-US/changelogs/41.txt` (the new version code) with a short summary, 500 characters at most, for F-Droid.
4. Run `python3 build.py test` and try the build on a unit.
5. Commit, then tag the commit and push the tag:

   ```
   git tag -a v1.33 -m "OpenRath 1.33"
   git push origin v1.33
   ```

The tag must be `v` followed by `VERSION_NAME`, or the job stops. The job then:

- runs the unit tests,
- builds the public APK with `OPENRATH_PUBLIC=1` (no brand packs),
- signs it with the release key,
- makes a source zip of the tagged commit,
- writes `SHA256SUMS` for both files,
- writes `update.json` (the version, the APK's link and its SHA-256), which "App updates from GitHub" reads,
- deletes the key from the build machine,
- and publishes a GitHub Release with the APK, the source zip and `SHA256SUMS`.

If something goes wrong, delete the release and the tag on GitHub, fix it, and tag again.

## Signing offline

The key can stay off GitHub altogether. Without the four secrets, the release workflow builds nothing and passes, leaving a note. Then, on the computer that holds the key, with `signing.json` beside `build.py` holding the keystore's path and alias:

```
read -rs OPENRATH_STORE_PASS && export OPENRATH_STORE_PASS   # type the password; it isn't shown
OPENRATH_PUBLIC=1 python3 build.py out
mv out/openrath.apk openrath-vX.Y.apk
git archive --format=zip --prefix=openrath/ -o openrath-vX.Y-source.zip vX.Y
sha256sum openrath-vX.Y.apk openrath-vX.Y-source.zip > SHA256SUMS
python3 tools/update_json.py openrath-vX.Y.apk > update.json
unset OPENRATH_STORE_PASS
```

`signing.json` can hold the passwords too (`storePass`, `keyPass`), but then they sit in a file. Either way `build.py` hands them to the signer on its standard input, not on its command line, where any program on the computer could read them. Set `OPENRATH_KEY_PASS` as well if the key's password differs from the keystore's.

Then on GitHub, open Releases > Draft a new release, pick the tag, paste the CHANGELOG section and the certificate's SHA-256 (`keytool -printcert -jarfile openrath-vX.Y.apk`), and attach the four files. Units with "App updates from GitHub" switched on read `update.json` from the latest release, so attach it to every release. They can only read it while the repository is public.

## The release key

The release key is a Java keystore that belongs to the project. Every release must be signed with the same key, or Android won't install the update over the installed app.

- Keep the keystore and its passwords offline, and back them up in two separate places (for example two encrypted USB sticks kept apart). Don't keep them in a synced or shared folder, or next to the source.
- Never commit it. `signing.json` and `*.keystore` are in `.gitignore`.
- If the key is lost, users have to uninstall and install again, losing their settings unless they have a backup. If the key leaks, say so publicly and move to a new key.

To make a key once:

```
keytool -genkeypair -keystore openrath-release.keystore -alias openrath -keyalg RSA -keysize 4096 -validity 10000
```

### Setting the secrets

The secrets go in an environment, not in the repository, so that only the release job can read them:

1. In the GitHub repository, open Settings > Environments > New environment and name it `release`.
2. Under Deployment branches and tags, choose Selected branches and tags and add the tag rule `v*`. Optionally add yourself under Required reviewers, so every release waits for your approval.
3. Under Environment secrets, add these four:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_B64` | The keystore file in base64 |
| `RELEASE_STORE_PASS` | The keystore password |
| `RELEASE_KEY_ALIAS` | The key's alias, for example `openrath` |
| `RELEASE_KEY_PASS` | The key's password |

To get the base64 text:

```
base64 -w0 openrath-release.keystore > keystore.b64      # Linux
base64 -i openrath-release.keystore -o keystore.b64      # macOS
```

Paste the contents of `keystore.b64` into the secret, then delete `keystore.b64`. If any secret is missing, the release job builds nothing and passes with a note, and the release is signed offline as above. It never signs with the debug key.

## Checking a download

Download the APK and `SHA256SUMS` from the same release into one folder, then:

```
sha256sum -c SHA256SUMS --ignore-missing
```

It should say `OK` for the APK. Then check who signed it, with `apksigner` from the Android SDK build-tools:

```
apksigner verify --print-certs openrath-v1.33.apk
```

The line `Signer #1 certificate SHA-256 digest` must match OpenRath's release certificate:

```
88:E2:18:0C:76:5C:A0:77:E6:6B:15:DE:ED:BB:8C:9E:C1:6D:17:B9:A6:26:D4:29:32:09:99:48:FA:BB:C2:D5
```

(`apksigner` prints it without colons, in lower case: `88e2180c765ca077e66b15deedbb8c9ec16d17b9a626d42932099948fabbc2d5`.) `keytool -printcert -jarfile openrath-vX.Y.apk` shows the same digest, with colons.

Each release's notes also show the certificate it was signed with. If the two don't match, don't install the APK, and report it as described in `SECURITY.md`.

## Reproducible builds

Anyone can build the same APK from the source zip. Use the same tools as the release job:

- Java 17 (Temurin)
- Android `platforms;android-34` (`android.jar`) and `build-tools;34.0.0` (`d8.jar`)
- apktool 2.9.3 and uber-apk-signer 1.3.0 (`sh tools/fetch_tools.sh` fetches both and checks their SHA-256)
- Python 3 with Pillow and numpy, and the Inter font as packaged in Ubuntu 24.04 (`fonts-inter`)

Build with `OPENRATH_PUBLIC=1 python3 build.py out`. Brand packs are never included in public builds.

Your APK is signed with your own key, so its signature differs. To compare, look at the contents rather than the signature, for example by unzipping both APKs and comparing `classes.dex`, `resources.arsc` and the files under `assets/`. Pictures drawn by `art/lib` depend on the versions of Pillow, numpy and the font, so use the same Ubuntu release as the job if they differ.
