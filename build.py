"""Build OpenRath, the free home-screen app for Android car stereos.

usage: python3 build.py OUTDIR     build the signed APK into OUTDIR
       python3 build.py test       compile and run the unit tests (tests/)
       python3 build.py androidtest OUTDIR
                                   build the on-device tests (androidTest/) into OUTDIR/openrath-test.apk
                                   and OUTDIR/openrath-fleet.apk; run them with adb (.github/workflows/device-tests.yml)

Artwork is drawn by art/lib. Tools: $THEME_TOOLS (or tools/.tools) holding apktool.jar, signer.jar
(tools/fetch_tools.sh), android.jar (API 34) and d8.jar (build-tools 34; dx.jar still works) from the
Android SDK.

OPENRATH_PUBLIC=1 leaves out brand packs; OPENRATH_UNSIGNED=1 stops before signing
(OUTDIR/openrath-unsigned.apk); AAPT2=path uses that aapt2 instead of apktool's own.

Signing: signing.json beside this file, {"keystore": path, "alias", "storePass", "keyPass"}, kept
out of the repository; without it a debug key is made in ~/.openrath-debug.keystore. Keep using the
same key, or Android won't install an update over the installed app.

Brand packs (brands/<id>/badge.py, not published) add car makers' badges; see brands/README.md.
"""
import importlib.util, os, shutil, subprocess, sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / 'art' / 'lib'))
from art import *          # noqa: E402
from car import car        # noqa: E402
from svgpath import subpaths   # noqa: E402
from fill import nonzero, as_polys   # noqa: E402
from weather import weather    # noqa: E402

TOOLS = Path(os.environ.get('THEME_TOOLS', HERE / 'tools' / '.tools'))


def signing():
    """(keystore, alias, store password, key password): signing.json, else a debug key made once."""
    import json
    f = HERE / 'signing.json'
    if f.exists():
        j = json.loads(f.read_text())
        ks = Path(j['keystore'])
        return (ks if ks.is_absolute() else HERE / ks), j['alias'], j['storePass'], j['keyPass']
    ks = Path.home() / '.openrath-debug.keystore'
    if not ks.exists():
        subprocess.run(['keytool', '-genkeypair', '-keystore', str(ks), '-alias', 'debug', '-keyalg', 'RSA',
                        '-keysize', '2048', '-validity', '10000', '-storepass', 'android', '-keypass', 'android',
                        '-dname', 'CN=OpenRath debug'], check=True)
    return ks, 'debug', 'android', 'android'


def packs():
    """The brand packs present: {brand id: module}. OPENRATH_PUBLIC=1 leaves them out (the APK to share)."""
    out = {}
    if os.environ.get('OPENRATH_PUBLIC'):
        return out
    for d in sorted((HERE / 'brands').glob('*/badge.py')):
        spec = importlib.util.spec_from_file_location('badge_' + d.parent.name.replace('-', '_'), d)
        m = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(m)
        out[d.parent.name] = m
    return out


def badge_image(polys, width, height):
    """Badge outlines (24 units tall) in chrome, `height` px tall."""
    u = height / 24
    L = Layer(width * u, height)
    # nonzero fill, so rings and letter counters stay open
    L.im = Image.fromarray(nonzero([[(x * u * SS, y * u * SS) for x, y in p] for p in as_polys(polys)], L.im.width, L.im.height))
    m = L.mask()
    return to_rgba(chrome_rgb(m.shape[0], m.shape[1], tilt=0.05), m)


def white(polys, size):
    """Outlines in white, `size` px tall (for pictures the app tints)."""
    polys = [q for p in polys for q in as_polys(p)]
    xs = [x for p in polys for x, _ in p]
    ys = [y for p in polys for _, y in p]
    s = (size - 4) / (max(ys) - min(ys))
    im = Image.new('RGBA', (round((max(xs) - min(xs)) * s) + 4, size), (255, 255, 255, 0))
    a = nonzero([[((x - min(xs)) * s + 2, (y - min(ys)) * s + 2) for x, y in p] for p in polys], im.width, im.height)
    im.putalpha(Image.fromarray(a))
    return im
X = 2.4   # images are drawn for a 600 px tall screen times X, then scaled down on the unit
VERSION_CODE, VERSION_NAME = 42, '1.34'
# Oldest Android the APK installs on (26 = Android 8.0). It targets Android 14.
MIN_SDK = 26


