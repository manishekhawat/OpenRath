import math
import numpy as np


def nonzero(polys, w, h):
    """Polygons (pixel coordinates) filled with the nonzero winding rule, as SVG and Android's
    default fill do, so holes (rings, counters) stay open. Returns an (h, w) uint8 mask, 0 or 255."""
    edges = []
    for p in polys:
        for a, b in zip(p, p[1:] + p[:1]):
            if a[1] != b[1]:
                edges.append((a[0], a[1], b[0], b[1]))
    out = np.zeros((h, w), np.uint8)
    if not edges:
        return out
    e = np.array(edges, np.float64)
    x0, y0, x1, y1 = e[:, 0], e[:, 1], e[:, 2], e[:, 3]
    up = y1 > y0
    lo, hi = np.minimum(y0, y1), np.maximum(y0, y1)
    for r in range(max(0, int(lo.min())), min(h, int(math.ceil(hi.max())) + 1)):
        y = r + .5
        act = (lo <= y) & (hi > y)
        if not act.any():
            continue
        xs = x0[act] + (y - y0[act]) * (x1[act] - x0[act]) / (y1[act] - y0[act])
        wd = np.where(up[act], 1, -1)
        order = np.argsort(xs)
        xs, wd = xs[order], wd[order]
        acc = np.cumsum(wd)
        for k in range(len(xs) - 1):
            if acc[k] != 0:
                a, b = int(math.ceil(xs[k] - .5)), int(math.ceil(xs[k + 1] - .5))
                if b > a:
                    out[r, max(0, a):min(w, b)] = 255
    return out


def as_polys(x):
    """A brand pack's outline: one polygon, or a list of them."""
    if x and isinstance(x[0][0], (int, float, np.floating)):
        return [list(x)]
    return [list(p) for p in x]
