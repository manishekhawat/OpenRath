#!/usr/bin/env python3
"""Writes update.json for a release: the file OpenRath's "App updates from GitHub" reads once a day.

    python3 tools/update_json.py openrath-v1.38.apk owner/repo > update.json

It names the version in build.py, where the APK is in the release, and the APK's SHA-256, which the
app checks before it offers to install.
"""
import hashlib
import json
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent.parent


def main(apk, repo):
    code, name = re.search(r"VERSION_CODE, VERSION_NAME = (\d+), '([^']+)'", (HERE / 'build.py').read_text()).groups()
    p = pathlib.Path(apk)
    digest = hashlib.sha256(p.read_bytes()).hexdigest()
    print(json.dumps({
        'versionCode': int(code),
        'versionName': name,
        'apk': f'https://github.com/{repo}/releases/download/v{name}/{p.name}',
        'sha256': digest,
    }, indent=2))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else 'manishekhawat/OpenRath')