def wheel_mask(height):
    """The OpenRath mark: a chariot wheel (rim, eight spokes, hub) as a Layer `height` tall."""
    import math
    u = height / 24
    L = Layer(height, height)
    c = 12 * u
    def ring(r_out, r_in):
        L.d.ellipse(L.s(c - r_out, c - r_out, c + r_out, c + r_out), fill=255)
        L.d.ellipse(L.s(c - r_in, c - r_in, c + r_in, c + r_in), fill=0)
    ring(11.8 * u, 9.6 * u)    # the rim
    ring(4.0 * u, 2.2 * u)     # the hub
    for i in range(8):
        a = i * math.pi / 4 + math.pi / 8
        dx, dy = math.cos(a), math.sin(a)
        px, py = -dy * 0.75 * u, dx * 0.75 * u
        p0 = (c + dx * 3.6 * u, c + dy * 3.6 * u)
        p1 = (c + dx * 9.9 * u, c + dy * 9.9 * u)
        L.d.polygon([((p0[0] + px) * SS, (p0[1] + py) * SS), ((p1[0] + px) * SS, (p1[1] + py) * SS),
                     ((p1[0] - px) * SS, (p1[1] - py) * SS), ((p0[0] - px) * SS, (p0[1] - py) * SS)], fill=255)
    return L


def wheel(height):
    """The OpenRath mark in chrome, as an RGBA image."""
    m = wheel_mask(height).mask()
    h, w = m.shape
    return to_rgba(chrome_rgb(h, w, tilt=0.05), m)


def art(apk):
    img = apk / 'assets' / 'img'
    img.mkdir(parents=True, exist_ok=True)
    brands = packs()
    # the car: car_on/off (hatchback or saloon) and car_on/off_suv, with a plain emblem; a brand pack
    # adds car_on_<brand>.png and so on with its emblem. The app picks the most specific one there is.
    for body in ('car', 'suv'):
        for on in (False, True):
            name = f"car_{'on' if on else 'off'}" + ('' if body == 'car' else '_suv')
            car(round(340 * X), round(200 * X), on, body).save(img / f'{name}.png', optimize=True)
            for bid, b in brands.items():
                car(round(340 * X), round(200 * X), on, body, b.emblem()).save(img / f'{name}_{bid}.png', optimize=True)
    # the logo beside the car name: the OpenRath wheel, or a brand pack's badge (img/logo_<brand id>.png)
    wheel(22 * X).save(img / 'logo.png')
    for bid, b in brands.items():
        bw, polys = b.badge(subpaths)
        badge_image(polys, bw, 22 * X).save(img / f'logo_{bid}.png')
    for n in ('w_sun', 'w_moon', 'w_partly', 'w_cloud', 'w_rain', 'w_snow', 'w_storm', 'w_fog'):
        weather(n, round(56 * X)).save(img / f'{n}.png')
    for n in ('navi', 'music', 'radio', 'bt', 'phone', 'apps', 'settings'):
        icon(n, round(62 * X), X).save(img / f'dock_{n}.png')
    for n in ('prev', 'play', 'pause', 'next'):
        media_button(n, round(52 * X), X).save(img / f'btn_{n}.png')
    # the picture layouts' backgrounds and cars (art/scenes.py), and the badge for the twin-dials layout
    spec = importlib.util.spec_from_file_location('scenes', HERE / 'art' / 'scenes.py')
    scenes = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(scenes)
    lay = apk / 'assets' / 'layouts'
    scenes.main(lay)
    previews(lay)
    for bid, b in brands.items():
        white([b.small()], 164).save(lay / f'logo_s_{bid}.png', optimize=True)
    m = wheel_mask(160).mask()
    w = Image.new('RGBA', (160, 160), (255, 255, 255, 0))
    w.putalpha(Image.fromarray((m * 255).astype('uint8'), 'L'))
    w.save(lay / 'logo_s.png', optimize=True)   # the twin-dials badge: the wheel, in white
    cars(apk)
    badges(apk, brands)
    fonts = apk / 'assets' / 'fonts'
    fonts.mkdir(parents=True, exist_ok=True)
    shutil.copy(FONT + 'InterDisplay-Light.otf', fonts)   # the car name's font, for the welcome line
    shutil.copy('/usr/share/doc/fonts-inter/copyright', fonts / 'INTER-LICENSE')
    mm = apk / 'res' / 'mipmap-mdpi-v4'
    mm.mkdir(parents=True, exist_ok=True)
    ic = Image.new('RGBA', (96, 96), (0, 0, 0, 0))
    disc = Layer(96, 96)
    disc.d.ellipse(disc.s(2, 2, 94, 94), fill=255)
    paste(ic, to_rgba(flat_rgb(96, 96, (12, 13, 15)), disc.mask()), 0, 0)
    lg = wheel(64)
    paste(ic, lg, (96 - lg.width) / 2, (96 - lg.height) / 2)
    ic.save(mm / 'ic_launcher.png')


