#!/bin/sh
# Fetch the build tools into ./.tools (or $THEME_TOOLS), and check each against its known SHA-256,
# so a changed download stops the build instead of running (and seeing the signing key).
#   apktool.jar   apktool 2.9.3, builds the APK
#   signer.jar    uber-apk-signer 1.3.0, aligns and signs it
#   d8.jar        D8 8.5.35 (Google's R8 release), turns classes into dex; skipped when there is one
#                 already (copied from the Android SDK's build-tools)
#   json.jar      org.json 20240303 (public domain), for the unit tests only: android.jar's org.json is a stub
set -e
T=${THEME_TOOLS:-$(dirname "$0")/.tools}
mkdir -p "$T/framework"

fetch() {   # file url sha256
    if [ -f "$T/$1" ] && echo "$3  $T/$1" | sha256sum -c --status; then return; fi
    curl -fsSL --retry 5 --retry-all-errors -o "$T/$1.part" "$2"
    if ! echo "$3  $T/$1.part" | sha256sum -c --status; then
        echo "$1: the download from $2 doesn't match its SHA-256; not using it" >&2
        rm -f "$T/$1.part"
        exit 1
    fi
    mv "$T/$1.part" "$T/$1"
}

fetch apktool.jar https://github.com/iBotPeaches/Apktool/releases/download/v2.9.3/apktool_2.9.3.jar \
    7956eb04194300ce0d0a84ad18771eebc94b89fb8d1ddcce8ea4c056818646f4
fetch signer.jar https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar \
    e1299fd6fcf4da527dd53735b56127e8ea922a321128123b9c32d619bba1d835
[ -f "$T/d8.jar" ] || fetch d8.jar https://storage.googleapis.com/r8-releases/raw/8.5.35/r8lib.jar \
    4733945987ee0a840fafc34080b135259e01678412e07212b23f706334290294
fetch json.jar https://repo1.maven.org/maven2/org/json/json/20240303/json-20240303.jar \
    3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed
echo "tools in $T"
