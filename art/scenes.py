"""Backgrounds for the picture layouts, and a rear view of a compact saloon. All drawn here from shapes,
gradients and noise, so they're ours to share under the app's licence.

usage: python3 scenes.py OUTDIR      writes OUTDIR/<scene>.jpg (1280 x 750) and car_rear_*.png
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 1280, 750
RNG = np.random.default_rng(7)


# ---------------------------------------------------------------- helpers

def grad(top, bottom, h=H, w=W, power=1.0):
    t = np.linspace(0, 1, h, dtype=np.float32)[:, None, None] ** power
    a, b = np.array(top, np.float32), np.array(bottom, np.float32)
    return np.broadcast_to(a + (b - a) * t, (h, w, 3)).copy()


def blob(img, cx, cy, rx, ry, col, strength=1.0):
    yy, xx = np.mgrid[0:img.shape[0], 0:img.shape[1]].astype(np.float32)
    m = np.exp(-(((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2)) * strength
    img[:] = img * (1 - m[..., None]) + np.array(col, np.float32) * m[..., None]


def ridge(x, base, amp, seed, octaves=5, rough=0.55):
    r = np.random.default_rng(seed)
    y = np.zeros_like(x, dtype=np.float32)
    a, f = amp, 1.0 / 260
    for _ in range(octaves):
        ph = r.uniform(0, 1000)
        y += a * np.abs(np.sin(x * f + ph) + 0.5 * np.sin(x * f * 2.3 + ph * 1.7))
        a *= rough
        f *= 2.1
    return base - y


def layer_mask(h, w, top):
    yy = np.arange(h, dtype=np.float32)[:, None]
    return np.clip(yy - top[None, :] + 0.5, 0, 1)


def put(img, mask, col, alpha=1.0):
    c = np.array(col, np.float32) if not isinstance(col, np.ndarray) else col
    img[:] = img * (1 - mask[..., None] * alpha) + c * mask[..., None] * alpha


def to_img(a):
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGB')


def noise(img, amount=3.0):
    img += RNG.normal(0, amount, img.shape).astype(np.float32)


def road(img, horizon, vx, bottom_half_w, top_half_w, col, edge_col, dash_col=None, fade=True):
    h, w = img.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    t = np.clip((yy - horizon) / (h - horizon), 0, 1)
    half = top_half_w + (bottom_half_w - top_half_w) * t
    inside = (np.abs(xx - vx) < half) & (yy > horizon)
    m = inside.astype(np.float32)
    if fade:
        m *= np.clip(t * 4, 0, 1)
    put(img, m, col)
    for side in (-1, 1):
        e = (np.abs(xx - (vx + side * half * 0.92)) < (1 + 5 * t)) & (yy > horizon)
        put(img, e.astype(np.float32) * np.clip(t * 3, 0, 1), edge_col, 0.8)
    if dash_col is not None:
        # dashes get longer towards the viewer
        z = 1 / np.maximum(t, 0.02)
        on = (np.sin(z * 2.2) > 0.2) & (np.abs(xx - vx) < (1 + 4 * t)) & (yy > horizon + 8)
        put(img, on.astype(np.float32) * np.clip(t * 3, 0, 1), dash_col, 0.9)


def save(a, out, name, q=88):
    to_img(a).save(out / f'{name}.jpg', quality=q, optimize=True, progressive=True)


# ---------------------------------------------------------------- the car from behind

def car_rear(w, h, paint=(0.62, 0.64, 0.67)):
    """RGBA rear view of a compact saloon , w x h."""
    SS = 4
    s = min(w / 210, h / 150)
    ox, oy = (w - 200 * s) / 2, h * 0.04

    def mask(polys=(), rects=(), ellipses=()):
        im = Image.new('L', (w * SS, h * SS), 0)
        d = ImageDraw.Draw(im)
        for p in polys:
            d.polygon([((ox + x * s) * SS, (oy + y * s) * SS) for x, y in p], fill=255)
        for x0, y0, x1, y1, r in rects:
            d.rounded_rectangle([(ox + x0 * s) * SS, (oy + y0 * s) * SS, (ox + x1 * s) * SS, (oy + y1 * s) * SS], r * s * SS, fill=255)
        for x0, y0, x1, y1 in ellipses:
            d.ellipse([(ox + x0 * s) * SS, (oy + y0 * s) * SS, (ox + x1 * s) * SS, (oy + y1 * s) * SS], fill=255)
        return np.asarray(im.resize((w, h), Image.LANCZOS), np.float32) / 255

    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    uy, ux = (yy - oy) / s, (xx - ox) / s
    rgb = np.zeros((h, w, 3), np.float32)
    alpha = np.zeros((h, w), np.float32)

    def lay(m, col):
        nonlocal rgb, alpha
        c = col if isinstance(col, np.ndarray) and col.ndim == 3 else np.broadcast_to(np.array(col, np.float32), (h, w, 3))
        rgb = rgb * (1 - m[..., None]) + c * m[..., None]
        alpha = np.maximum(alpha, m)

    sh = np.clip(1 - (((ux - 100) / 108) ** 2 + ((uy - 108) / 8) ** 2), 0, 1) ** 1.4
    alpha = np.maximum(alpha, sh * 0.8)
    lay(mask(rects=[(18, 84, 46, 110, 3), (154, 84, 182, 110, 3)]), (0.05, 0.05, 0.06))
    body = [(14, 52), (30, 46), (170, 46), (186, 52), (191, 70), (189, 92), (181, 100), (19, 100), (11, 92), (9, 70)]
    cabin = [(46, 12), (154, 12), (166, 26), (176, 48), (24, 48), (34, 26)]
    p = np.array(paint, np.float32)
    shade = 1.0 - 0.28 * np.clip((uy - 46) / 54, 0, 1) + 0.18 * np.exp(-((uy - 50) / 3.5) ** 2)
    shade = shade - 0.18 * np.clip((np.abs(ux - 100) - 72) / 20, 0, 1) + 0.08 * np.exp(-((uy - 15) / 4) ** 2)
    lay(mask([body, cabin, [(12, 40), (26, 38), (28, 47), (14, 48)], [(188, 40), (174, 38), (172, 47), (186, 48)]]),
        np.clip(shade[..., None] * p, 0, 1))
    gl = 0.07 + 0.08 * np.exp(-(((ux - 100) * 0.4 - (uy - 28)) / 9) ** 2)
    lay(mask([[(50, 16), (150, 16), (160, 28), (168, 44), (32, 44), (40, 28)]]), np.stack([gl * .9, gl * .95, gl * 1.1], -1))
    # boot lid line and the chrome strip
    lay(mask([[(30, 57), (170, 57), (170, 58), (30, 58)]]) * 0.5, tuple(p * 0.6))
    lay(mask([[(62, 62), (138, 62), (138, 63.4), (62, 63.4)]]), (0.85, 0.87, 0.9))
    # tail lamps: dark red with a bright edge
    lamp = [(12, 55), (52, 57), (56, 66), (16, 68), (11, 62)]
    lamp_r = [(200 - x, y) for x, y in lamp][::-1]
    lay(mask([lamp, lamp_r]), (0.45, 0.04, 0.05))
    lay(mask([[(14, 57), (50, 58.5), (51, 60), (15, 59.5)], [(186, 57), (150, 58.5), (149, 60), (185, 59.5)]]), (0.95, 0.25, 0.25))
    # number plate, bumper, reflectors
    lay(mask(rects=[(78, 70, 122, 80, 1.5)]), (0.93, 0.93, 0.9))
    lay(mask([[(40, 88), (160, 88), (156, 95), (44, 95)]]), (0.07, 0.07, 0.08))
    lay(mask(rects=[(26, 86, 36, 89, 1), (164, 86, 174, 89, 1)]), (0.6, 0.06, 0.06))
    rgb = np.clip(rgb, 0, 1)
    out = np.concatenate([rgb, alpha[..., None]], -1)
    return Image.fromarray((out * 255).astype(np.uint8), 'RGBA')


# ---------------------------------------------------------------- scenes

def sunset_sea(w, h):
    """The tall picture in the bubbles layout: sky, low sun, still sea."""
    a = grad((96, 138, 214), (242, 186, 160), h, w, 1.3)
    hz = int(h * 0.62)
    blob(a, w * 0.55, hz - 6, w * 0.9, 34, (255, 196, 140), 0.9)
    sea = grad((112, 104, 140), (226, 166, 168), h - hz, w, 0.8)
    yy = np.arange(h - hz, dtype=np.float32)[:, None]
    shimmer = np.exp(-((np.arange(w)[None, :] - w * 0.55) / (w * 0.08)) ** 2) * (np.sin(yy * 1.7) * 0.5 + 0.5) * 60
    sea[..., 0] += shimmer * np.exp(-yy / 120)
    sea[..., 1] += shimmer * 0.7 * np.exp(-yy / 120)
    a[hz:] = sea
    a[hz - 2:hz + 1] = (60, 52, 70)
    noise(a, 2)
    return a


def bubbles(out):
    a = grad((228, 233, 240), (196, 206, 218))
    blob(a, 1100, 120, 400, 240, (240, 236, 230), 0.6)
    blob(a, 200, 650, 420, 200, (206, 212, 228), 0.5)
    noise(a, 1.5)
    save(a, out, 'bubbles')
    save(sunset_sea(320, 640), out, 'bubbles_pill')


def scenery(out):
    a = grad((214, 220, 226), (236, 238, 240), power=0.8)
    x = np.arange(W, dtype=np.float32)
    hz = 455
    for i, (base, amp, col, mist) in enumerate([(395, 70, (150, 158, 168), 0.0), (420, 60, (118, 126, 138), 0.0),
                                                (440, 40, (92, 100, 112), 0.0)]):
        top = ridge(x, base, amp, 11 + i, octaves=4, rough=0.45)
        # lower towards the middle, where the road meets the horizon
        k = 0.25 + 0.75 * np.clip(np.abs(x - W / 2) / 420, 0, 1) ** 1.5
        top = hz - (hz - top) * k
        m = layer_mask(H, W, top) * (np.arange(H)[:, None] < hz)
        put(a, m, col)
        put(a, m * np.clip(1 - (np.arange(H)[:, None] - top[None, :]) / 40, 0, 1) * 0.35, (240, 242, 245))
        # mist rising from the lake between the ridges
        yy = np.arange(H, dtype=np.float32)[:, None]
        put(a, np.broadcast_to(np.exp(-((yy - hz) / 38) ** 2), (H, W)) * 0.55, (226, 230, 234))
    # still salt-flat water with reflections
    ref = np.asarray(to_img(a[hz - 1::-1][:H - hz].copy()).filter(ImageFilter.GaussianBlur(3)), np.float32)
    a[hz:] = ref * 0.5 + np.array([214, 220, 226], np.float32) * 0.5
    road(a, hz, W / 2, 520, 6, (214, 218, 222), (246, 247, 248))
    noise(a, 1.5)
    save(a, out, 'scenery')


def columns(out):
    a = grad((46, 70, 104), (98, 132, 170), power=0.9)
    blob(a, 900, 300, 500, 380, (120, 152, 190), 0.4)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    lines = ((np.abs((xx - 640) - (yy - 375) * 0.6) % 160) < 1.2).astype(np.float32) * 0.06
    put(a, lines, (200, 220, 240))
    noise(a, 1.5)
    save(a, out, 'columns')


def picture_clock(out):
    a = grad((236, 238, 241), (214, 218, 223))
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    for k, (cy, r, col, wd) in enumerate([(1400, 1150, (196, 202, 210), 60), (1500, 1180, (226, 230, 236), 30),
                                          (1300, 1050, (205, 210, 218), 18)]):
        d = np.sqrt((xx - 1500) ** 2 + (yy - cy) ** 2)
        put(a, np.clip(1 - np.abs(d - r) / wd, 0, 1), col, 0.8)
    d = np.sqrt((xx - 1700) ** 2 + (yy - 1650) ** 2)
    streak = np.clip(1 - np.abs(d - 1330) / 7, 0, 1) * np.clip((700 - xx) / 600, 0, 1)
    put(a, streak, (60, 130, 230), 0.9)
    blob(a, 330, 620, 360, 70, (180, 186, 196), 0.4)
    noise(a, 1.2)
    save(a, out, 'picture_clock')


def strip(out):
    a = grad((214, 224, 246), (238, 222, 232), power=1.1)
    blob(a, 1000, 330, 300, 200, (250, 214, 220), 0.5)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    d = np.sqrt((xx - 960) ** 2 + (yy - 420) ** 2)
    put(a, np.clip(1 - np.abs(d - 130) / 34, 0, 1) * (yy < 520), (246, 140, 150), 0.85)
    hz = 520
    r = np.random.default_rng(3)
    x = 40
    while x < 720:
        bw, bh = r.integers(36, 80), r.integers(80, 300)
        col = np.array([70, 110, 200], np.float32) * r.uniform(0.75, 1.15)
        a[hz - bh:hz, x:x + bw] = a[hz - bh:hz, x:x + bw] * 0.15 + col * 0.85
        for wy in range(hz - bh + 8, hz - 6, 14):
            for wx in range(x + 6, x + bw - 6, 12):
                if r.random() < 0.3:
                    a[wy:wy + 5, wx:wx + 5] = (255, 190, 150)
        x += bw + int(r.integers(2, 12))
    blob(a, 380, hz, 520, 60, (226, 230, 248), 0.6)
    ref = a[hz - 1::-1][:H - hz].copy()
    a[hz:] = ref * 0.35 + np.array([206, 218, 242], np.float32) * 0.65
    noise(a, 1.5)
    save(a, out, 'strip')


def twin_dials(out):
    a = grad((20, 26, 38), (8, 10, 16))
    blob(a, 640, 300, 520, 260, (34, 44, 62), 0.6)
    road(a, 300, 640, 260, 4, (14, 17, 24), (110, 130, 160), fade=False)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    for cx in (290, 990):
        d = np.sqrt((xx - cx) ** 2 + (yy - 400) ** 2)
        put(a, np.clip(1 - np.abs(d - 268) / 3, 0, 1), (90, 110, 140), 0.7)
        put(a, np.clip(1 - np.abs(d - 296) / 1.5, 0, 1) * (yy < 300), (160, 180, 210), 0.6)
    noise(a, 1.2)
    save(a, out, 'twin_dials')


def room(out):
    a = grad((226, 224, 220), (206, 202, 196))
    fl = 520
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    wood = grad((196, 158, 116), (176, 134, 94), H - fl)
    planks = (np.sin(xx[fl:] * 0.06 + np.sin(yy[fl:] * 0.3) * 0.3) * 6)
    wood += planks[..., None]
    a[fl:] = wood
    a[fl - 4:fl] = (176, 170, 160)
    # window light on the wall and a column
    blob(a, 980, 260, 260, 200, (244, 242, 238), 0.7)
    a[60:fl, 440:470] = a[60:fl, 440:470] * 0.8 + np.array([240, 238, 234]) * 0.2
    # sofa
    so = ((xx > 1020) & (xx < 1270) & (yy > 430) & (yy < 540)).astype(np.float32)
    so = np.asarray(Image.fromarray((so * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(6)), np.float32) / 255
    put(a, so, (222, 214, 204))
    put(a, ((xx > 1040) & (xx < 1250) & (yy > 470) & (yy < 485)).astype(np.float32) * 0.3, (160, 150, 140))
    # plants: leaves as rotated ellipses
    leaves = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(leaves)
    r = np.random.default_rng(5)
    for cx, cy, n in ((90, 480, 26), (1190, 420, 18)):
        for _ in range(n):
            ang = r.uniform(-2.6, -0.5)
            ln = r.uniform(70, 150)
            x1, y1 = cx + np.cos(ang) * ln, cy + np.sin(ang) * ln
            d.line([(cx, cy), (x1, y1)], fill=255, width=int(r.uniform(8, 16)))
    lm = np.asarray(leaves.filter(ImageFilter.GaussianBlur(2)), np.float32) / 255
    put(a, lm, (64, 104, 70))
    # pot, pendant lamps
    put(a, ((xx - 90) ** 2 / 50 ** 2 + (yy - 520) ** 2 / 34 ** 2 < 1).astype(np.float32), (60, 58, 56))
    for lx, ly in ((560, 250), (620, 300)):
        a[0:ly, lx:lx + 2] = (120, 110, 100)
        put(a, ((xx - lx) ** 2 / 26 ** 2 + (yy - ly - 10) ** 2 / 22 ** 2 < 1).astype(np.float32) * (yy > ly), (200, 160, 100))
        blob(a, lx, ly + 30, 60, 50, (255, 230, 180), 0.35)
    noise(a, 1.5)
    save(a, out, 'room')


def big_clock(out):
    a = grad((70, 72, 76), (52, 54, 58))
    im = to_img(a)
    sq = Image.new('RGB', (W, H), (0, 0, 0))
    m = Image.new('L', (W, H), 0)
    d, dm = ImageDraw.Draw(sq), ImageDraw.Draw(m)
    for box, col in (((560, 170, 1000, 520), (80, 170, 230)), ((780, 300, 1240, 700), (140, 200, 40)),
                     ((900, 140, 1180, 420), (70, 90, 110))):
        d.rounded_rectangle(box, 30, fill=col)
        dm.rounded_rectangle(box, 30, fill=200)
    sq = sq.filter(ImageFilter.GaussianBlur(26))
    m = m.filter(ImageFilter.GaussianBlur(26))
    im.paste(sq, (0, 0), m)
    a = np.asarray(im, np.float32).copy()
    noise(a, 2)
    save(a, out, 'big_clock')


def lyrics(out):
    a = grad((236, 242, 246), (214, 232, 238))
    blob(a, 900, 200, 420, 260, (196, 230, 236), 0.6)
    blob(a, 1180, 640, 360, 220, (226, 210, 240), 0.6)
    noise(a, 1.2)
    save(a, out, 'lyrics')


def cards(out):
    a = grad((70, 86, 104), (150, 164, 176), power=1.2)
    x = np.arange(W, dtype=np.float32)
    hz = 470
    for i, (base, amp, col) in enumerate([(340, 110, (200, 208, 216)), (390, 70, (120, 130, 142)), (430, 40, (70, 78, 90))]):
        top = ridge(x, base, amp, 21 + i, octaves=4, rough=0.42)
        m = layer_mask(H, W, top) * (np.arange(H)[:, None] < hz)
        put(a, m, col)
        put(a, m * np.clip(1 - (np.arange(H)[:, None] - top[None, :]) / 30, 0, 1) * 0.6, (236, 240, 244))
    # a lodge with warm windows
    a[hz - 44:hz, 470:930] = (34, 30, 30)
    a[hz - 54:hz - 44, 430:970] = (52, 44, 40)
    for wx in range(486, 914, 46):
        a[hz - 38:hz - 8, wx:wx + 38] = (250, 172, 96)
    blob(a, 700, hz - 20, 300, 40, (250, 180, 110), 0.15)
    ref = np.asarray(to_img(a[hz - 1::-1][:H - hz].copy()).filter(ImageFilter.GaussianBlur(2)), np.float32)
    a[hz:] = ref * 0.5 + np.array([40, 46, 54], np.float32) * 0.5
    noise(a, 1.5)
    save(a, out, 'cards')


def mission(out):
    """Night sky over the curve of the Earth, like a launch console's screens."""
    a = grad((0, 0, 2), (4, 10, 24), power=1.6)
    r = np.random.default_rng(11)
    for _ in range(420):
        x, y = int(r.integers(0, W)), int(r.integers(0, 560))
        v = float(r.uniform(60, 230))
        a[y, x] = (v, v, min(255, v * 1.08))
        if v > 200 and x + 1 < W:
            a[y, x + 1] = a[y, x] * 0.6
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    d = np.sqrt((xx - 640) ** 2 + (yy - 2350) ** 2)
    R = 1760
    # the planet, night side with a little blue towards the edge, then the thin glowing air
    put(a, np.clip(R - d + 0.5, 0, 1), (4, 10, 22))
    put(a, np.clip(R - d + 0.5, 0, 1) * np.exp(-(R - d) / 60), (20, 60, 130), 0.8)
    put(a, np.exp(-((d - R) / 5) ** 2), (120, 190, 255), 0.95)
    put(a, np.exp(-((d - R - 6) / 22) ** 2) * (d > R), (40, 110, 220), 0.45)
    blob(a, 640, 600, 600, 90, (30, 80, 170), 0.25)
    noise(a, 1.0)
    save(a, out, 'mission')