APKTOOL_YML = f'''!!brut.androlib.meta.MetaInfo
apkFileName: home.apk
compressionType: false
doNotCompress:
- resources.arsc
- png
isFrameworkApk: false
packageInfo:
  forcedPackageId: '127'
  renameManifestPackage: null
sdkInfo:
  minSdkVersion: '{MIN_SDK}'
  targetSdkVersion: '34'
sharedLibrary: false
sparseResources: false
usesFramework:
  ids:
  - 1
  tag: null
version: 2.9.3
versionInfo:
  versionCode: '{VERSION_CODE}'
  versionName: '{VERSION_NAME}'
'''


def badges(apk, brands):
    """assets/badges.json: the brand packs' badges as outlines (24 units tall) for the boot logo and
    animation the app makes (BootArt.java), so they stay sharp at any screen size."""
    import json
    out = {}
    for bid, b in brands.items():
        bw, polys = b.badge(subpaths)
        out[bid] = {'w': round(bw, 3), 'h': 24, 'polys': [[[round(x, 3), round(y, 3)] for x, y in p] for p in polys]}
    (apk / 'assets' / 'badges.json').write_text(json.dumps(out))


def cars(apk):
    """assets/cars.json: the brands and models offered by the car setup, from catalogue/cars.json
    (names only; the catalogue's unverified photo and logo notes are left out)."""
    import json
    src = json.loads((HERE / 'catalogue' / 'cars.json').read_text())
    out = [{'id': b['id'], 'name': b['name'], 'models': [m['name'] for m in b['models']]} for b in src['brands']]
    (apk / 'assets' / 'cars.json').write_text(json.dumps(out, ensure_ascii=False))


def dex(work, out):
    classes = work / 'classes'
    classes.mkdir()
    srcs = [str(p) for p in (HERE / 'java').rglob('*.java')]
    subprocess.run(['javac', '-nowarn', '--release', '8', '-encoding', 'UTF-8', '-cp', str(TOOLS / 'android.jar'),
                    '-d', str(classes)] + srcs, check=True)
    if (TOOLS / 'd8.jar').exists():
        # d8 (Android build-tools 30+), as the SDK uses today
        d8out = work / 'd8'
        d8out.mkdir()
        subprocess.run(['java', '-cp', str(TOOLS / 'd8.jar'), 'com.android.tools.r8.D8', '--release', '--min-api', str(MIN_SDK),
                        '--lib', str(TOOLS / 'android.jar'), '--output', str(d8out)]
                       + [str(p) for p in sorted(classes.rglob('*.class'))], check=True)
        shutil.move(str(d8out / 'classes.dex'), str(out))
        return
    # else dx: dx 1.7 only reads older class files; the code uses no Java 8 language features
    for p in classes.rglob('*.class'):
        b = bytearray(p.read_bytes()); b[7] = 50; p.write_bytes(bytes(b))
    subprocess.run(['java', '-cp', str(TOOLS / 'dx.jar'), 'com.android.dx.command.Main', '--dex',
                    f'--output={out}', str(classes)], check=True)


