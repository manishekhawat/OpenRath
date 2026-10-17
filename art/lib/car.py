"""A grey car, front view, with headlights off or on: a hatchback or saloon ("car") or an SUV.
Drawn in a 200 x 120 unit box. The emblem on the grille is a plain oval unless a brand pack passes
its own outline (24 units square, as brands/<id>/badge.py gives it)."""
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

SS = 4

def _mir(pts):
    return [(200 - x, y) for x, y in pts][::-1]

BODY = [(16, 50), (34, 43), (166, 43), (184, 50), (190, 66), (189, 90), (181, 99), (19, 99), (11, 90), (10, 66)]
CABIN = [(48, 8), (152, 8), (164, 22), (174, 45), (26, 45), (36, 22)]
GLASS = [(52, 12), (148, 12), (158, 24), (166, 42), (34, 42), (42, 24)]
MIRROR_L = [(14, 36), (30, 34), (32, 45), (16, 46)]
LAMP_L = [(17, 55), (60, 58), (63, 66), (24, 68), (15, 63)]
DRL_L = [(20, 57), (56, 59.5), (57, 61), (21, 59.2)]
GRILLE = [(66, 58), (134, 58), (139, 79), (61, 79)]
INTAKE = [(50, 84), (150, 84), (145, 93), (55, 93)]
FOG_L = [(22, 82), (40, 82), (40, 88), (24, 89)]
TYRE_L = (20, 84, 46, 112)
TYRE_R = (154, 84, 180, 112)

# the same parts for an SUV: taller body and glasshouse, bigger grille and wheels, roof rails
SUV = dict(
    BODY=[(14, 44), (32, 36), (168, 36), (186, 44), (191, 62), (190, 92), (182, 101), (18, 101), (10, 92), (9, 62)],
    CABIN=[(44, 4), (156, 4), (166, 16), (174, 38), (26, 38), (34, 16)],
    GLASS=[(48, 8), (152, 8), (160, 18), (166, 35), (34, 35), (40, 18)],
    MIRROR_L=[(12, 30), (29, 28), (31, 39), (14, 40)],
    LAMP_L=[(15, 48), (58, 51), (61, 59), (22, 61), (13, 56)],
    DRL_L=[(18, 50), (54, 52.5), (55, 54), (19, 52.2)],
    GRILLE=[(62, 50), (138, 50), (142, 76), (58, 76)],
    INTAKE=[(48, 82), (152, 82), (147, 94), (53, 94)],
    FOG_L=[(20, 80), (38, 80), (38, 87), (22, 88)],
    TYRE_L=(17, 82, 48, 114), TYRE_R=(152, 82, 183, 114),
    RAILS=(46, 0.5, 154, 3), PROJ=[(42, 52, 50, 58), (150, 52, 158, 58)], CREASE=39, BARS=53, BAR_STEP=4.6, EMBLEM=(100, 62),
)
CAR = dict(BODY=BODY, CABIN=CABIN, GLASS=GLASS, MIRROR_L=MIRROR_L, LAMP_L=LAMP_L, DRL_L=DRL_L, GRILLE=GRILLE,
           INTAKE=INTAKE, FOG_L=FOG_L, TYRE_L=TYRE_L, TYRE_R=TYRE_R, RAILS=None, PROJ=[(44, 60, 52, 66), (148, 60, 156, 66)], CREASE=46, BARS=61, BAR_STEP=3.8,
           EMBLEM=(100, 66))


