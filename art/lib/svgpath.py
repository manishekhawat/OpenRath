import math, re, numpy as np
# SVG path flattener (M L H V C S Q T A Z, absolute and relative) -> list of subpath polygons.
# Fill the result with the nonzero rule (fill.nonzero), as SVG does.

def _bez(p0, p1, p2, p3, n=20):
    t = np.linspace(0, 1, n)[1:, None]
    return list(map(tuple, (1 - t) ** 3 * p0 + 3 * (1 - t) ** 2 * t * p1 + 3 * (1 - t) * t ** 2 * p2 + t ** 3 * p3))


def _quad(p0, p1, p2, n=16):
    t = np.linspace(0, 1, n)[1:, None]
    return list(map(tuple, (1 - t) ** 2 * p0 + 2 * (1 - t) * t * p1 + t ** 2 * p2))


def _arc(p0, rx, ry, phi, large, sweep, p1, n=24):
    """Endpoint arc to points (SVG implementation notes, F.6.5)."""
    if rx == 0 or ry == 0 or np.allclose(p0, p1):
        return [tuple(p1)]
    rx, ry = abs(rx), abs(ry)
    c, s = math.cos(math.radians(phi)), math.sin(math.radians(phi))
    dx, dy = (p0[0] - p1[0]) / 2, (p0[1] - p1[1]) / 2
    x1, y1 = c * dx + s * dy, -s * dx + c * dy
    lam = x1 ** 2 / rx ** 2 + y1 ** 2 / ry ** 2
    if lam > 1:
        rx, ry = rx * math.sqrt(lam), ry * math.sqrt(lam)
    num = rx ** 2 * ry ** 2 - rx ** 2 * y1 ** 2 - ry ** 2 * x1 ** 2
    den = rx ** 2 * y1 ** 2 + ry ** 2 * x1 ** 2
    k = math.sqrt(max(0, num / den)) if den else 0
    if large == sweep:
        k = -k
    cx1, cy1 = k * rx * y1 / ry, -k * ry * x1 / rx
    cx = c * cx1 - s * cy1 + (p0[0] + p1[0]) / 2
    cy = s * cx1 + c * cy1 + (p0[1] + p1[1]) / 2

    def ang(ux, uy, vx, vy):
        a = math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        return a

    t1 = ang(1, 0, (x1 - cx1) / rx, (y1 - cy1) / ry)
    dt = ang((x1 - cx1) / rx, (y1 - cy1) / ry, (-x1 - cx1) / rx, (-y1 - cy1) / ry)
    if not sweep and dt > 0:
        dt -= 2 * math.pi
    elif sweep and dt < 0:
        dt += 2 * math.pi
    m = max(4, int(abs(dt) / (2 * math.pi) * n * 2))
    out = []
    for i in range(1, m + 1):
        t = t1 + dt * i / m
        x, y = rx * math.cos(t), ry * math.sin(t)
        out.append((c * x - s * y + cx, s * x + c * y + cy))
    return out


def subpaths(d):
    toks = re.findall(r'[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?', d)
    polys = []; pts = []; cur = np.zeros(2); start = cur; prev_c2 = None; prev_q = None; cmd = None; i = 0

    def num():
        nonlocal i
        v = float(toks[i]); i += 1
        return v

    def flag():
        # arc flags may be written without separators ("a1 1 0 011 1"): take one digit
        nonlocal i
        t = toks[i]
        if len(t) > 1 and t[0] in '01' and not t.startswith('0.'):
            toks[i] = t[1:]
            return int(t[0])
        i += 1
        return int(float(t))

    while i < len(toks):
        if re.match('[A-Za-z]', toks[i]):
            cmd = toks[i]; i += 1
        rel = cmd.islower(); base = cur if rel else np.zeros(2); c = cmd.upper()
        if c == 'Z':
            if pts: polys.append(pts)
            pts = []; cur = start; prev_c2 = prev_q = None
            continue
        if c == 'M':
            if pts: polys.append(pts)
            cur = base + [num(), num()]; start = cur; pts = [tuple(cur)]; cmd = 'l' if rel else 'L'; prev_c2 = prev_q = None
        elif c == 'L':
            cur = base + [num(), num()]; pts.append(tuple(cur)); prev_c2 = prev_q = None
        elif c == 'H':
            cur = np.array([(cur[0] if rel else 0) + num(), cur[1]]); pts.append(tuple(cur)); prev_c2 = prev_q = None
        elif c == 'V':
            cur = np.array([cur[0], (cur[1] if rel else 0) + num()]); pts.append(tuple(cur)); prev_c2 = prev_q = None
        elif c in 'CS':
            if c == 'C': p1 = base + [num(), num()]
            else: p1 = 2 * cur - prev_c2 if prev_c2 is not None else cur
            p2 = base + [num(), num()]; p3 = base + [num(), num()]
            pts += _bez(cur, p1, p2, p3); prev_c2 = p2; prev_q = None; cur = p3
        elif c in 'QT':
            if c == 'Q': p1 = base + [num(), num()]
            else: p1 = 2 * cur - prev_q if prev_q is not None else cur
            p2 = base + [num(), num()]
            pts += _quad(cur, p1, p2); prev_q = p1; prev_c2 = None; cur = p2
        elif c == 'A':
            rx, ry, phi = num(), num(), num()
            large, sweep = flag(), flag()
            p1 = base + [num(), num()]
            pts += _arc(cur, rx, ry, phi, large, sweep, p1); cur = p1; prev_c2 = prev_q = None
        if not pts and c != 'M':
            pts = [tuple(cur)]
    if pts: polys.append(pts)
    return [p for p in polys if len(p) >= 3]