def previews(lay):
    """Small pictures of each layout for the gallery in Settings > Look (art/layout_preview.py),
    and a sketch of the Wide map arrangement."""
    import json, tempfile
    spec = importlib.util.spec_from_file_location('layout_preview', HERE / 'art' / 'layout_preview.py')
    lp = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(lp)
    lp.SCENES = lay
    tmp = Path(tempfile.mkdtemp(prefix='openrath-prev-'))
    for layout in json.loads((HERE / 'assets' / 'layouts' / 'layouts.json').read_text())['layouts']:
        lp.render(layout, tmp)
        im = Image.open(tmp / f"layout-{layout['id']}.png").convert('RGB').resize((384, 225), Image.LANCZOS)
        im.save(lay / f"prev_{layout['id']}.jpg", quality=86, optimize=True)
    shutil.rmtree(tmp)
    # Wide map: a big map, two cards and the app bar
    S = 2
    im = Image.new('RGB', (1024 * S, 600 * S), (22, 24, 28))
    d = ImageDraw.Draw(im)
    def rr(box, r, fill):
        d.rounded_rectangle([v * S for v in box], r * S, fill=fill)
    rr((18, 18, 690, 516), 18, (52, 92, 140))
    for x0, y0, x1, y1, w in ((60, 480, 420, 120, 14), (200, 516, 640, 300, 9), (18, 260, 690, 330, 7), (420, 18, 520, 516, 6)):
        d.line([x0 * S, y0 * S, x1 * S, y1 * S], fill=(120, 160, 205), width=w * S)
    d.ellipse([320 * S, 400 * S, 380 * S, 440 * S], fill=(90, 170, 255))
    rr((704, 18, 1006, 222), 18, (64, 66, 72))
    d.text((855 * S, 120 * S), '10:45', font=lp.font(52, 'med'), fill=(235, 235, 235), anchor='mm')
    rr((704, 236, 1006, 516), 18, (120, 70, 90))
    d.text((724 * S, 440 * S), 'Tum Hi Ho', font=lp.font(26, 'med'), fill=(255, 255, 255), anchor='ls')
    for i, col in enumerate(((60, 200, 90), (80, 200, 120), (60, 200, 210), (240, 240, 240))):
        cx = 380 + i * 90
        d.ellipse([(cx - 30) * S, 532 * S, (cx + 30) * S, 592 * S], fill=col)
    d.text((40 * S, 562 * S), '10:45', font=lp.font(26, 'reg'), fill=(220, 220, 220), anchor='lm')
    for i in range(3):
        d.ellipse([(880 + i * 46) * S, 552 * S, (900 + i * 46) * S, 572 * S], fill=(220, 220, 220))
    im.resize((384, 225), Image.LANCZOS).save(lay / 'prev_wide.jpg', quality=86, optimize=True)


def build(outdir):
    work = Path(outdir) / '.work-home'
    shutil.rmtree(work, ignore_errors=True)
    apk = work / 'apk'
    shutil.copytree(HERE / 'res', apk / 'res')
    shutil.copytree(HERE / 'assets', apk / 'assets')
    shutil.copy(HERE / 'AndroidManifest.xml', apk)
    (apk / 'apktool.yml').write_text(APKTOOL_YML)
    art(apk)
    dex(work, apk / 'classes.dex')
    unsigned = work / 'unsigned.apk'
    # AAPT2=path uses the SDK's aapt2 instead of the one inside apktool (F-Droid's build servers)
    aapt = ['-a', os.environ['AAPT2']] if os.environ.get('AAPT2') else []
    subprocess.run(['java', '-jar', str(TOOLS / 'apktool.jar'), 'b', str(apk), '-o', str(unsigned),
                    '--use-aapt2', '-p', str(TOOLS / 'framework')] + aapt, check=True)
    if os.environ.get('OPENRATH_UNSIGNED'):   # F-Droid signs it, or copies OpenRath's signature on
        final = Path(outdir) / 'openrath-unsigned.apk'
        shutil.move(str(unsigned), final)
        shutil.rmtree(work)
        return final
    ks, alias, sp, kp = signing()
    subprocess.run(['java', '-jar', str(TOOLS / 'signer.jar'), '-a', str(unsigned), '-o', str(work / 'signed'),
                    '--ks', str(ks), '--ksAlias', alias, '--ksPass', sp,
                    '--ksKeyPass', kp, '--allowResign'], check=True)
    final = Path(outdir) / 'openrath.apk'
    shutil.move(next((work / 'signed').glob('*.apk')), final)
    shutil.rmtree(work)
    return final


def androidtest(outdir):
    """openrath-test.apk: the on-device tests, signed with the app's key so they run inside it; and
    openrath-fleet.apk: a stand-in device-management app that sets OpenRath's managed settings, so the
    tests can check them as a fleet would see them."""
    jar = str(TOOLS / 'android.jar')
    work = Path(outdir) / '.work-test'
    shutil.rmtree(work, ignore_errors=True)
    app = work / 'app'
    app.mkdir(parents=True)
    subprocess.run(['javac', '-nowarn', '--release', '8', '-encoding', 'UTF-8', '-cp', jar, '-d', str(app)]
                   + [str(p) for p in (HERE / 'java').rglob('*.java')], check=True)
    out = [test_apk(work / 'test', HERE / 'androidTest', app, '126', Path(outdir) / 'openrath-test.apk'),
           test_apk(work / 'fleet', HERE / 'androidTest' / 'fleet', None, '125', Path(outdir) / 'openrath-fleet.apk')]
    shutil.rmtree(work)
    return '\n'.join(str(p) for p in out)


