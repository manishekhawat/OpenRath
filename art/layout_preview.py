"""Preview pictures of the picture layouts (assets/layouts/layouts.json), drawn the way the app's
LayoutView draws them, with sample data. For choosing layouts before installing.

usage: python3 layout_preview.py SCENES_DIR OUTDIR     (SCENES_DIR from art/scenes.py)
"""
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / 'lib'))
FONTS = '/usr/share/fonts/opentype/inter/'
S = 2                     # drawn at twice the size, then scaled down
W, H = 1024, 600

SAMPLE = dict(time='10:45', ampm='am', h=10, m=45, sec=20, day='Tuesday', date='13 Oct', mmdd=('10', '13'),
              temp='28°', lo='24°', hi='31°', desc='Partly cloudy', place='Gurugram', humidity='62%', wind='9 km/h NE',
              pressure='1008 hPa', speed=62, title='Tum Hi Ho', artist='Arijit Singh', altitude='216 m', heading=135,
              direction='SE', lat='28.4595° N', lon='77.0266° E', user='Tech', trip='48.6', progress=0.35,
              forecast=[('Wed', 'w_sun', '31°'), ('Thu', 'w_partly', '30°'), ('Fri', 'w_rain', '27°'), ('Sat', 'w_cloud', '29°')],
              lyrics=['Hum tere bin ab reh nahi sakte', 'Tere bina kya wajood mera', 'Tujh se juda agar ho jaayenge',
                      'Toh khud se hi ho jaayenge judaa'])


def font(size, weight='reg'):
    name = {'light': 'Inter-Light.otf', 'reg': 'Inter-Regular.otf', 'med': 'Inter-Medium.otf', 'bold': 'Inter-SemiBold.otf',
            'thin': 'Inter-ExtraLight.otf'}[weight]
    try:
        return ImageFont.truetype(FONTS + name, int(size * S))
    except OSError:
        return ImageFont.truetype(FONTS + 'Inter-Regular.otf', int(size * S))


def col(hexs, alpha=1.0):
    h = hexs.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (round(255 * alpha),)


class Canvas:
    def __init__(self, bg):
        self.im = bg.convert('RGBA').resize((W * S, H * S), Image.LANCZOS)

    def layer(self):
        return Image.new('RGBA', self.im.size, (0, 0, 0, 0))

    def put(self, layer):
        self.im.alpha_composite(layer)

    def shape(self, p, shadow=True):
        """The part's background: circle, rounded rect, optional vertical gradient, alpha."""
        if 'fill' not in p:
            return
        x, y, w, h = (p[k] * S for k in 'xywh')
        mask = Image.new('L', self.im.size, 0)
        d = ImageDraw.Draw(mask)
        if p.get('shape') == 'circle':
            d.ellipse([x, y, x + w, y + h], fill=255)
        else:
            d.rounded_rectangle([x, y, x + w, y + h], p.get('r', 12) * S, fill=255)
        if shadow:
            sh = Image.new('RGBA', self.im.size, (0, 0, 0, 0))
            sm = mask.filter(ImageFilter.GaussianBlur(10 * S)).point(lambda v: v * 0.25)
            sh.putalpha(sm)
            off = Image.new('RGBA', self.im.size, (0, 0, 0, 0))
            off.paste(sh, (0, 6 * S))
            self.put(off)
        a = p.get('alpha', 1.0)
        top = col(p['fill'], a)
        bot = col(p.get('fill2', p['fill']), a)
        g = Image.new('RGBA', (1, int(h)), 0)
        for i in range(int(h)):
            t = i / max(1, h - 1)
            g.putpixel((0, i), tuple(round(top[c] + (bot[c] - top[c]) * t) for c in range(4)))
        g = g.resize((int(w), int(h)))
        fill = self.layer()
        fill.paste(g, (int(x), int(y)))
        alpha = Image.composite(fill.getchannel('A'), Image.new('L', self.im.size, 0), mask)
        fill.putalpha(alpha)
        self.put(fill)

    def text(self, xy, s, size, ink, weight='reg', anchor='la'):
        L = self.layer()
        ImageDraw.Draw(L).text((xy[0] * S, xy[1] * S), s, font=font(size, weight), fill=col(ink), anchor=anchor)
        self.put(L)

    def width(self, s, size, weight='reg'):
        return font(size, weight).getlength(s) / S

    def fit(self, s, size, maxw, weight='reg'):
        while size > 8 and self.width(s, size, weight) > maxw:
            size -= 1
        return size

    def draw(self):
        L = self.layer()
        return L, ImageDraw.Draw(L)

    def image(self, im, x, y, w, h, circle=False, radius=0):
        im = im.convert('RGBA').resize((int(w * S), int(h * S)), Image.LANCZOS)
        if circle or radius:
            m = Image.new('L', im.size, 0)
            if circle:
                ImageDraw.Draw(m).ellipse([0, 0, im.size[0] - 1, im.size[1] - 1], fill=255)
            else:
                ImageDraw.Draw(m).rounded_rectangle([0, 0, im.size[0] - 1, im.size[1] - 1], radius * S, fill=255)
            im.putalpha(Image.composite(im.getchannel('A'), Image.new('L', im.size, 0), m))
        L = self.layer()
        L.paste(im, (int(x * S), int(y * S)))
        self.put(L)


# ---------------------------------------------------------------- glyphs (24-unit box), same shapes as LayoutView

