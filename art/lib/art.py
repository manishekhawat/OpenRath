"""Drawing helpers for OpenRath's built-in artwork: chrome fills, panels and line icons.

Everything is drawn at a 1024x600 design size and scaled by K = height/600, so the
1280x720 assets come out of the same code. Shapes are supersampled (SS) then reduced.
"""
import numpy as np
from PIL import Image, ImageDraw, ImageFont

SS = 4
FONT = '/usr/share/fonts/opentype/inter/'

# Palette, matched to the boot animation's final frame.
BLACK = (0, 0, 0)
PANEL = (11, 12, 14)
SILVER = (216, 220, 226)      # flat silver for live text the launcher draws itself
SILVER_DIM = (138, 143, 150)
EDGE = (70, 74, 80)


def chrome(t):
    """The boot animation's chrome ramp: bright sky, dark horizon, bright ground."""
    stops = [(0, .95), (.38, .78), (.5, .30), (.56, .55), (.8, .85), (1, .62)]
    xs, ys = zip(*stops)
    return np.interp(t, xs, ys)


def font(name, size):
    return ImageFont.truetype(FONT + name, max(1, round(size * SS)))


class Layer:
    """A w x h supersampled alpha mask that shapes are drawn into, then reduced."""

    def __init__(self, w, h):
        self.w, self.h = w, h
        self.im = Image.new('L', (round(w * SS), round(h * SS)), 0)
        self.d = ImageDraw.Draw(self.im)

    def s(self, *v):
        return [x * SS for x in v]

    def mask(self):
        return np.asarray(self.im.resize((round(self.w), round(self.h)), Image.LANCZOS), np.float32) / 255


def chrome_rgb(h, w, y0=0, y1=None, tilt=0.12):
    """A vertical chrome ramp over a w x h box (slightly tilted), as RGB floats."""
    y1 = h if y1 is None else y1
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    t = np.clip((yy - y0) / max(y1 - y0, 1) + (xx - w / 2) / max(w, 1) * tilt, 0, 1)
    l = chrome(t)
    return np.stack([l * .96, l * .98, l * 1.03], -1)


def flat_rgb(h, w, col):
    return np.broadcast_to(np.array(col, np.float32) / 255, (h, w, 3)).copy()


def to_rgba(rgb, alpha):
    a = np.clip(alpha, 0, 1)[..., None]
    return Image.fromarray((np.concatenate([np.clip(rgb, 0, 1), a], -1) * 255).astype(np.uint8), 'RGBA')


def paste(base, im, x, y):
    base.alpha_composite(im, (round(x), round(y)))


# ---------------------------------------------------------------- logos

from svgpath import subpaths as _subpaths

PHONE_PATH = "M20.01 15.38c-1.23 0-2.42-.2-3.53-.56-.35-.12-.74-.03-1.01.24l-1.57 1.97c-2.83-1.35-5.48-3.9-6.89-6.83l1.95-1.66c.27-.28.35-.67.24-1.02-.37-1.11-.56-2.3-.56-3.53 0-.54-.45-.99-.99-.99H4.19C3.65 3 3 3.24 3 3.99 3 13.28 10.73 21 20.01 21c.71 0 .99-.63.99-1.18v-3.45c0-.54-.45-.99-.99-.99z"


def chrome_text(text, size, tracking=0, name='Inter-Bold.otf'):
    f = font(name, size)
    widths = [f.getlength(c) for c in text]
    total = (sum(widths) + tracking * SS * (len(text) - 1)) / SS
    asc, desc = f.getmetrics()
    L = Layer(total + 2, (asc + desc) / SS + 2)
    x = SS
    for c, wc in zip(text, widths):
        L.d.text((x, SS), c, font=f, fill=255)
        x += wc + tracking * SS
    m = L.mask()
    h, w = m.shape
    return to_rgba(chrome_rgb(h, w, y0=h * .15, y1=h * .9, tilt=0), m)


def flat_text(base, text, x, y, size, col, name='Inter-Regular.otf', anchor='la', tracking=0):
    """Text the launcher draws itself (clock, track names): flat colour, no chrome."""
    f = ImageFont.truetype(FONT + name, round(size))
    d = ImageDraw.Draw(base)
    if not tracking:
        d.text((x, y), text, font=f, fill=col + (255,), anchor=anchor)
        return
    widths = [f.getlength(c) for c in text]
    total = sum(widths) + tracking * (len(text) - 1)
    if anchor[0] == 'm':
        x -= total / 2
    elif anchor[0] == 'r':
        x -= total
    for c, wc in zip(text, widths):
        d.text((x, y), c, font=f, fill=col + (255,), anchor='l' + anchor[1])
        x += wc + tracking


