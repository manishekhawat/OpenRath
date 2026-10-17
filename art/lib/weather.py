"""The weather icons, drawn in chrome."""
import math

from art import *   # noqa: F401,F403


def weather(name, size):
    S = size
    L = Layer(S, S)
    d, c = L.d, S / 2
    p = lambda *v: [x * SS for x in v]
    lw = round(S * .055 * SS)

    def cloud(cx, cy, r):
        d.ellipse(p(cx - r * .55, cy - r * .75, cx + r * .35, cy + r * .15), fill=255)
        d.ellipse(p(cx - r * 1.0, cy - r * .35, cx - r * .2, cy + r * .45), fill=255)
        d.rounded_rectangle(p(cx - r * 1.0, cy - r * .05, cx + r * .9, cy + r * .45), r * .25 * SS, fill=255)
        d.ellipse(p(cx + r * .0, cy - r * .5, cx + r * .9, cy + r * .4), fill=255)

    def sun(cx, cy, r):
        d.ellipse(p(cx - r * .5, cy - r * .5, cx + r * .5, cy + r * .5), fill=255)
        for i in range(8):
            a = i * math.pi / 4
            d.line(p(cx + math.cos(a) * r * .7, cy + math.sin(a) * r * .7, cx + math.cos(a) * r, cy + math.sin(a) * r), fill=255, width=lw)

    if name == 'w_sun':
        sun(c, c, S * .4)
    elif name == 'w_moon':
        d.ellipse(p(S * .18, S * .18, S * .82, S * .82), fill=255)
        d.ellipse(p(S * .36, S * .08, S * .98, S * .7), fill=0)
    elif name == 'w_partly':
        sun(S * .62, S * .36, S * .26)
        d.ellipse(p(S * .12, S * .3, S * .78, S * .92), fill=0)
        cloud(S * .45, S * .62, S * .3)
    elif name == 'w_cloud':
        cloud(c, c * 1.05, S * .36)
    elif name in ('w_rain', 'w_snow', 'w_storm'):
        cloud(c, S * .4, S * .32)
        if name == 'w_rain':
            for i in range(3):
                x = S * (.32 + i * .18)
                d.line(p(x, S * .7, x - S * .06, S * .9), fill=255, width=lw)
        elif name == 'w_snow':
            for i in range(3):
                x = S * (.32 + i * .18)
                d.ellipse(p(x - S * .04, S * .76, x + S * .04, S * .84), fill=255)
        else:
            d.polygon(p(S * .52, S * .6, S * .38, S * .8, S * .5, S * .8, S * .44, S * .98, S * .64, S * .72, S * .52, S * .72), fill=255)
    elif name == 'w_fog':
        for i in range(4):
            y = S * (.3 + i * .14)
            d.rounded_rectangle(p(S * (.15 + (i % 2) * .08), y, S * (.85 - (i % 2) * .08), y + S * .06), S * .03 * SS, fill=255)
    m = L.mask()
    return to_rgba(chrome_rgb(m.shape[0], m.shape[1], tilt=0), m)
