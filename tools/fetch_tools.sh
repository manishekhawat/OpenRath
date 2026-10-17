#!/bin/sh
# Fetch the build tools into ./.tools (or $THEME_TOOLS).
set -e
T=${THEME_TOOLS:-$(dirname "$0")/.tools}
mkdir -p "$T/framework"
curl -sSL -o "$T/apktool.jar" https://github.com/iBotPeaches/Apktool/releases/download/v2.9.3/apktool_2.9.3.jar
curl -sSL -o "$T/signer.jar" https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar
echo "tools in $T"