# ---------------------------------------------------------------- panels

def panel(w, h, k, radius=14, fill=PANEL, edge=True):
    """A dark card with a thin brushed-silver edge that is brightest along the top."""
    w, h = round(w), round(h)
    r = radius * k
    body = Layer(w, h)
    body.d.rounded_rectangle(body.s(0, 0, w - 1, h - 1), r * SS, fill=255)
    bm = body.mask()
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    shade = 1 - 0.35 * yy / h
    rgb = np.array(fill, np.float32)[None, None] / 255 * shade[..., None] + 0.018 * (1 - yy / h)[..., None]
    if edge:
        ring = Layer(w, h)
        lw = max(1, round(1.2 * k)) * SS
        ring.d.rounded_rectangle(ring.s(0, 0, w - 1, h - 1), r * SS, outline=255, width=lw)
        rm = ring.mask()
        # brushed light: bright at top centre, fading down and to the sides
        glint = 0.30 + 0.55 * np.exp(-((xx - w * .5) / (w * .45)) ** 2) * (1 - yy / h) ** 1.5
        rgb = rgb * (1 - rm[..., None]) + np.stack([glint * .96, glint * .98, glint * 1.03], -1) * rm[..., None]
    return to_rgba(rgb, bm)


def background(w, h, k):
    """Near-black page with a cool radial lift behind the main area, like the boot screen."""
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    r = np.sqrt(((xx - w * .55) / (620 * k)) ** 2 + ((yy - h * .42) / (380 * k)) ** 2)
    glow = np.clip(1 - r, 0, 1) ** 2 * 0.085
    rgb = np.stack([glow * .75, glow * .8, glow * .88], -1) + 0.012
    return to_rgba(rgb, np.ones((h, w), np.float32))


def rule(w, k):
    """A thin horizontal line, bright in the middle and fading out at both ends."""
    w = round(w)
    h = max(2, round(2 * k))
    xx = np.arange(w, dtype=np.float32)
    a = 0.75 * np.clip(1 - np.abs(xx - w / 2) / (w / 2), 0, 1) ** 0.6
    return to_rgba(flat_rgb(h, w, (230, 233, 238)), np.broadcast_to(a, (h, w)) * (np.arange(h) < max(1, round(1.2 * k)))[:, None])


# ---------------------------------------------------------------- icons