def electric(out):
    """Soft light grey, with a lighter panel on the left where the car drives."""
    a = grad((226, 228, 231), (214, 216, 220))
    a[:, :500] = grad((246, 246, 247), (234, 235, 237), w=500)
    a[:, 498:501] = (210, 212, 216)
    road(a, 312, 250, 212, 6, (222, 224, 228), (255, 255, 255))
    blob(a, 250, 312, 220, 30, (250, 250, 251), 0.5)
    noise(a, 1.0)
    save(a, out, 'electric')


def roadster(out):
    """A dark road to a red dusk horizon, with mountains."""
    a = grad((10, 10, 12), (64, 14, 16), power=1.8)
    hz = 375
    a[hz:] = grad((22, 22, 24), (8, 8, 9), H - hz)
    blob(a, 640, hz, 700, 70, (200, 40, 36), 0.55)
    x = np.arange(W, dtype=np.float32)
    for i, (base, amp, col) in enumerate([(330, 60, (34, 12, 14)), (350, 36, (14, 8, 9))]):
        top = ridge(x, base, amp, 31 + i, octaves=4, rough=0.5)
        m = layer_mask(H, W, top) * (np.arange(H)[:, None] < hz)
        put(a, m, col)
    road(a, hz, 640, 412, 5, (16, 16, 18), (232, 33, 39))
    blob(a, 640, 700, 420, 80, (232, 33, 39), 0.12)
    noise(a, 1.2)
    save(a, out, 'roadster')


def main(out):
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    for f in (bubbles, scenery, columns, picture_clock, strip, twin_dials, room, big_clock, lyrics, cards):
        f(out)
    car_rear(420, 300, (0.62, 0.64, 0.67)).save(out / 'car_rear_silver.png', optimize=True)
    car_rear(420, 300, (0.86, 0.87, 0.89)).save(out / 'car_rear_white.png', optimize=True)
    car_rear(420, 300, (0.30, 0.32, 0.35)).save(out / 'car_rear_grey.png', optimize=True)
    for f in (mission, electric, roadster):
        f(out)
    car_rear(420, 300, (0.78, 0.08, 0.10)).save(out / 'car_rear_red.png', optimize=True)


if __name__ == '__main__':
    main(sys.argv[1])