def glyph(c, name, x, y, size, ink, lw=2.0):
    L, d = c.draw()
    k = size / 24 * S
    P = lambda *v: [(x * S + v[i] * k, y * S + v[i + 1] * k) for i in range(0, len(v), 2)]
    fill = col(ink)
    w = max(1, round(lw * k))
    if name == 'navi':
        d.polygon(P(12, 3, 20, 21, 12, 16.5, 4, 21), fill=fill)
    elif name == 'music':
        d.line(P(9, 17, 9, 5, 19, 3, 19, 15), fill=fill, width=w, joint='curve')
        for cx, cy in ((6.6, 17), (16.6, 15)):
            d.ellipse(P(cx - 3, cy - 2.4, cx + 3, cy + 2.4), fill=fill)
    elif name == 'radio':
        d.rounded_rectangle(P(3, 8, 21, 20), 2 * k, outline=fill, width=w)
        d.ellipse(P(5.5, 11, 11.5, 17), outline=fill, width=w)
        d.line(P(14, 12, 18.5, 12), fill=fill, width=w)
        d.line(P(14, 16, 18.5, 16), fill=fill, width=w)
        d.line(P(6, 8, 17, 3), fill=fill, width=w)
    elif name == 'bt':
        d.line(P(7, 7, 17, 16.5, 12, 21.5, 12, 2.5, 17, 7.5, 7, 17), fill=fill, width=w, joint='curve')
    elif name == 'phone':
        d.arc(P(2, -6, 30, 22), 95, 175, fill=fill, width=round(4 * k))
        d.ellipse(P(3.5, 2.5, 9.5, 8.5), fill=fill)
        d.ellipse(P(15.5, 14.5, 21.5, 20.5), fill=fill)
    elif name == 'apps':
        for ix in (0, 1):
            for iy in (0, 1):
                d.rounded_rectangle(P(3.5 + ix * 9.5, 3.5 + iy * 9.5, 10.5 + ix * 9.5, 10.5 + iy * 9.5), 2.2 * k, fill=fill)
    elif name == 'settings':
        pts = []
        for i in range(48):
            a = i / 48 * 2 * math.pi
            r = 9.5 if (i // 3) % 2 == 0 else 7.6
            pts += [12 + r * math.cos(a), 12 + r * math.sin(a)]
        d.polygon(P(*pts), fill=fill)
        d.ellipse(P(8.6, 8.6, 15.4, 15.4), fill=(0, 0, 0, 0))
    elif name == 'user':
        d.ellipse(P(8, 3, 16, 11), outline=fill, width=w)
        d.arc(P(4, 13, 20, 29), 180, 360, fill=fill, width=w)
    elif name == 'dash':
        d.rounded_rectangle(P(3, 11, 21, 18), 2 * k, outline=fill, width=w)
        d.line(P(6, 11, 8, 6, 16, 6, 18, 11), fill=fill, width=w)
        d.ellipse(P(5.5, 17, 9.5, 21), fill=fill)
        d.ellipse(P(14.5, 17, 18.5, 21), fill=fill)
    elif name == 'home':
        d.line(P(4, 11, 12, 4, 20, 11, 20, 20, 4, 20, 4, 11), fill=fill, width=w, joint='curve')
    elif name == 'office':
        d.rectangle(P(6, 4, 18, 21), outline=fill, width=w)
        for yy in (8, 12, 16):
            d.line(P(9, yy, 15, yy), fill=fill, width=max(1, w // 2))
    elif name in ('prev', 'next', 'play', 'pause'):
        if name == 'play':
            d.polygon(P(7, 4, 20, 12, 7, 20), fill=fill)
        elif name == 'pause':
            d.rectangle(P(6, 4, 10, 20), fill=fill)
            d.rectangle(P(14, 4, 18, 20), fill=fill)
        else:
            sgn = 1 if name == 'next' else -1
            m = lambda v: 12 + sgn * (v - 12)
            d.polygon(P(m(4), 4, m(16), 12, m(4), 20), fill=fill)
            d.rectangle(P(min(m(17), m(20)), 4, max(m(17), m(20)), 20), fill=fill)
    c.put(L)


def controls(c, x, y, w, size, ink, playing=True):
    """prev, play/pause, next spread over w."""
    for i, n in enumerate(('prev', 'pause' if playing else 'play', 'next')):
        cx = x + w * (0.12 + 0.38 * i)
        glyph(c, n, cx - size / 2, y, size, ink)


def disc(c, cx, cy, r, art=None):
    L, d = c.draw()
    d.ellipse([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], fill=(16, 16, 18, 255))
    for k in (0.92, 0.84, 0.76):
        d.ellipse([(cx - r * k) * S, (cy - r * k) * S, (cx + r * k) * S, (cy + r * k) * S], outline=(40, 40, 44, 255), width=S)
    c.put(L)
    a = art or cover_art(int(r * 1.3 * S))
    c.image(a, cx - r * 0.62, cy - r * 0.62, r * 1.24, r * 1.24, circle=True)


def cover_art(px):
    """Stand-in album cover: a warm gradient with soft shapes."""
    im = Image.new('RGB', (px, px))
    d = ImageDraw.Draw(im)
    for i in range(px):
        t = i / px
        d.line([(0, i), (px, i)], fill=(int(40 + 180 * t), int(60 + 50 * t), int(120 - 40 * t)))
    d.ellipse([px * .25, px * .2, px * .8, px * .75], fill=(250, 190, 120))
    return im.filter(ImageFilter.GaussianBlur(px / 60))


def weather_icon(name, size):
    try:
        from weather import weather
        return weather(name, size)
    except Exception:
        im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        ImageDraw.Draw(im).ellipse([size * .2, size * .2, size * .8, size * .8], fill=(255, 200, 80, 255))
        return im


_W = {}


def wicon(name, size):
    key = (name, size)
    if key not in _W:
        _W[key] = weather_icon(name, size * S)
    return _W[key]


def arc_ring(c, cx, cy, r, width, frac, ink, track):
    L, d = c.draw()
    box = [(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S]
    d.arc(box, 135, 405, fill=col(track), width=round(width * S))
    d.arc(box, 135, 135 + 270 * frac, fill=col(ink), width=round(width * S))
    c.put(L)


def analog(c, cx, cy, r, ink, sub, accent=None, ticks=12, numbers=False, face=None):
    L, d = c.draw()
    if face:
        d.ellipse([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], fill=col(face))
    for i in range(60 if ticks == 60 else ticks):
        a = i / (60 if ticks == 60 else ticks) * 2 * math.pi
        major = ticks != 60 or i % 5 == 0
        r0 = r * (0.86 if major else 0.92)
        d.line([((cx + math.sin(a) * r0) * S, (cy - math.cos(a) * r0) * S),
                ((cx + math.sin(a) * r * .97) * S, (cy - math.cos(a) * r * .97) * S)],
               fill=col(sub if not major else ink), width=round((2.4 if major else 1.2) * S * r / 100))
    if numbers:
        for n in (12, 3, 6, 9):
            a = n / 12 * 2 * math.pi
            d.text(((cx + math.sin(a) * r * .7) * S, (cy - math.cos(a) * r * .7) * S), str(n), font=font(r * .16, 'med'),
                   fill=col(ink), anchor='mm')
    hm = SAMPLE['h'] % 12 + SAMPLE['m'] / 60
    for ang, ln, wd, cc in ((hm / 12, 0.5, 4.5, ink), (SAMPLE['m'] / 60, 0.75, 3, ink), (SAMPLE['sec'] / 60, 0.85, 1.4, accent or sub)):
        a = ang * 2 * math.pi
        d.line([(cx * S, cy * S), ((cx + math.sin(a) * r * ln) * S, (cy - math.cos(a) * r * ln) * S)],
               fill=col(cc), width=round(wd * S * r / 100))
    d.ellipse([(cx - r * .05) * S, (cy - r * .05) * S, (cx + r * .05) * S, (cy + r * .05) * S], fill=col(ink))
    c.put(L)


# ---------------------------------------------------------------- parts

def part_clock(c, p):
    x, y, w, h = p['x'], p['y'], p['w'], p['h']
    ink, sub, st = p.get('ink', '#FFFFFF'), p.get('sub', p.get('ink', '#FFFFFF')), p.get('style', 'digital')
    cx, cy = x + w / 2, y + h / 2
    if st == 'digital':
        c.text((cx, cy - 6), SAMPLE['time'], h * 0.26, ink, 'med', 'mm')
        c.text((cx, cy + h * 0.17), SAMPLE['date'], h * 0.09, sub, 'reg', 'mm')
    elif st == 'digital-line':
        c.text((x, y + h * 0.5), SAMPLE['time'], h * 0.62, ink, 'med', 'ls')
        c.text((x + 4, y + h * 0.82), SAMPLE['day'] + '   ' + SAMPLE['date'], h * 0.14, sub, 'reg', 'ls')
    elif st in ('analog-date',):
        c.text((cx, y + 16), SAMPLE['day'] + '  ' + SAMPLE['date'], 17, sub, 'reg', 'mt')
        analog(c, cx, y + 60 + (h - 60) / 2, min(w, h - 60) * 0.42, ink, sub, p.get('accent'), ticks=12, numbers=True)
    elif st == 'analog-digital':
        r = h * 0.38
        analog(c, x + 16 + r, cy, r, ink, sub, p.get('accent'), ticks=12, face='#FFFFFF')
        c.text((x + 32 + 2 * r, cy - 2), SAMPLE['time'], h * 0.3, ink, 'med', 'ls')
        c.text((x + 34 + 2 * r, cy + h * 0.2), SAMPLE['day'], h * 0.13, sub, 'reg', 'ls')
    elif st == 'wall':
        r = h * 0.4
        analog(c, x + 20 + r, cy, r, ink, sub, p.get('accent'), ticks=60, face='#FFFFFF')
        mm, dd = SAMPLE['mmdd']
        c.text((x + w - 24, cy - 6), mm, h * 0.34, ink, 'bold', 'rs')
        c.text((x + w - 24, cy + h * 0.32), dd, h * 0.34, ink, 'bold', 'rs')
    elif st == 'analog-big':
        analog(c, cx, cy, min(w, h) / 2, ink, sub, None, ticks=12)
    elif st == 'analog-dark':
        analog(c, cx, cy, min(w, h) * 0.42, ink, sub, p.get('accent'), ticks=12, numbers=True)
    elif st == 'digital-analog':
        c.text((x, y + h * 0.55), SAMPLE['time'], h * 0.38, ink, 'med', 'ls')
        c.text((x + 2, y + h * 0.8), SAMPLE['day'], h * 0.14, sub, 'reg', 'ls')
        r = h * 0.42
        analog(c, x + w - r - 6, cy, r, ink, sub, None, ticks=60)


def part_weather(c, p):
    x, y, w, h = p['x'], p['y'], p['w'], p['h']
    ink, sub, st = p.get('ink', '#FFFFFF'), p.get('sub', '#DDDDDD'), p.get('style', 'simple')
    cx, cy = x + w / 2, y + h / 2
    if st == 'bubble':
        L, d = c.draw()
        d.ellipse([(x + w * .55) * S, (y - h * .15) * S, (x + w * 1.15) * S, (y + h * .45) * S], fill=(255, 214, 110, 150))
        mask = Image.new('L', c.im.size, 0)
        ImageDraw.Draw(mask).ellipse([x * S, y * S, (x + w) * S, (y + h) * S], fill=255)
        L.putalpha(Image.composite(L.getchannel('A'), Image.new('L', c.im.size, 0), mask))
        c.put(L)
        c.text((cx, cy - 4), SAMPLE['temp'], h * 0.26, ink, 'med', 'mm')
        c.text((cx, cy + h * 0.17), SAMPLE['desc'], h * 0.085, sub, 'reg', 'mm')
    elif st == 'detail':
        c.text((x + 6, y + 70), SAMPLE['temp'], 72, ink, 'bold', 'ls')
        c.text((x + w, y + 18), SAMPLE['place'], 14, sub, 'reg', 'rt')
        for i, (k, v) in enumerate((('Humidity', SAMPLE['humidity']), ('Wind', SAMPLE['wind']), ('Air pressure', SAMPLE['pressure']))):
            yy = y + 118 + i * 38
            c.text((x + 6, yy), k, 15, sub, 'reg', 'lm')
            c.text((x + w, yy), v, 15, sub, 'reg', 'rm')
    elif st == 'range':
        c.text((x, y + h * 0.42), SAMPLE['temp'], h * 0.36, ink, 'med', 'ls')
        c.text((x + 2, y + h * 0.7), SAMPLE['lo'] + '/' + SAMPLE['hi'], h * 0.15, sub, 'reg', 'ls')
    elif st == 'wide':
        c.image(wicon('w_partly', 52), x + w - 60, y - 6, 52, 52)
        c.text((x, y + 40), SAMPLE['temp'].replace('°', '°C'), 40, ink, 'med', 'ls')
        c.text((x + c.width(SAMPLE['temp'] + 'C', 40, 'med') + 12, y + 40), SAMPLE['desc'], 15, sub, 'reg', 'ls')
        c.text((x + 2, y + 70), SAMPLE['place'] + '  ·  humidity ' + SAMPLE['humidity'], 14, sub, 'reg', 'ls')
    elif st == 'calendar':
        L, d = c.draw()
        for dx in (0.3, 0.7):
            d.ellipse([(x + w * dx - 5) * S, (y + 8) * S, (x + w * dx + 5) * S, (y + 18) * S], fill=col('#8A8076'))
        d.line([(x + 6) * S, (y + 30) * S, (x + w - 6) * S, (y + 30) * S], fill=col('#CFC6BA'), width=S)
        c.put(L)
        c.text((cx, y + 64), SAMPLE['lo'] + '–' + SAMPLE['hi'], 19, ink, 'bold', 'mm')
        c.image(wicon('w_partly', 44), cx - 22, y + 84, 44, 44)
        c.text((cx, y + 138), SAMPLE['desc'].split()[-1], 12, sub, 'reg', 'mm')
    elif st == 'row':
        c.image(wicon('w_rain', 60), x, y + h / 2 - 30, 60, 60)
        c.text((x + w - 20, y + h / 2), SAMPLE['temp'].replace('°', '°C'), 40, ink, 'med', 'rm')
    elif st == 'card':
        c.text((x + 16, y + 26), SAMPLE['place'], 14, sub, 'reg', 'lm')
        c.text((x + 14, y + 74), SAMPLE['temp'], 46, ink, 'med', 'lm')
        c.image(wicon('w_partly', 28), x + 16, y + 98, 28, 28)
        c.text((x + 16, y + 136), SAMPLE['desc'].split()[-1] + '  ' + SAMPLE['lo'] + '–' + SAMPLE['hi'], 13, sub, 'reg', 'lm')


def part_forecast(c, p):
    x, y, w, h = p['x'], p['y'], p['w'], p['h']
    ink, sub = p.get('ink', '#FFFFFF'), p.get('sub', '#DDDDDD')
    n = len(SAMPLE['forecast'])
    for i, (dname, ic, t) in enumerate(SAMPLE['forecast']):
        cx = x + w * (i + 0.5) / n
        c.image(wicon(ic, 40), cx - 20, y + 8, 40, 40)
        c.text((cx, y + 66), t, 16, ink, 'med', 'mm')
        c.text((cx, y + 92), dname, 13, sub, 'reg', 'mm')


def part_speed(c, p):
    x, y, w, h = p['x'], p['y'], p['w'], p['h']
    ink, sub, st = p.get('ink', '#FFFFFF'), p.get('sub', '#DDDDDD'), p.get('style', 'number')
    cx, cy = x + w / 2, y + h / 2
    v = str(SAMPLE['speed'])
    if st == 'ring':
        arc_ring(c, cx, cy, w * 0.4, w * 0.035, SAMPLE['speed'] / 180, '#FFFFFF', '#FFFFFF55')
        c.text((cx, cy - 6), v, h * 0.27, ink, 'med', 'mm')
        c.text((cx, cy + h * 0.17), 'km/h', h * 0.085, sub, 'reg', 'mm')
    elif st == 'number':
        c.text((cx, y + h * 0.72), v, h * 0.8, ink, 'med', 'ms')
        c.text((cx, y + h * 0.95), 'km/h', h * 0.13, sub, 'reg', 'ms')
    elif st == 'badge':
        c.text((cx, cy), v + ' km/h', h * 0.38, ink, 'med', 'mm')
    elif st == 'gauge-small':
        r = h * 0.36
        arc_ring(c, x + 18 + r, cy, r, 4, SAMPLE['speed'] / 180, p.get('accent', '#3CB46E'), '#00000022')
        c.text((x + 18 + r, cy - 2), v, r * 0.62, ink, 'med', 'mm')
        c.text((x + 18 + r, cy + r * 0.5), 'km/h', 10, sub, 'reg', 'mm')
        c.text((x + w - 16, cy - 10), SAMPLE['direction'] + '  ' + str(SAMPLE['heading']) + '°', 15, ink, 'med', 'rm')
        c.text((x + w - 16, cy + 16), SAMPLE['altitude'], 13, sub, 'reg', 'rm')
    elif st == 'dial':
        r = w / 2
        L, d = c.draw()
        for i in range(0, 181, 10):
            a = math.radians(225 - i / 180 * 270)
            major = i % 20 == 0
            r0 = r * (0.78 if major else 0.82)
            d.line([((cx + math.cos(a) * r0) * S, (cy - math.sin(a) * r0) * S),
                    ((cx + math.cos(a) * r * .88) * S, (cy - math.sin(a) * r * .88) * S)],
                   fill=col(ink if major else sub), width=round((3 if major else 1.5) * S))
            if major:
                d.text(((cx + math.cos(a) * r * .66) * S, (cy - math.sin(a) * r * .66) * S), str(i), font=font(r * .085, 'med'),
                       fill=col(sub if i < 140 else p.get('accent', '#3A8DFF')), anchor='mm')
        box = [(cx - r * .93) * S, (cy - r * .93) * S, (cx + r * .93) * S, (cy + r * .93) * S]
        d.arc(box, 135, 135 + 270 * SAMPLE['speed'] / 180, fill=col(p.get('accent', '#3A8DFF')), width=round(5 * S))
        d.ellipse([(cx - r * .42) * S, (cy - r * .42) * S, (cx + r * .42) * S, (cy + r * .42) * S], outline=col(sub, 0.6), width=S * 2)
        c.put(L)
        c.text((cx, cy - 2), v, r * 0.3, ink, 'med', 'mm')
        c.text((cx, cy + r * 0.24), 'km/h', r * 0.09, sub, 'reg', 'mm')
    elif st == 'row':
        c.text((x, cy + 14), v, 52, ink, 'med', 'ls')
        c.text((x + c.width(v, 52, 'med') + 12, cy + 14), 'km/h', 15, sub, 'reg', 'ls')
    elif st == 'card':
        c.text((cx, y + 64), v, 64, ink, 'med', 'mm')
        c.text((cx, y + 104), 'km/h', 15, sub, 'reg', 'mm')
        L, d = c.draw()
        for sgn in (-1, 1):
            d.line([((cx + sgn * 14) * S, (y + 140) * S), ((cx + sgn * 70) * S, (y + h - 20) * S)], fill=col('#9AC8F0'), width=S * 2)
        c.put(L)
        car = Image.open(SCENES / 'car_rear_white.png')
        c.image(car, cx - 55, y + h - 110, 110, 79)


def part_music(c, p):
    x, y, w, h = p['x'], p['y'], p['w'], p['h']
    ink, sub, st = p.get('ink', '#FFFFFF'), p.get('sub', '#DDDDDD'), p.get('style', 'row')
    cx, cy = x + w / 2, y + h / 2
    t, a = SAMPLE['title'], SAMPLE['artist']
    if st == 'pill':
        r = h * 0.38
        disc(c, x + h / 2, cy, r)
        tx = x + h + 6
        glyph(c, 'music', tx, y + 36, 18, ink)
        c.text((tx + 26, y + 45), 'Music', 15, ink, 'med', 'lm')
        c.text((tx, y + 88), t, c.fit(t, 22, w - h - 30, 'med'), ink, 'med', 'lm')
        L, d = c.draw()
        d.line([tx * S, (y + 116) * S, (tx + 60) * S, (y + 116) * S], fill=col(p.get('accent', '#E0703A')), width=S * 3)
        c.put(L)
        controls(c, tx, y + 136, w - h - 30, 24, ink)
    elif st == 'float':
        c.text((x + 18, y + 40), t, c.fit(t, 30, w - 36, 'bold'), ink, 'bold', 'lm')
        c.text((x + 18, y + 76), a, 15, sub, 'reg', 'lm')
        controls(c, x + 18, y + 112, w - 36, 24, ink)
    elif st == 'card':
        art = cover_art(400)
        c.image(art, x + 8, y + 8, w - 16, h - 90, radius=4)
        c.text((cx, y + h - 56), t, c.fit(t, 20, w - 20, 'bold'), ink, 'bold', 'mm')
        c.text((cx, y + h - 28), a, 13, sub, 'reg', 'mm')
        # controls sit in their own box underneath
        c.shape(dict(fill='#151A22', alpha=0.9, x=x, y=y + h + 12, w=w, h=70, r=8), shadow=False)
        controls(c, x + 10, y + h + 32, w - 20, 30, ink)
    elif st == 'row':
        art = cover_art(200)
        sz = min(70, h - 64)
        c.image(art, x + 14, y + 12, sz, sz, radius=6)
        c.text((x + 26 + sz, y + 12 + sz * 0.35), t, c.fit(t, 17, w - sz - 40, 'med'), ink, 'med', 'lm')
        c.text((x + 26 + sz, y + 12 + sz * 0.75), a, 12, sub, 'reg', 'lm')
        controls(c, x + 10, y + h - 42, w - 20, 22, ink)
        L, d = c.draw()
        d.line([(x + 14) * S, (y + h - 8) * S, (x + 14 + (w - 28) * SAMPLE['progress']) * S, (y + h - 8) * S], fill=col(ink), width=S * 3)
        c.put(L)
    elif st == 'dial':
        r = w / 2
        disc(c, cx, cy, r * 0.62)
        L, d = c.draw()
        d.ellipse([(cx - r * .98) * S, (cy - r * .98) * S, (cx + r * .98) * S, (cy + r * .98) * S], outline=col('#5A6A80'), width=S * 4)
        c.put(L)
        c.text((cx, y + r * 0.26), t, r * 0.11, ink, 'med', 'mm')
        c.text((cx, y + h - r * 0.26), a, r * 0.11, ink, 'med', 'mm')
        glyph(c, 'prev', x + r * 0.1, cy - 14, 28, ink)
        glyph(c, 'next', x + w - r * 0.1 - 28, cy - 14, 28, ink)
        glyph(c, 'pause', cx - 16, cy - 16, 32, '#FFFFFF')
    elif st == 'record':
        r = min(w, h) * 0.36
        L, d = c.draw()
        d.ellipse([(cx - r - 8) * S, (y + 10 - 8 + 0) * S, (cx + r + 8) * S, (y + 10 + 2 * r + 8) * S], fill=(255, 255, 255, 230))
        c.put(L)
        disc(c, cx, y + 10 + r, r)
        c.text((cx, y + 2 * r + 46), t, 17, sub, 'med', 'mm')
        L, d = c.draw()
        d.line([(x + 10) * S, (y + 2 * r + 72) * S, (x + w - 10) * S, (y + 2 * r + 72) * S], fill=col(sub, 0.4), width=S * 2)
        d.line([(x + 10) * S, (y + 2 * r + 72) * S, (x + 10 + (w - 20) * SAMPLE['progress']) * S, (y + 2 * r + 72) * S], fill=col(ink), width=S * 2)
        c.put(L)
        controls(c, x + 10, y + 2 * r + 92, w - 20, 30, ink)
    elif st == 'list':
        glyph(c, 'music', x, y + 8, 30, ink)
        c.text((x + w - 20, y + 24), t, c.fit(t, 20, w - 60, 'med'), ink, 'med', 'rm')
        controls(c, x - 6, y + 70, w - 20, 26, ink)
        L, d = c.draw()
        d.line([x * S, (y + h - 4) * S, (x + w - 20) * S, (y + h - 4) * S], fill=col(sub, 0.3), width=S)
        d.line([x * S, (y - 10) * S, (x + w - 20) * S, (y - 10) * S], fill=col(sub, 0.3), width=S)
        c.put(L)
    elif st == 'lyrics':
        glyph(c, 'music', x + 24, y + 22, 18, sub)
        c.text((x + 50, y + 31), t + '  ·  ' + a, 14, sub, 'reg', 'lm')
        lines = SAMPLE['lyrics']
        for i, ln in enumerate(lines):
            cur = i == 1
            c.text((cx, y + 120 + i * 58), ln, 30 if cur else 18, ink if cur else sub, 'bold' if cur else 'reg', 'mm')
        L, d = c.draw()
        d.line([(x + 60) * S, (y + h - 96) * S, (x + w - 60) * S, (y + h - 96) * S], fill=col(sub, 0.4), width=S * 2)
        d.line([(x + 60) * S, (y + h - 96) * S, (x + 60 + (w - 120) * SAMPLE['progress']) * S, (y + h - 96) * S], fill=col(ink), width=S * 4)
        c.put(L)
        controls(c, x + 80, y + h - 70, w - 160, 36, ink, playing=False)
    elif st == 'wide':
        glyph(c, 'music', x + 22, y + 20, 16, sub)
        c.text((x + 46, y + 28), 'Music', 13, sub, 'reg', 'lm')
        c.text((x + 22, y + 72), t, 26, ink, 'med', 'lm')
        controls(c, x + 10, y + 108, w * 0.55, 28, ink)
        disc(c, x + w - 90, cy + 4, 66)


def part_stats(c, p):
    x, y, w, h = p['x'], p['y'], p['w'], p['h']
    ink, sub = p.get('ink', '#FFFFFF'), p.get('sub', '#DDDDDD')
    labels = {'temp': ('Temp (°C)', SAMPLE['temp']), 'altitude': ('Altitude', SAMPLE['altitude']),
              'direction': ('Direction ' + SAMPLE['direction'], str(SAMPLE['heading']) + '°'),
              'pressure': ('Air pressure', SAMPLE['pressure']), 'latlon': (SAMPLE['lon'], SAMPLE['lat'])}
    items = p.get('items', [])
    if p.get('style') == 'frame':
        c.image(cover_art(300), x + 16, y + 16, h - 32, h - 32, radius=2)
        for i, it in enumerate(items):
            lab, val = labels[it]
            c.text((x + h + 10, y + 44 + i * 56), val, 26, ink, 'bold', 'lm')
            c.text((x + h + 10, y + 70 + i * 56), lab, 12, sub, 'reg', 'lm')
        return
    step = h / len(items)
    icons = {'temp': 'dash', 'altitude': 'home', 'direction': 'navi', 'pressure': 'office'}
    for i, it in enumerate(items):
        lab, val = labels[it]
        yy = y + i * step
        L, d = c.draw()
        if it == 'temp':
            d.arc([(x + 4) * S, (yy + 10) * S, (x + 44) * S, (yy + 50) * S], 150, 390, fill=col(ink), width=S * 3)
            d.line([(x + 24) * S, (yy + 30) * S, (x + 34) * S, (yy + 18) * S], fill=col(ink), width=S * 3)
        elif it == 'altitude':
            d.line([(x + 4) * S, (yy + 44) * S, (x + 18) * S, (yy + 18) * S, (x + 28) * S, (yy + 32) * S, (x + 34) * S, (yy + 24) * S,
                    (x + 46) * S, (yy + 44) * S], fill=col(ink), width=S * 3, joint='curve')
        else:
            d.ellipse([(x + 4) * S, (yy + 10) * S, (x + 44) * S, (yy + 50) * S], outline=col(ink), width=S * 3)
            d.polygon([((x + 24 + 9) * S, (yy + 21) * S), ((x + 24 + 3) * S, (yy + 33) * S), ((x + 24 - 9) * S, (yy + 39) * S),
                       ((x + 24 - 3) * S, (yy + 27) * S)], fill=col(ink))
        c.put(L)
        c.text((x + 70, yy + 30), val, 34, ink, 'bold', 'ls')
        c.text((x + 70, yy + 54), lab, 14, sub, 'reg', 'ls')


def part_compass(c, p):
    x, y, w, h = p['x'], p['y'], p['w'], p['h']
    ink, sub = p.get('ink', '#FFFFFF'), p.get('sub', '#DDDDDD')
    cx, cy = x + w / 2, y + h * 0.5
    L, d = c.draw()
    # a big arrow in perspective pointing ahead, on a faint grid floor
    for i in range(5):
        yy = cy + 10 + i * 22
        d.line([(x + 10) * S, yy * S, (x + w - 10) * S, yy * S], fill=col(sub, 0.25), width=S)
    d.polygon([(cx * S, (cy - 70) * S), ((cx + 46) * S, (cy + 30) * S), (cx * S, (cy + 10) * S), ((cx - 46) * S, (cy + 30) * S)],
              fill=col(ink, 0.85))
    d.line([cx * S, (cy + 10) * S, cx * S, (cy + 110) * S], fill=col(ink, 0.35), width=S * 8)
    c.put(L)
    c.text((cx, y + 12), SAMPLE['direction'] + '  ' + str(SAMPLE['heading']) + '°', 18, ink, 'med', 'mt')


def part_dock(c, p):
    x, y, w, h = p['x'], p['y'], p['w'], p['h']
    ink, items = p.get('ink', '#FFFFFF'), p.get('items', [])
    n = len(items) + (1 if p.get('clock') else 0)
    size = min(h * 0.5, 34)
    labels = {'navi': 'Navigation', 'apps': 'Apps', 'bt': 'Bluetooth', 'music': 'Music', 'radio': 'Radio', 'phone': 'Phone',
              'settings': 'Settings', 'user': 'Driver', 'dash': 'Dashboard'}
    for i, it in enumerate(items):
        cx = x + w * (i + 0.5) / n
        if p.get('tiles'):
            c.shape(dict(fill=p.get('fill', '#E9D7BE'), alpha=p.get('alpha', 0.6), x=cx - h / 2, y=y, w=h, h=h, r=8), shadow=False)
        gy = y + (h - size) / 2 - (8 if p.get('labels') else 0)
        glyph(c, it, cx - size / 2, gy, size, ink)
        if p.get('labels'):
            c.text((cx, gy + size + 16), labels.get(it, it), 13, ink, 'med', 'mm')
    if p.get('clock'):
        c.text((x + w - 10, y + h / 2), SAMPLE['time'], 26, ink, 'med', 'rm')


def part_user(c, p):
    x, y, w, h = p['x'], p['y'], p['w'], p['h']
    ink, sub = p.get('ink', '#FFFFFF'), p.get('sub', '#DDDDDD')
    if p.get('style') == 'line':
        L, d = c.draw()
        d.ellipse([x * S, (y + 4) * S, (x + 32) * S, (y + 36) * S], fill=col('#C89A60'))
        c.put(L)
        glyph(c, 'user', x + 4, y + 8, 24, '#FFFFFF')
        c.text((x + 44, y + h / 2), "Let's go, " + SAMPLE['user'] + '!', 16, sub, 'reg', 'lm')
        return
    r = h * 0.34
    L, d = c.draw()
    d.ellipse([(x + 18) * S, (y + h / 2 - r) * S, (x + 18 + 2 * r) * S, (y + h / 2 + r) * S], fill=col('#C89A60'))
    c.put(L)
    glyph(c, 'user', x + 18 + r - 14, y + h / 2 - 14, 28, '#FFFFFF')
    c.text((x + 30 + 2 * r, y + h / 2 - 10), SAMPLE['user'], 20, ink, 'bold', 'lm')
    c.text((x + 30 + 2 * r, y + h / 2 + 16), 'Drive safe', 13, sub, 'reg', 'lm')


def part_apps(c, p):
    x, y, w, h = p['x'], p['y'], p['w'], p['h']
    glyph(c, 'apps', x + w / 2 - w * 0.2, y + h / 2 - w * 0.2, w * 0.4, p.get('ink', '#FFFFFF'))


def part_picture(c, p):
    x, y, w, h = p['x'], p['y'], p['w'], p['h']
    im = Image.open(SCENES / (p['src'] + '.jpg'))
    c.image(im, x, y, w, h, radius=p.get('r', 0))
    if p.get('style') == 'trip':
        ink, sub = p.get('ink', '#FFFFFF'), p.get('sub', '#EEEEEE')
        c.text((x + w / 2, y + h * 0.42), SAMPLE['trip'] + ' km', 40, ink, 'med', 'mm')
        c.text((x + w / 2, y + h * 0.42 + 40), 'today · ' + SAMPLE['place'], 15, sub, 'reg', 'mm')


def part_car(c, p):
    im = Image.open(SCENES / (p['src'] + '.png'))
    c.image(im, p['x'], p['y'], p['w'], p['h'])


def part_logo(c, p):
    x, y, w, h = p['x'], p['y'], p['w'], p['h']
    c.shape(dict(p, shape='circle'), shadow=False)
    L, d = c.draw()
    d.ellipse([(x + 4) * S, (y + 4) * S, (x + w - 4) * S, (y + h - 4) * S], outline=col(p.get('ink', '#FFFFFF')), width=S * 2)
    c.put(L)
    # the OpenRath wheel (the app shows a brand pack's badge instead when there is one)
    import math
    L, d = c.draw()
    k = h * 0.5 / 24
    cx, cy, ink = x + w / 2, y + h / 2, col(p.get('ink', '#FFFFFF'))
    for ro, ri in ((11.8, 9.6), (4.0, 2.2)):
        d.ellipse([(cx - ro * k) * S, (cy - ro * k) * S, (cx + ro * k) * S, (cy + ro * k) * S], outline=ink, width=max(1, round((ro - ri) * k * S)))
    for i in range(8):
        a = i * math.pi / 4 + math.pi / 8
        d.line([(cx + math.cos(a) * 3.6 * k) * S, (cy + math.sin(a) * 3.6 * k) * S,
                (cx + math.cos(a) * 9.9 * k) * S, (cy + math.sin(a) * 9.9 * k) * S], fill=ink, width=max(1, round(1.5 * k * S)))
    c.put(L)


def part_places(c, p):
    x, y, w, h = p['x'], p['y'], p['w'], p['h']
    glyph(c, 'home', x, y + 10, 30, p.get('ink', '#FFFFFF'))
    glyph(c, 'office', x + w - 50, y + 10, 30, p.get('ink', '#FFFFFF'))


def part_favourites(c, p):
    x, y, w, h = p['x'], p['y'], p['w'], p['h']
    cols = [('#F06A4A', '#F7B048'), ('#4AA8F0', '#7A5CF0'), ('#38C07A', '#2A9AD0'), ('#F0C040', '#F07A30'),
            ('#9A6AF0', '#F06AB0'), ('#2E3440', '#4C566A')]
    names = ['Maps', 'YouTube', 'Spotify', 'WhatsApp', 'Photos', 'Files']
    for i in range(6):
        cx = x + w * ((i % 3) + 0.5) / 3
        cy = y + h * ((i // 3) + 0.5) / 2
        s = min(w / 3, h / 2) * 0.62
        c.shape(dict(fill=cols[i][0], fill2=cols[i][1], x=cx - s / 2, y=cy - s / 2 - 6, w=s, h=s, r=s * 0.24))
        c.text((cx, cy + s / 2 + 6), names[i], 11, p.get('ink', '#FFFFFF'), 'reg', 'mm')


def part_gear(c, p):
    glyph(c, 'settings', p['x'], p['y'], p['w'], p.get('ink', '#888888'))


PARTS = {k[5:]: v for k, v in globals().items() if k.startswith('part_')}


def road_dashes(c, r):
    L, d = c.draw()
    hz, vx, half = r['horizon'], r['x'], r['half']
    z = 0.0
    while True:
        # dashes spaced evenly along the road in depth: y = hz + K / depth
        for k in range(1, 40):
            depth = k * 1.6 + 0.6
            t0, t1 = 1 / depth, 1 / (depth + 0.8)
            y0, y1 = hz + (H - hz) * t0, hz + (H - hz) * t1
            if y1 <= hz + 6 or y0 > H:
                continue
            wd = 1.5 + 4 * t0
            d.polygon([((vx - wd) * S, y0 * S), ((vx + wd) * S, y0 * S), ((vx + wd * 0.7) * S, y1 * S), ((vx - wd * 0.7) * S, y1 * S)],
                      fill=col(r.get('dash', '#FFFFFF'), 0.9))
        break
    c.put(L)


def render(layout, out):
    bg = Image.open(SCENES / (layout['bg'] + '.jpg'))
    c = Canvas(bg)
    if 'road' in layout:
        road_dashes(c, layout['road'])
    if 'lines' in layout:
        L, d = c.draw()
        for x0, y0, x1, y1 in layout['lines']:
            d.line([x0 * S, y0 * S, x1 * S, y1 * S], fill=(255, 255, 255, 50), width=S)
        c.put(L)
    for p in layout['parts']:
        c.shape(p, shadow=layout.get('light', True) or p.get('alpha', 1) > 0.8)
        PARTS[p['kind']](c, p)
    c.im.convert('RGB').resize((W, H), Image.LANCZOS).save(out / f"layout-{layout['id']}.png", optimize=True)


if __name__ == '__main__':
    SCENES = Path(sys.argv[1])
    out = Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    spec = json.loads((HERE.parent / 'assets' / 'layouts' / 'layouts.json').read_text())
    for lay in spec['layouts']:
        if len(sys.argv) > 3 and lay['id'] not in sys.argv[3:]:
            continue
        render(lay, out)
        print('drew', lay['id'])