def icon(name, size, k, pressed=False):
    """A chrome line icon in a ring. `size` is the full square in pixels."""
    S = round(size)
    L = Layer(S, S)
    d, c = L.d, S / 2
    lw = max(1.5, 2.2 * k) * SS
    p = lambda *v: [x * SS for x in v]
    # outer ring
    rr = S / 2 - 1.5 * k
    d.ellipse(p(c - rr, c - rr, c + rr, c + rr), outline=255, width=round(lw * .7))
    g = S * 0.26   # glyph half-size
    if name == 'navi':
        d.polygon(p(c, c - g, c + g * .8, c + g, c, c + g * .45, c - g * .8, c + g), fill=255)
    elif name == 'music':
        d.line(p(c - g * .25, c + g * .55, c - g * .25, c - g * .85), fill=255, width=round(lw))
        d.line(p(c - g * .25, c - g * .85, c + g * .7, c - g * .55), fill=255, width=round(lw))
        d.line(p(c + g * .7, c - g * .55, c + g * .7, c + g * .35), fill=255, width=round(lw))
        e = g * .32
        d.ellipse(p(c - g * .25 - e * 1.6, c + g * .55 - e, c - g * .25 + e * .2, c + g * .55 + e), fill=255)
        d.ellipse(p(c + g * .7 - e * 1.6, c + g * .35 - e, c + g * .7 + e * .2, c + g * .35 + e), fill=255)
    elif name == 'radio':
        d.ellipse(p(c - g * .18, c - g * .18, c + g * .18, c + g * .18), fill=255)
        for i, rad in enumerate((g * .55, g * .95)):
            for a0 in (-50, 130):
                d.arc(p(c - rad, c - rad, c + rad, c + rad), a0, a0 + 100, fill=255, width=round(lw))
    elif name == 'bt':
        x0, x1 = c - g * .5, c + g * .5
        y0, y1 = c - g, c + g
        d.line(p(c, y0, c, y1), fill=255, width=round(lw))
        d.line(p(x0, c - g * .5, x1, c + g * .5, c, y1), fill=255, width=round(lw), joint='curve')
        d.line(p(x0, c + g * .5, x1, c - g * .5, c, y0), fill=255, width=round(lw), joint='curve')
    elif name == 'video':
        d.rounded_rectangle(p(c - g, c - g * .7, c + g * .45, c + g * .7), g * .15 * SS, outline=255, width=round(lw))
        d.polygon(p(c + g * .5, c - g * .05, c + g, c - g * .5, c + g, c + g * .5, c + g * .5, c + g * .05), fill=255)
    elif name == 'phone':
        # Material Design "call" glyph (Apache-2.0), 24-unit box
        sc = g * 2 / 18
        for poly in _subpaths(PHONE_PATH):
            d.polygon(p(*[v for x, y in poly for v in (c + (x - 12) * sc, c + (y - 12) * sc)]), fill=255)
    elif name == 'apps':
        q = g * .38
        for ix in (-1, 0, 1):
            for iy in (-1, 0, 1):
                cx, cy = c + ix * g * .72, c + iy * g * .72
                d.rounded_rectangle(p(cx - q * .5, cy - q * .5, cx + q * .5, cy + q * .5), q * .2 * SS, fill=255)
    elif name == 'settings':
        import math
        pts = []
        for i in range(48):
            a = i / 48 * 2 * math.pi
            rad = g * (1.0 if (i // 3) % 2 == 0 else .78)
            pts += [c + rad * math.cos(a), c + rad * math.sin(a)]
        d.polygon(p(*pts), fill=255)
        d.ellipse(p(c - g * .36, c - g * .36, c + g * .36, c + g * .36), fill=0)
    m = L.mask()
    rgb = chrome_rgb(S, S, y0=S * .2, y1=S * .85, tilt=0.15)
    if pressed:
        # pressed state: brighter fill disc behind a dark glyph
        disc = Layer(S, S)
        disc.d.ellipse(disc.s(1.5 * k, 1.5 * k, S - 1.5 * k, S - 1.5 * k), fill=255)
        dm = disc.mask()
        glyph = m * (dm > 0.5)
        rgb_disc = chrome_rgb(S, S, y0=0, y1=S, tilt=0.15) * 0.9
        out = rgb_disc * (1 - glyph[..., None]) + 0.05 * glyph[..., None]
        return to_rgba(out, dm)
    return to_rgba(rgb, m)


def media_button(name, size, k, pressed=False):
    """Prev / play / pause / next glyphs without a ring (the card provides the frame)."""
    S = round(size)
    L = Layer(S, S)
    d, c, g = L.d, S / 2, S * .3
    p = lambda *v: [x * SS for x in v]
    if name == 'play':
        d.polygon(p(c - g * .6, c - g, c + g, c, c - g * .6, c + g), fill=255)
    elif name == 'pause':
        d.rounded_rectangle(p(c - g * .75, c - g, c - g * .2, c + g), g * .12 * SS, fill=255)
        d.rounded_rectangle(p(c + g * .2, c - g, c + g * .75, c + g), g * .12 * SS, fill=255)
    elif name in ('next', 'prev'):
        sgn = 1 if name == 'next' else -1
        d.polygon(p(c - sgn * g, c - g * .8, c + sgn * g * .35, c, c - sgn * g, c + g * .8), fill=255)
        bx = c + sgn * g * .55
        d.rounded_rectangle(p(min(bx, bx + sgn * g * .22), c - g * .8, max(bx, bx + sgn * g * .22), c + g * .8), g * .06 * SS, fill=255)
    m = L.mask()
    if pressed:
        ring = Layer(S, S)
        ring.d.ellipse(ring.s(1, 1, S - 1, S - 1), fill=255)
        rm = ring.mask() * 0.22
        alpha = np.clip(m + rm, 0, 1)
        rgb = chrome_rgb(S, S, tilt=0) * (m[..., None] > 0.01) + 0.85 * (m[..., None] <= 0.01)
        return to_rgba(rgb, alpha)
    return to_rgba(chrome_rgb(S, S, y0=S * .2, y1=S * .8, tilt=0), m)