def test_apk(work, src, app, package_id, final):
    """One test APK from src/AndroidManifest.xml, src/java and src/res; with `app`, OpenRath's classes
    are on the class path but not in the APK (they come from the installed app)."""
    jar = str(TOOLS / 'android.jar')
    classes, d8out, apk = work / 'classes', work / 'd8', work / 'apk'
    for d in (classes, d8out, apk / 'res' / 'values'):
        d.mkdir(parents=True)
    cp = f'{jar}:{app}' if app else jar
    subprocess.run(['javac', '-nowarn', '--release', '8', '-encoding', 'UTF-8', '-cp', cp, '-d', str(classes)]
                   + [str(p) for p in (src / 'java').rglob('*.java')], check=True)
    if (TOOLS / 'd8.jar').exists():
        subprocess.run(['java', '-cp', str(TOOLS / 'd8.jar'), 'com.android.tools.r8.D8', '--release', '--min-api', str(MIN_SDK),
                        '--lib', jar, '--output', str(d8out)] + (['--classpath', str(app)] if app else [])
                       + [str(p) for p in sorted(classes.rglob('*.class'))], check=True)
        shutil.move(str(d8out / 'classes.dex'), str(apk / 'classes.dex'))
    else:
        for p in classes.rglob('*.class'):
            c = bytearray(p.read_bytes()); c[7] = 50; p.write_bytes(bytes(c))
        subprocess.run(['java', '-cp', str(TOOLS / 'dx.jar'), 'com.android.dx.command.Main', '--dex',
                        f'--output={apk / "classes.dex"}', str(classes)], check=True)
    shutil.copy(src / 'AndroidManifest.xml', apk)
    if (src / 'res').is_dir():
        shutil.copytree(src / 'res', apk / 'res', dirs_exist_ok=True)
    (apk / 'res' / 'values' / 'test_name.xml').write_text('<resources><string name="test_name">OpenRath tests</string></resources>\n')
    (apk / 'apktool.yml').write_text(APKTOOL_YML.replace('home.apk', 'test.apk').replace("forcedPackageId: '127'", f"forcedPackageId: '{package_id}'"))
    unsigned = work / 'unsigned.apk'
    subprocess.run(['java', '-jar', str(TOOLS / 'apktool.jar'), 'b', str(apk), '-o', str(unsigned),
                    '--use-aapt2', '-p', str(TOOLS / 'framework')], check=True)
    ks, alias, sp, kp = signing()
    subprocess.run(['java', '-jar', str(TOOLS / 'signer.jar'), '-a', str(unsigned), '-o', str(work / 'signed'),
                    '--ks', str(ks), '--ksAlias', alias, '--ksPass', sp, '--ksKeyPass', kp, '--allowResign'], check=True)
    shutil.move(next((work / 'signed').glob('*.apk')), final)
    return final


def test():
    """Compile the app and the tests against android.jar and run them (plain Java, no framework)."""
    import tempfile
    out = Path(tempfile.mkdtemp(prefix='openrath-test-'))
    srcs = [str(p) for p in (HERE / 'java').rglob('*.java')] + [str(p) for p in (HERE / 'tests').rglob('*.java')]
    subprocess.run(['javac', '-nowarn', '--release', '8', '-encoding', 'UTF-8', '-cp', str(TOOLS / 'android.jar'),
                    '-d', str(out)] + srcs, check=True)
    r = subprocess.run(['java', '-cp', f"{out}:{TOOLS / 'android.jar'}", 'org.openrath.launcher.Tests'], cwd=HERE)   # the tests read res/ and assets/
    shutil.rmtree(out, ignore_errors=True)
    return r.returncode


if __name__ == '__main__':
    if sys.argv[1:] == ['test']:
        sys.exit(test())
    if sys.argv[1:2] == ['androidtest']:
        print(androidtest(sys.argv[2]))
        sys.exit(0)
    print(build(sys.argv[1]))