def _mask(polys, w, h, s, ox, oy, ellipses=(), rects=()):
    im = Image.new('L', (w * SS, h * SS), 0)
    d = ImageDraw.Draw(im)
    for p in polys:
        d.polygon([((ox + x * s) * SS, (oy + y * s) * SS) for x, y in p], fill=255)
    for x0, y0, x1, y1 in rects:
        d.rounded_rectangle([(ox + x0 * s) * SS, (oy + y0 * s) * SS, (ox + x1 * s) * SS, (oy + y1 * s) * SS], 3 * s * SS, fill=255)
    for x0, y0, x1, y1 in ellipses:
        d.ellipse([(ox + x0 * s) * SS, (oy + y0 * s) * SS, (ox + x1 * s) * SS, (oy + y1 * s) * SS], fill=255)
    return np.asarray(im.resize((w, h), Image.LANCZOS), np.float32) / 255


def car(w, h, lights_on=False, body='car', emblem=None):
    """RGBA image of the car, w x h, with room below for its shadow and light beams. `emblem` is a
    badge outline in a 24-unit box (None for a plain oval)."""
    G = SUV if body == 'suv' else CAR
    BODY, CABIN, GLASS, MIRROR_L, LAMP_L, DRL_L = G['BODY'], G['CABIN'], G['GLASS'], G['MIRROR_L'], G['LAMP_L'], G['DRL_L']
    GRILLE, INTAKE, FOG_L, TYRE_L, TYRE_R = G['GRILLE'], G['INTAKE'], G['FOG_L'], G['TYRE_L'], G['TYRE_R']
    s = min(w / 210, h / 150)
    ox, oy = (w - 200 * s) / 2, h * 0.04
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    uy = (yy - oy) / s          # car units
    ux = (xx - ox) / s
    rgb = np.zeros((h, w, 3), np.float32)
    alpha = np.zeros((h, w), np.float32)

    def lay(m, col):
        nonlocal rgb, alpha
        c = col if isinstance(col, np.ndarray) and col.ndim == 3 else np.broadcast_to(np.array(col, np.float32), (h, w, 3))
        rgb = rgb * (1 - m[..., None]) + c * m[..., None]
        alpha = np.maximum(alpha, m)

    # ground shadow
    sh = np.clip(1 - (((ux - 100) / 105) ** 2 + ((uy - 110) / 9) ** 2), 0, 1) ** 1.5
    rgb += 0; alpha = np.maximum(alpha, sh * 0.85)

    # beams on the road
    if lights_on:
        for cx in (40, 160):
            d = np.clip(uy - 100, 0, None)
            spread = 16 + d * 1.2
            beam = np.exp(-((ux - cx - (cx - 100) * d * 0.02) / spread) ** 2) * np.exp(-d / 40) * (uy > 96)
            edge = np.clip((h - yy) / (h * .12), 0, 1) * np.clip(np.minimum(xx, w - xx) / (w * .12), 0, 1)
            m = np.clip(beam * 0.8 * edge, 0, 1)
            rgb = rgb * (1 - m[..., None]) + np.array([0.92, 0.95, 1.0]) * m[..., None]
            alpha = np.maximum(alpha, m)

    lay(_mask([], w, h, s, ox, oy, rects=[TYRE_L, TYRE_R]), (0.05, 0.05, 0.06))

    # grey paint: lighter on the bonnet, darker towards the bumper, a soft vertical sheen
    paint = 0.40 - 0.16 * np.clip((uy - 43) / 56, 0, 1) + 0.12 * np.exp(-((uy - 47) / 4) ** 2)
    paint = paint + 0.06 * np.exp(-((ux - 100) / 60) ** 2) - 0.12 * np.clip((np.abs(ux - 100) - 70) / 20, 0, 1)
    paint = paint + 0.08 * np.exp(-((uy - 14) / 5) ** 2) * (uy < 22)
    grey = np.stack([paint * 0.97, paint * 0.99, paint * 1.03], -1)
    lay(_mask([BODY, CABIN, MIRROR_L, _mir(MIRROR_L)], w, h, s, ox, oy), grey)

    # windscreen: dark glass with a diagonal reflection
    gl = 0.08 + 0.10 * np.exp(-(((ux - 100) * 0.4 + (uy - 25)) / 8) ** 2)
    lay(_mask([GLASS], w, h, s, ox, oy), np.stack([gl * .9, gl * .95, gl * 1.1], -1))

    # bonnet crease highlight
    c0 = G['CREASE']
    crease = _mask([[(40, c0), (160, c0), (160, c0 + 1.2), (40, c0 + 1.2)]], w, h, s, ox, oy)
    lay(crease * 0.6, (0.85, 0.87, 0.9))

    # grille, intake, fogs
    lay(_mask([GRILLE, INTAKE, FOG_L, _mir(FOG_L)], w, h, s, ox, oy), (0.06, 0.065, 0.07))
    for i in range(5):
        y = G['BARS'] + i * G['BAR_STEP']
        bar = _mask([[(64 + i * .5, y), (136 - i * .5, y), (136 - i * .5, y + 0.9), (64 + i * .5, y + 0.9)]], w, h, s, ox, oy)
        lay(bar * 0.9, (0.55, 0.57, 0.6))
    # chrome emblem on the grille: the brand's, else a plain oval
    ex, ey = G['EMBLEM']
    if emblem:
        from fill import nonzero, as_polys
        S = [[((ox + (ex + (x - 12) * 0.55) * s) * SS, (oy + (ey + (y - 12) * 0.55) * s) * SS) for x, y in p] for p in as_polys(emblem)]
        m = Image.fromarray(nonzero(S, w * SS, h * SS))
        lay(np.asarray(m.resize((w, h), Image.LANCZOS), np.float32) / 255, (0.85, 0.87, 0.9))
    else:
        ring = _mask([], w, h, s, ox, oy, ellipses=[(ex - 7, ey - 4.2, ex + 7, ey + 4.2)])
        hole = _mask([], w, h, s, ox, oy, ellipses=[(ex - 5.6, ey - 2.9, ex + 5.6, ey + 2.9)])
        lay(np.clip(ring - hole, 0, 1), (0.85, 0.87, 0.9))
    if G['RAILS']:
        x0, y0, x1, y1 = G['RAILS']
        lay(_mask([], w, h, s, ox, oy, rects=[(x0, y0, x1, y1)]), (0.12, 0.125, 0.13))

    # headlamps
    lamp = _mask([LAMP_L, _mir(LAMP_L)], w, h, s, ox, oy)
    drl = _mask([DRL_L, _mir(DRL_L)], w, h, s, ox, oy)
    proj = _mask([], w, h, s, ox, oy, ellipses=G['PROJ'])
    if lights_on:
        lay(lamp, (0.80, 0.84, 0.90))
        lay(proj, (1, 1, 1))
        lay(drl, (1, 1, 1))
        glow = Image.fromarray((np.clip(lamp, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(9 * s / 2))
        g = np.asarray(glow, np.float32) / 255 * 0.9
        rgb = rgb + g[..., None] * np.array([0.6, 0.66, 0.75])
        alpha = np.maximum(alpha, np.clip(g * 1.4, 0, 1))
    else:
        lay(lamp, (0.10, 0.11, 0.12))
        lay(proj * 0.8, (0.30, 0.31, 0.33))
        lay(drl * 0.8, (0.32, 0.33, 0.35))
    rgb = np.clip(rgb, 0, 1)
    out = np.concatenate([rgb, alpha[..., None]], -1)
    return Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8), 'RGBA')


if __name__ == '__main__':
    import sys
    bg = Image.new('RGBA', (840, 300), (8, 9, 11, 255))
    bg = Image.new('RGBA', (1680, 300), (8, 9, 11, 255))
    bg.alpha_composite(car(420, 300, False), (0, 0))
    bg.alpha_composite(car(420, 300, True), (420, 0))
    bg.alpha_composite(car(420, 300, False, 'suv'), (840, 0))
    bg.alpha_composite(car(420, 300, True, 'suv'), (1260, 0))
    bg.convert('RGB').save(sys.argv[1])
